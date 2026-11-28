# ECS 发版 SOP（阿里云单机形态）

> 适用范围：阿里云 ECS `i-uf62f6628at8ehgwonbg`（cn-shanghai，`8.153.176.50`）。**2026-11-24 起有两个部署面**：compose 栈（`/opt/family-tree/`）只跑 MySQL(:3308)/Redis(:6381) 数据面，应用层在单机 k3s 蓝绿双槽（蓝 :8091 / 绿 :8092，见 §11）。发版第 1~5 步（预检/备份/迁移判断）两个面共用，第 6 步起分岔。
> **不适用于**本机 Docker Desktop k8s 那套（术语叫 `prod`；ECS 这套叫 `ecs` 环境）。两边数据从同一份 dump 分叉，切流量前必须先冻结写入再重新 dump/restore，见「数据与备份」。
>
> 首次部署（建 `.env`、装 nginx、导数据）看 [`README.md`](README.md)；本文件只讲**已有一台在跑、要换新镜像**的重复性动作。

## 0. 边界：哪些动作不能在这条流程里顺手做

| 动作 | 状态 | 原因 |
|---|---|---|
| `docker compose up/down`、改 `.env` 的 `FT_IMAGE`、`repair` | 本 SOP 内，照做 | 就是发版本身 |
| `./ecs-setup.sh tobind`（命名卷 → 宿主 bind 目录） | **暂缓，需点名授权** | 实例上 compose 仍是命名卷（`family-tree_mysql-data` / `family-tree_redis-data`）。仓库版 compose 已改 bind 挂载，**直接传上去 = mysql 挂到空目录 = 安静建一套空 schema 并正常启动**（V7 修好后连报错都没有），等于把生产库换成空库还看不出来 |
| 把 `SPRING_PROFILES_ACTIVE` 从 `mysql` 切到 `mysql,ecs` / `mysql,prod` | **暂缓** | `prod` 在明文 HTTP 下下发 `Secure` Cookie → 登录 200 但下一个请求就 401；`ecs`/`prod` 都打开 `forward-headers-strategy: framework`，改的是 **Tomcat 侧**的 remoteAddr 口径——应用自己的 IP 口径已在 `8395f3b` 之后由 [`ClientIpResolver`](../../family-tree-web/src/main/java/com/mouhin/family/tree/web/util/ClientIpResolver.java) 复核落定（仅本机直连对端采信代理头、取 XFF 最右段），但 framework 策略会按 XFF **最左**段重写 `getRemoteAddr()`（客户端自带 XFF 即可再次自选来源，按 IP 的限流/短信额度又回到可绕过），与 resolver 的「非环回对端不信头部」相反，且 logback 日志里的 IP 也会随之改写 → 切 profile 时仍需单独复核，别顺手带 |
| 安全组、DNS、备案、宝塔面板 | **不碰** | 由用户在控制台操作 |
| `git commit` / `push` | **ECS 常规发版按 2026-11-27 用户授权自动执行**；其他场景需点名 | 用户已明确“以后发布还是基于之前的规则处理，不要我再确定”；仍须先过 clean package、备份、旁路验收与切流后复验 |

## 1. 预检（只读，5 分钟）

```bash
# 实例现状一屏打印：容器/profile/存储形态/Flyway 与业务读数/备份时间/磁盘内存/健康/镜像清单/cron
workbench upload deploy/ecs/ops/ft-preflight.sh /tmp/ --instance-id <i-xxx> --region cn-shanghai -f
workbench exec -i <i-xxx> -r cn-shanghai --timeout 120 -c 'bash /tmp/ft-preflight.sh'
```

必须逐条落纸，否则后面的「验收」没有对照物：

> **预检脚本本身在 2026-11-24 之前是骗人的**（k3s 接管后暴露，仓库版已修）：`set -e` 让它在 curl 连不上时中途静默退出，后半截（镜像/cron）根本不打印，会被读成「没有历史镜像」；Flyway 顶部版本用 `max(version)`，而 version 是 VARCHAR，字符串序 `'9' > '40'`，永远报 `schema_version=9`；取中文的查询缺 `--default-character-set=utf8mb4`，中文全变 `????`，会被 §7 误判成生产数据坏了；应用端口写死 8090，compose app 退役后恒为 `000`。现在脚本按「compose app 是否 Running」自适应形态，k3s 侧的应用端口从 nginx 反代实际指向的 `127.0.0.1:809x` 现取（**注意取法**：`uniq -c` 的行是「计数 端口」，`tr -dc '0-9'` 会把计数粘成 `68091` 这种假端口，必须 `awk '{print $2}'`）。

1. **在跑的是哪个镜像、compose 是哪种存储形态**：`docker ps` 的 tag + `docker volume ls`；k3s 侧另看 `-n prod get deploy/pods`（在任槽位 + 镜像 tag）。形态决定第 5 步能不能传 compose（见第 0 节）。
2. **Flyway 基线**：`SELECT version, checksum FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;`、表数、关键表行数（2026-11-24 实测 `29 表 / family 9 / account 167 / has_phone 1`）。行数是第 7 步的对照，**不是**「不该变」——真实用户写入会让它涨；`has_phone` 是短信登录可用性的真实前置（开关已双开，但没有手机号的用户走不了短信路）。
3. **最近一次备份**：`ls -lt /opt/family-tree/backups | head`（cron 自动备份）与 `ls -lt /opt/family-tree/backup | head`（发版前手工快照 `family_tree-pre-vNN-*.sql`、归档 jar 目录），自动备份必须是今天的，否则先补第 2 步。
4. **镜像 tar 的落点与磁盘余量**：`/root/ft-migrate/`，`df -h /`（177MB 的 tar + load 后的 500MB 镜像层）。**k3s 侧还要看 containerd**：`k3s ctr -n k8s.io images list | grep family-tree`，清单是 `imagePullPolicy: Never`，只导 docker 不导 containerd 会让 pod 卡 `ImageNeverPull`。

## 2. 备份快照

```bash
workbench exec -i <i-xxx> -r cn-shanghai --timeout 300 -c 'cd /opt/family-tree && ./backup.sh; ls -lt backups | head -3'
```

备份文件必须在实例上**验证可读**再往下走（gzip 截断是哑巴失败）：

```bash
gz=$(ls -t /opt/family-tree/backups/*.sql.gz | head -1); gzip -t "$gz" && echo GZ_OK
zcat "$gz" | grep -c "CREATE TABLE"        # 期望 = 表数（本例 29）
zcat "$gz" | grep -m1 "INSERT INTO \`family\`" | cut -c1-120   # 中文没乱码
```

## 3. 容器网络自检（2026-11-22 事故的固定前置步骤）

**症状**：网页打得开、接口卡 30 秒然后转圈；`/actuator/health` 由 200 变 503（聚合 `db` 贡献者 DOWN）；登录接口 HTTP 状态仍是 200，body 是 `{"code":500,"message":"系统内部错误"}`。

**根因**：`firewall-cmd` 所属的 firewalld 一旦被重启/重载（这台机器上宝塔面板每天会干），docker 网桥就不再属于 `trusted` zone，转发流量落进 default zone（`public`），命中 nft 的 `filter_FORWARD_IN_ZONES → filter_FWDI_public → filter_FWDI_public_deny`（reject）。ICMP 不受这条链影响，所以 **ping 得通、TCP 连不上**。

**判定必须从 app 容器的网络命名空间里量**——distroless 镜像没有 shell，用 `nsenter` 借宿主的 curl；在宿主上另起一个临时容器属于**另一个网络**，ARP 都不通，量出来是假阳性：

```bash
pid=$(docker inspect -f '{{.State.Pid}}' family-tree-app)
mip=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' family-tree-mysql | cut -d' ' -f1)
nsenter -t "$pid" -n curl -v --max-time 5 "http://$mip:3306/" 2>&1 | grep -E "Connected to|No route to host"
# 注意：mysqld 先发握手，所以 HTTP 码永远是 000，别用 %{http_code} 判定，要看有没有 Connected to
```

**修法（方案 A，2026-11-22 定稿）**：只把 family-tree 用到的网桥绑回 `trusted`，不动主机侧任何 INPUT/端口/服务策略。

```bash
firewall-cmd --get-zone-of-interface=br-<网络ID前12位>   # 期望 trusted；"no zone" 就是掉出 trusted 了
firewall-cmd --zone=trusted --change-interface=br-<...>   # 运行时；立即生效，无需 reload
```

三条别踩：

- **不要用 `--add-source=172.16.0.0/12`**：这台机器 `rp_filter` 全为 0（`eth0` 也是 0），按源地址放行等于给伪造源地址的包开门。绑接口比绑地址严。
- **不要 `firewall-cmd --reload`**：运行时状态已经对了，reload 会重刷 docker 自己插的 iptables 规则，代价是把在跑的容器连接全断一遍。
- **`--query-interface` 在这台 firewalld 上对已绑 trusted 的接口也回 `no`**，判归属只能用 `--get-zone-of-interface`。

**持久化 + 自愈**（已装好，勿重复安装）：

```bash
firewall-cmd --permanent --zone=trusted --change-interface=br-<...>   # 写 /etc/firewalld/zones/trusted.xml
# 网桥名随 docker 网络重建变化，permanent 里写死的名字下次不一定是它 —— 所以真正的兜底是运行时重绑：
/usr/local/sbin/ft-firewall-guard.sh          # 幂等：只把「名字以 family-tree 开头」或「挂着 family-tree* 容器」的网桥绑进 trusted，宝塔的 baota_net 一律不动
systemctl list-timers ft-firewall-guard.timer # 开机 60s 后首跑，之后每 5min 一次；journalctl -u ft-firewall-guard.service 看历史
```

`/opt/family-tree/ft-sentinel.sh` = 第 3、6、7 步的机器可读版本：mysql/redis 的 TCP 可达、`/actuator/health` = 200、登录接口 25s 内返回。任一项失败退出码 1。

**2026-11-24 重写，两件事必须记住**：

1. **它按形态自适应**。compose app 在任时 `nsenter` 进 app 容器 netns 量（在宿主上另起临时容器属于另一个网络，ARP 都不通，量出来是假阳性），地址按容器名现查；compose app 退役后走 k3s 分支——pod 是 `hostNetwork`，它的 netns 就是宿主，所以直接量宿主发布端口 mysql `127.0.0.1:3308` / redis `127.0.0.1:6381`，应用端口从 nginx 反代现取，**不写死槽位**（蓝绿在任会随发版交替）。第一版输出会打印 `mode=compose|k3s base=...`，判读前先确认这行是不是当前拓扑。
2. **旧版有两个「假通过」**，已修：容器已停时 `docker inspect -f {{.State.Pid}}` 返回 **0**，`nsenter -t 0 -n` 等于在宿主跑探针，compose 网络被拦也照样报 OK（现在按 `State.Running` 判形态，compose 模式要求 PID 非 0）；连接被拒时 curl 立刻返回且 `http_code` 为空/000，旧脚本只量耗时，于是把「根本没连上」判成「0s 返回，OK」（现在必须同时拿到非 000 的 HTTP 码）。

> 反面教材：k3s 接管当天，这份旧哨兵在**健康的生产上**报 3 行 FAIL、`rc=1`——不是应用坏了，是闸门本身过时了。**闸门红先判闸门，别直接回滚**（用 `/opt/family-tree/ft-sentinel.sh.bak-20261124` 这类备份件对照，或手工 curl 复核）。旧版在 `/opt` 一直用到 2026-11-24，之后仓库 = 实例 = 同一份自适应脚本。

## 4. 本机构建 → 上传 → 导入

```bash
cd /Users/mac/CodeDir/Family_Tree
git rev-parse --short HEAD && git status --porcelain | wc -l   # 记录：镜像是哪份代码，工作区脏不脏
# Dockerfile 是 package -DskipTests，测试必须在构建前本机跑过，否则等于把没测过的树推到生产
./mvnw -B test 2>&1 | tail -20

docker build -t family-tree:vNNN-local .
mkdir -p /tmp/ft-ecs && cd /tmp/ft-ecs
docker save family-tree:vNNN-local | gzip > ft-vNNN.tar.gz
shasum -a 256 ft-vNNN.tar.gz | tee ft-vNNN.tar.gz.sha256

workbench upload /tmp/ft-ecs/ft-vNNN.tar.gz /root/ft-migrate/ --instance-id <i-xxx> --region cn-shanghai
# upload/download 子命令不认 --timeout（会 unknown flag 直接失败），只有 exec 认；大文件不用加超时参数 --timeout 600
# 远端已存在同名文件时上传会交互式问 Overwrite 并被取消，加 -f 才是不问就覆盖
```

导入并在实例上比摘要（**字节数 + sha256 都对上才 load**；`.sha256` 里记的是本机绝对路径，`sha256sum -c` 在目标目录会失败，自己 `cut -d' ' -f1` 取摘要再比）：

```bash
workbench exec -i <i-xxx> -r cn-shanghai --timeout 600 -c 'cd /root/ft-migrate && sha256sum ft-vNNN.tar.gz | cut -d" " -f1 > /tmp/new.sha && cat /tmp/new.sha && gunzip -c ft-vNNN.tar.gz | docker load'
```

> `workbench exec` 的远端 shell 是 **dash**：`<(...)`、`[[ ]]` 会 `syntax error`；命令必须**单行**（多行静默不执行）；长命令显式 `--timeout 600`（默认只有 30s）。
> 偶发丢 stdout 或报 timeout 但远端其实成功（`docker load`/`sha256sum` 尤其明显）——**别据返回文本判定失败，用一条独立短 exec 复查**（`docker images family-tree:vNNN-local`）。
> upload/exec/pull 并发会抢同一实例会话导致超时，**串行，一次一条**。

### 4b. 基座镜像拉不动时的离线兜底（2026-11-22 实际用到）

`docker build` 的两个基座此刻都取不到：`registry-1.docker.io` 被 DNS 污染解析到一个 Dropbox IP（`162.125.32.13`，`curl` 0.05s 直接失败），`gcr.io` 连接超时；能通的只有 `ghcr.io` / `quay.io` / `docker.m.daocloud.io`（Docker Hub 镜像），而 distroless 不在 Docker Hub 也不在 ghcr。这种情况下有两条路：

```bash
# 路线一（首选，仍是官方 Dockerfile）：把基座从可达镜像源拉下来再打回原名
docker pull docker.m.daocloud.io/library/eclipse-temurin:21-jdk \
  && docker tag docker.m.daocloud.io/library/eclipse-temurin:21-jdk eclipse-temurin:21-jdk
# distroless 没有可用的 Hub 镜像源，仍要另想办法（下面路线二，或从能连 gcr 的机器 docker save 过来）

# 路线二（本次用的）：本机 mvnw 打 jar + 换掉上一版镜像里的 jar 层，基座层直接复用
./mvnw -B -DskipTests package                    # 本机 JDK 21.0.11（Homebrew），镜像里的 JRE 是 21.0.10，class 版本同为 65
mkdir -p /tmp/ft-build && cp family-tree-web/target/family-tree-web-*.jar /tmp/ft-build/app.jar
printf 'FROM family-tree:vNNN-1-local\nCOPY app.jar /app/app.jar\n' > /tmp/ft-build/Dockerfile
docker build -f /tmp/ft-build/Dockerfile -t family-tree:vNNN-local /tmp/ft-build
```

路线二的两处偏差必须记账：**编译发生在宿主 JDK 而不是 `eclipse-temurin:21-jdk` 容器里**（构建不可复现，发版前先 `./mvnw -B test` 兜质量），以及 **jar 层之外的基座层继承自上一版镜像**。所以打完要验证「只换了 jar、运行形态没变」：

```bash
docker image inspect family-tree:vNNN-local --format 'Entrypoint={{.Config.Entrypoint}} User={{.Config.User}} WorkDir={{.Config.WorkingDir}} Env={{.Config.Env}}'   # 与上一版逐字一致
docker history family-tree:vNNN-local --format '{{.Size}}' | wc -l     # 层数 = 上一版 + 1
cid=$(docker create family-tree:vNNN-local); docker cp "$cid":/app/app.jar /tmp/check.jar; docker rm "$cid" >/dev/null
shasum -a 256 /tmp/check.jar family-tree-web/target/family-tree-web-*.jar   # 两个摘要必须相同
# 顺手验镜像里打包的迁移文件是不是新版（决定要不要 repair）：
unzip -p /tmp/check.jar BOOT-INF/classes/db/migration/mysql/V7__add_widowed_flag.sql | grep -c "IF NOT EXISTS"   # 0 = 新 V7
```

`.dockerignore` 里 `target/` 是被排除的，所以路线二**不能**拿仓库根当构建上下文（`COPY target/*.jar` 会拷不到东西）；上面单独开了 `/tmp/ft-build` 当上下文就是这个原因。

**路线三（基座断链，2026-11-24 已验证可行）**：路线二的链式继承让 v76→v80 每个镜像都依赖前序镜像不被 prune。为验证可断链，把链底镜像的文件系统整体导出、重打为单层基座，再叠 jar：

```bash
# ① 导出 v74-local（链底，本身即官方 Dockerfile 完整构建物）扁平文件系统为单层基座；
#    docker import 不继承原 Config，须用 --change 逐字补齐 ENTRYPOINT/ENV(PATH/SSL_CERT_FILE/LANG/JAVA_OPTS/SPRING_PROFILES_ACTIVE)/WORKDIR/EXPOSE
C=$(docker create family-tree:v74-local); docker export $C > /tmp/base-v74.tar; docker rm $C >/dev/null
docker import --change 'ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]' \
  --change 'EXPOSE 8090' --change 'ENV PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin' \
  --change 'ENV SSL_CERT_FILE=/etc/ssl/certs/ca-certificates.crt' --change 'ENV LANG=C.UTF-8' \
  --change 'ENV SPRING_PROFILES_ACTIVE=prod' --change 'ENV JAVA_OPTS=-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0' \
  --change 'WORKDIR /app' /tmp/base-v74.tar family-tree:v74-baseexport
# ② FROM v74-baseexport COPY app.jar（v80 jar）→ family-tree:v80-rebased
# ③ 对照：docker export 两镜像 tar -tvf|sort 逐条 diff = 2329 条目全等，仅 app.jar/COPY 层时间戳差异
# ④ 实跑：临时容器接生产同网同 env（含 .sms.env）与同卷，55s 起、health 200、/api/auth/config 与 login.js?v 同生产、" ERROR " 0 条，验毕即删
```

结论：**后续发版可 `FROM family-tree:v74-baseexport` 建 jar 层，不再依赖 v75~v79 链镜像**；`v74-baseexport` 与 `v80-rebased` 已留存实例。**（2026-11-25 更正：这两个 tag 后来都不在实例上了，`docker images` 只剩在任的 `v81-local`；本段配方仍然可用，但要先照本节从任一完整镜像重做基座，否则按 §4c 链式继承在任镜像。）**偏差记账：baseexport 是扁平单层（5 个历史 jar 层叠加被 export 拍平，无 whiteout 残留，tar 序 diff 已证内容等价）；将来若 gcr 通路恢复，优先回到官方 `gcr.io/distroless/java21-debian12` 全量重建。v75~v79 链镜像的 prune 自此**技术上可安全**，但仍属破坏性操作，必须点名后才执行（执行前用路线三对当前在任版本再各验一次）。

**2026-11-24 断链清理执行记录（用户点名"执行，清理历史镜像"）**：验证（当日路线三全绿）后，先把链上历史 jar 抽出归档 `/opt/family-tree/backup/jars-pre-prune-20261124/`（v75 `5328a640…` / v76 `40198b82…` / v77 `04613e14…` / v78 `c5cbe5eb…` / v79 `650a602a…`，sha 与历次发版登记逐一吻合），再 `docker rmi` 删除 v74~v78-local 五个标签。**保留**：v79-local（回滚锚）、v80-local（在任）、v74-baseexport+v80-rebased（断链基座与等价实证）。此后回滚 v79 仍走 FT_IMAGE 回切；回滚更早版本 = 取归档 jar 重建镜像层。清理后 v74~v78 独有 jar 层释放，链底层仍被 v79/v80 共享占用；磁盘 44%（22G 余，因新增 baseexport/rebased 与 jar 归档，净增约 0.7G）。下次发版基座改用 `FROM family-tree:v74-baseexport`。**（2026-11-25 更正：baseexport 后已不在实例上，此句作废；现基座为 `family-tree:app-base`，见 §4c。）**


### 4c. 本机 Docker 起不来时：直接在实例侧换 jar 层（2026-11-25 发 v82 用的就是这条）

本机 Docker 守护进程没跑（这台 Mac 上 Docker Desktop 未启动，`docker info` 直接失败）时，第 4 节「build → save 177MB → upload → load」整条链断掉。实例上本来就有基座镜像（在任版本），只传 jar 反而更省：

```bash
# 本机：测试必须先跑过（Dockerfile 侧不跑测试），再打 fat jar
./mvnw -B test && ./mvnw -B -o -DskipTests package
mkdir -p /tmp/ft-build && cp family-tree-web/target/family-tree-web-*.jar /tmp/ft-build/app.jar
shasum -a 256 /tmp/ft-build/app.jar
workbench upload /tmp/ft-build/app.jar /root/ft-build/ --instance-id <i-xxx> --region cn-shanghai -f   # 131.5MB，比 tar.gz 少 45MB，约 1 分钟

# 实例：dash 单行；先确认 sha 与本机逐字相同再 build
workbench exec -i <i-xxx> -r cn-shanghai --timeout 600 -c 'cd /root/ft-build && sha256sum app.jar && printf "FROM family-tree:app-base\nCOPY app.jar /app/app.jar\n" > Dockerfile && docker build -t family-tree:vNNN-local . 2>&1 | tail -4'
```

§4b 的三条自检照旧（Config 逐字对照、`docker history` 层数 = 上一版 + 1、`docker create` + `docker cp` 把镜像里的 jar 掏出来比 sha），另加两条实例侧专属：

- **基座 = `family-tree:app-base`（2026-11-25 建成，发版不再链式继承）**。此前 §4c 只能 `FROM 上一版` 链式（v81→v82→v83，每版 +138MB 且互相依赖 prune）。现按 §4b 路线三从链底 `v81-local` 重做了无 jar 单层基座：`docker create + export + tar --delete app/app.jar + import --change`（ENTRYPOINT/ENV×5/WORKDIR/EXPOSE 逐字照抄 v81 Config），**192MB 单层**（`1b81a990d958`）。验证：基座导出清单 0 条 `app/app.jar`；`FROM app-base + COPY v83 jar` = `v83-basecheck` **330MB**、镜像内 jar sha 与在任 v83 逐字一致；该验证镜像 8099 旁路实跑全绿（health UP、config 三开关、`?v=20261125a`，阅后即焚）。**自 v84 起每版体积恒定 ~330MB**，且新镜像只依赖 app-base——v81~v83 链镜像不再被发版链绑定（prune 仍属破坏性操作需点名）。**实测基线（2026-11-25 v84/v85/v86 三轮连发，见 §9 末行）**：build 0~3s（同 jar 层复用）、`save|ctr import` 12~14s、rollout ~86s、切流+验收每轮 ~2 分钟。基座防误删：`docker save | gzip` 已落 `/opt/family-tree/backup/family-tree-app-base-20261125.tar.gz`（63MB，`gzip -t` 过，sha256 `7419cdca…`；⚠仍在同盘，异机副本挂 §11.4）。重建配方细节见 `/root/ft-build/ft-make-base.sh`（已随基座完成而使命结束，留档可参考）。
- 构建完**必须**补 `docker save family-tree:vNNN-local | k3s ctr -n k8s.io images import -`：k3s 不读 docker 的镜像库（`imagePullPolicy: Never`，漏导就 `ImageNeverPull`）。导完用 `k3s ctr -n k8s.io images list | grep family-tree` 复确认（会顺带打 CRI `v1alpha2` deprecation warning，无害）。

## 5. 这一版要不要 `repair`

规则只有一条：**`db/migration/mysql/` 里「已经被应用过的」文件内容变了没有**（新增 V41 不算，那走正常 migrate）。变了就必须在起新镜像前 `repair`，否则新容器启动即 `Migration checksum mismatch`，`validate-on-migrate` 直接把应用拦死。

本例：`V7__add_widowed_flag.sql` 去掉 MariaDB 的 `ADD COLUMN IF NOT EXISTS`（MySQL 8 报 1064，空库从零迁移卡在 V7）→ checksum 从 `-781586466` 变 `465406669`。

想手算复核 checksum：Flyway 按行切文件、**丢空行、行内首尾空格保留（注释行算数）**、行间不加分隔符拼接，取 CRC32 按有符号 int32 解释。

```bash
# 1) 传与新镜像同一份代码的迁移目录（tar 解出 /opt/family-tree/migrations/mysql）
tar czf /tmp/ft-ecs/migrations-mysql.tgz -C family-tree-web/src/main/resources/db/migration mysql
workbench upload /tmp/ft-ecs/migrations-mysql.tgz /opt/family-tree/ --instance-id <i-xxx> --region cn-shanghai

# 2) 停应用 → repair → 校验（脚本会把改写后的 V7 checksum 与 EXPECT_V7_CHECKSUM 比对，传错版本会停在这儿）
cd /opt/family-tree && tar xzf migrations-mysql.tgz
docker compose --env-file .env stop family-tree
./ecs-setup.sh repair          # 首次会拉 flyway/flyway:11.14-alpine（版本必须等于 Boot 4.0.8 管理的 Flyway 11.14.1，否则算出的 checksum 对不上）
```

> 若 `ecs-setup.sh` 还是实例上那份旧的（7KB，没有 `repair`/`tobind` 阶段），先把仓库版 19KB 的传上去再跑。脚本本身不参与运行时拓扑，传它不影响在跑的容器。
>
> 解包位置：打包命令用了 `-C ... db/migration mysql`，归档根是 `mysql/`，解出来是 `/opt/family-tree/mysql`，而脚本要的是 `/opt/family-tree/migrations/mysql`（`MIGRATIONS_DIR`）—— 解完补一句 `mkdir -p migrations && mv mysql migrations/mysql`。

`repair` 阶段要 `flyway/flyway:11.14-alpine`，而**基座镜像能不能拉到是这一版发版的最大变量**（2026-11-22 实测：实例直连 `registry-1.docker.io` 的默认 mirror 对这个仓库不起作用，`docker pull` 干等 600s 后超时且不落 tag）：

```bash
# 实例上走 daocloud 镜像源，名字必须再打回 flyway/flyway:11.14-alpine，否则脚本仍按原名去拉
docker pull docker.m.daocloud.io/flyway/flyway:11.14-alpine
docker tag docker.m.daocloud.io/flyway/flyway:11.14-alpine flyway/flyway:11.14-alpine
```

Workbench 的 exec 有会话超时，**大镜像拉取要脱离会话跑**（`docker pull` 被会话杀掉时层已下完也不会打 tag，等于白跑）：

```bash
nohup sh -c 'docker pull docker.m.daocloud.io/flyway/flyway:11.14-alpine >/tmp/flypull.log 2>&1; echo PULL_EXIT=$? >>/tmp/flypull.log' >/dev/null 2>&1 &
# 之后用独立短 exec 复查：docker images | grep flyway（别拿 exec 的返回文本判定成败）
```

**兜底（2026-11-22 实际用了）**：flyway 镜像拉不动、应用已经停着等不起时，本例的 `repair` 等价于一条带旧值守卫的 UPDATE —— 这一版只改了 V7 的内容，没有 `success=0` 的失败行要清，`description`/`type` 也没变：

```bash
# /tmp/ft-v7-checksum-fix.sh（仓库里没有，逻辑就是下面这三行）：先数一遍守卫行数必须为 1，再改，再回读
UPDATE family_tree.flyway_schema_history SET checksum=465406669 WHERE version='7' AND checksum=-781586466;
```

用这条的前提：**新 checksum 是从镜像里的 jar 反算的**（`unzip -p app.jar BOOT-INF/classes/db/migration/mysql/V7__add_widowed_flag.sql` + 丢空行拼接取 CRC32），不是抄文档。改完必须回读确认 `success=0` 的行数为 0、历史表仍 40 行、最高版本 40，然后才能起新镜像。等 flyway 镜像后续拉到了，再跑一次 `./ecs-setup.sh repair` 也无害（幂等）。

## 6. 切换镜像并启动

```bash
cd /opt/family-tree
sed -i.bak "s/^FT_IMAGE=.*/FT_IMAGE=family-tree:vNNN-local/" .env && rm -f .env.bak
./ecs-setup.sh app             # docker compose up -d 应用层 + 等 /actuator/health 200
```

`stage_app` 会打印「空库警告」：如果它说库里 0 张表而你明知不是空库，**立刻停手**，那是存储形态挂错（见第 0 节 tobind）。

## 7. 验收电池（顺序跑，任一项红就回滚，别边修边收工）

```bash
/opt/family-tree/ft-sentinel.sh          # 期望首行 mode 与当前拓扑一致 + 四行 OK，rc=0
```

再加四条业务侧读数，全部与第 1 步的基线对照：

```bash
cd /opt/family-tree && ./ecs-setup.sh status
# 注意：MySQL 默认 sql_mode 下 `||` 是 OR 不是字符串拼接（会输出 1 这种假读数），跨表计数必须用 CONCAT()
docker exec family-tree-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N --default-character-set=utf8mb4 -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=\"family_tree\"; \
   SELECT CONCAT((SELECT COUNT(*) FROM family_tree.family),\",\" ,(SELECT COUNT(*) FROM family_tree.account)); \
   SELECT version,checksum FROM family_tree.flyway_schema_history ORDER BY installed_rank DESC LIMIT 1; \
   SELECT name FROM family_tree.family ORDER BY id DESC LIMIT 1;"'   # 中文必须正常，乱码=字符集回归
curl -s -o /dev/null -w "nginx80=%{http_code}\n" http://127.0.0.1/
ls -lt /opt/family-tree/logs | head -3   # 日志在往前走，且没有 ERROR 刷屏
```

> k3s 形态下别拿 `./ecs-setup.sh status` 当应用状态：compose app 已退役，它恒显示 app 停。应用侧看 `k3s kubectl -n prod get deploy,pods -o wide` + `logs deploy/family-tree-app-<在任槽>`；`/opt/family-tree/logs` 仍是有效证据（pod 把 hostPath 同一目录挂进 `/app/logs`）。日志时间戳是 **UTC**，nginx access.log 是 **CST**，差 8 小时。

浏览器侧还有一项脚本量不到的：**真的登录一次、开一次族谱树**。会话在 redis 上，切镜像不该登出；若「登录成功、下一个请求就未登录」，那是 Cookie 属性（`Secure`）问题，见第 0 节 profile 行。

### 7b. 登录 IP 限流的验收（仅带 `@RateLimit` / `RateLimiter` 的版本起）

限流现在由 Controller 上的 `@RateLimit` 声明驱动（`/api/auth/login`、`/login-sms`、`/register` 各挂 60s/30 与 600s/120 两条），计数**本地 Caffeine 窗口 + Redis `INCR` 双写、判定取大**：Redis 挂了不再放开限制（旧口径是 fail-open），重启或双槽并行则由 Redis 账本补回额度。

用 TEST-NET 假 IP 当靶子，**不要用真实账号当探针用户名**（会锁住该标识 10 分钟），也不要打 `/api/auth/sms/send-code`（测试短信照常收费）：

```bash
# dash 兼容，直连在任槽端口；直连对端是环回，resolver 才会采信代理头
for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31; do \
  code=$(curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H 'X-Forwarded-For: 203.0.113.250' -d '{"username":"__probe__","password":"x"}' http://127.0.0.1:8091/api/auth/login); done
echo "throttle_last=$code"   # 期望 429（第 31 次起）；若一直是 200 = 限流没生效
# 反证限流桶用的是解析后的 IP 而不是别的：换一个假 IP 立刻恢复可用
curl -s -o /dev/null -w "other_ip=%{http_code}\n" -X POST -H 'Content-Type: application/json' \
  -H 'X-Forwarded-For: 203.0.113.251' -d '{"username":"__probe__","password":"x"}' http://127.0.0.1:8091/api/auth/login   # 期望非 429
# Redis 侧账本（redis 无 requirepass）：应看到短窗/长窗两个 key 且带 TTL，值等于打进去的次数
docker exec family-tree-redis redis-cli --scan --pattern 'rate:limit:auth-ip:203.0.113.250:*'
docker exec family-tree-redis redis-cli ttl "$(docker exec family-tree-redis redis-cli --scan --pattern 'rate:limit:auth-ip:203.0.113.250:*' | head -1)"
```

> 想立刻复白名单（把探针 IP 的额度清掉）只 `DEL` Redis key **不够**——本地 Caffeine 那份还在，判定取大。清不动就等 60 秒窗口翻篇，或滚动重启在任槽。
> 判据只看 **HTTP 状态**：业务失败（口令错、账号不存在）经全局异常处理照样回 HTTP 200，错误码在响应体 `code` 里；只有被限流才回 `429`。本机 2026-11-25 实测（H2 profile、Redis 故意指向空端口）：同一 XFF 连打 30 次全 200，第 31–33 次 `429 + Retry-After: 27` + 体 `{"code":429,"message":"登录尝试过于频繁…"}`，换 `203.0.113.251` 立即恢复 200——顺带证伪了「Redis 不可用就放开限流」的旧口径。
> 副作用：这会往 `operation_log` 灌 31 行 `LOGIN_FAIL`（target 为 `__probe__`、IP 为 `203.0.113.250`），并把标识 `__probe__` 自身锁 10 分钟——都不影响真实用户，正好也验证了「按 IP 的额度是各自独立的」。发版当天可用 `SELECT COUNT(*) FROM family_tree.operation_log WHERE username='__probe__'` 数一下。
> 顺带一条真流量证据：发版后新产生的 `operation_log.ip_address` 应是用户公网 IP，不再是清一色 `127.0.0.1`——这同时证明 nginx 那条链路把 `X-Real-IP`/XFF 传到了应用。
> redis 侧 `--maxmemory 64mb` + `volatile-lru` 只淘汰带 TTL 的 key，限流键正在其列：内存吃紧时账本可能先掉限流键，这时靠本地窗口兜底——双写的意义之一。

补三条 2026-11-25 发 v82 时踩到的口径：

- **TTL 不是窗口长度，是「窗口 + 60」**（`RateLimiter.remoteCount` 首次自增时留跨界余量）。60s 窗口的 key 观测到 `ttl=117` 属正常，别当 bug 报。
- **抓 `Retry-After` 必须和 flood 在同一条 exec 里连着打**。`workbench exec` 一次调用几十秒，60s 窗口在两次调用之间就翻篇了——实测第一轮 flood 完（第 31–32 次已 `429`），隔约 90s 再打同一个 IP 回到 200：那不是限流失效，是 `rate:limit:auth-ip:<ip>:<slot>` 的 slot 换了（`--scan` 能看到相邻两个 slot 的 key 并存）。
- 宿主上没有 `mysql` 客户端（`mysql: command not found`），查库要 `docker exec family-tree-mysql sh -c "mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" -N --default-character-set=utf8mb4 -e \"…\""`；`operation_log` 的时间列是 **`create_time`**（不是 `created_at`），值是 **UTC**。生产实测一轮：同一 `X-Real-IP` 连打 34 次按窗口放行，第 35 次 `429 + Retry-After: 39`，账本值 35，换 IP 立即恢复；`operation_log.ip_address` 记的就是解析后的来源 IP（探针为 TEST-NET 段、真实用户为公网 IP）。

### 7c. Redis 失联时的降级验收（本机就能演，别拿生产 redis 试手）

起一版本机 jar 把 Redis 指到没人听的端口即可：`REDIS_HOST=127.0.0.1 REDIS_PORT=16399 java -jar family-tree-web/target/family-tree-web-1.0.0-SNAPSHOT.jar --spring.profiles.active=h2 --server.port=8126`。

**形态一：端口拒连（进程被停 / 容器没了）——这是线上最常见的那种，降级是成立的。** 2026-11-25 本机实测：

- `/api/auth/login` 口令错回 `{"code":400,…,"message":"用户名或密码错误"}`，**不再是 `{"code":500,"系统内部错误"}`**（改前 `LoginAttemptService` 的 `INCR`/`GET` 直接抛未捕获异常）；单请求 30ms（首个 1.4s 是 Lettuce 建连尝试）
- 同一 XFF 连打 33 次：30×200 → 第 31–33 次 `429`，换新 IP 立刻 200 —— Redis 账本缺席时本地 Caffeine 窗口独立守住额度

**形态二：黑洞（TCP 握手成功但永不回包，如 redis 进程假死、中间被 DROP）——限流管不了，别宣称已兜住。** 实测 40 并发下只有 10 个 `429`、30 个连接直接无响应，随后连不碰 Redis 的 `/api/auth/config` 都要 20s 不回。`jstack` 给的原因：请求线程停在 `io.lettuce.core.DefaultConnectionFuture.get()`（`StandaloneConnectionProvider.getConnection`），即**连接握手阶段没有超时**——`spring.data.redis.timeout` / `connect-timeout` 只管「已建立连接上的命令」和 socket 连接本身，命令在握手完成前根本没写出去，超时自然不触发；且它排在 `LettuceConnectionFactory` 的共享连接锁里，会连带卡住其它请求。要真收敛这条得用 `spring.data.redis.client-type: pool` + `ClientOptions/TimeoutOptions`（需要 Java 配置类，yml 配不出来），**属未做项**；眼下黑洞形态的正确处置是直接把该槽重启（§11.3），而不是指望降级。

> 演练时别用日志判定降级：`h2` profile 下 logback root 没挂 appender（§11.2 那条老坑），`LoginAttemptService` 的「Redis 不可用」WARN 在 h2 里看不到。生产 profile（`mysql` / `ecs` / `prod` 都是 root=WARN → FILE/CONSOLE）才会落盘，「日志没打」不等于「没降级」——判据用 HTTP 码和响应体。

## 8. 回滚

> 本节是 **compose 应用面**的回滚路线（2026-11-24 起应用面在 k3s，见 §11.3）。它仍然管用的是因为 `.env` + `ecs-setup.sh app` 这条链没拆——但要注意实例上的 docker 镜像现为 `app-base`（发版基座）、`v86-local`（在任）/`v85-local`（蓝槽锚，同镜像）、`v81-local`（仅因 Exited 急锚容器 `family-tree-app` 的父镜像而保留）；`v82~v84` 与更早版本已不在（2026-11-26 点名清理，§9 末行），回滚到 v81 之前得先用归档 jar（`backup/jars-pre-prune-20261124|20261126`、`jars-pre-retire-20261124`）走 §4c `FROM app-base` 重建镜像。

改回 `FT_IMAGE` 再 `./ecs-setup.sh app` 就行——**但第 5 步做过 `repair` 的话，旧镜像对不上被改写后的历史表，回滚同样是「起不来」**。所以：

- 回滚到 repair 之前的版本 = 再跑一次 `repair`，用的是**旧那份迁移目录**（`migrations/mysql` 得留一份旧版本的 tar，或从旧镜像里 `jar xf` 掏）。这也是第 4 步坚持「tar 与镜像同一份代码」的原因。
- 真正干净的回滚锚点是第 2 步的 dump：`restore.sh mysql <备份文件>` 会覆盖现有数据，属生产写入，需点名。
- 窗口期别 `docker rm -f` 正在启动的 app 容器：迁移中途 kill 会在 `flyway_schema_history` 留 `success=0`，下次启动报 `Detected failed migration to version N`，还得再 repair。

## 9. 发版记录

> **发布登记硬要求（2026-11-27 起）**：每次发版必须在独立台账 [`RELEASE-LOG.md`](RELEASE-LOG.md) 新增一条倒序记录，在首部明确写出 **发布时间（CST 与 UTC）** 和 **发布内容（用户可读的功能/修复摘要）**；时间以实际切流完成时刻为准，不能只写日期。代码 commit、镜像/jar 摘要、备份、验收结果、在任槽位与回滚锚也必须记录。本 §9 保留 2026-11-27 前的历史记录，v89 起以独立台账为准。

| 日期 | 镜像 | 代码 | repair | 备注 |
|---|---|---|---|---|
| 2026-11-21 | `family-tree:v71-local` | 初次落地（`docker save` 上传，不走 ghcr） | — | profile `mysql`，命名卷 |
| 2026-11-22 | `family-tree:v73-local` | HEAD `02b5b5c` + 工作区脏（含 V7 修复、`application-ecs.yml`/`application-h2.yml`、跨家族关联监听）；本机 `./mvnw -B test` 607 用例全绿后打包 | 是（V7 `-781586466` → `465406669`，走了第 5 节 SQL 兜底） | 基座镜像拉不动，用 4b 路线二换 jar 层；顺带装上网桥自愈 guard + 哨兵 |
| 2026-11-22 | `family-tree:v74-local` | 同 v73 工作树 + 亲族口径改造（`AffinityFamilyDomainService` 统一出身家族）；本机全量单测绿；4b 路线二换 jar 层（基座继承 v73-local） | 否（实例 V7 已是 `465406669`，历史表未动） | k8s prod 蓝绿先发同名镜像，再走本 SOP 切 ECS；17:03 停 app → 17:03:56 `200 OK`（约 48s）；哨兵四行 OK、读数与 v73 基线一致；两侧库同批补订正数据（同人链 5 条 + 母边 3 条，见 §9 附注） |
| 2026-11-22 | `family-tree:v75-local` | 全库亲族误显修复：父族行仅在有族内父边或链接回指证据时显示；入嫁方节点本族改以其妻族/夫族行显示（`AffinityFamilyDomainService` 2026-11-22 修订）；4b 路线二换 jar 层（基座继承 v74-local，jar sha `5328a640…`） | 否（本版无迁移内容变化，V7 仍 `465406669`） | k8s 先发蓝槽接流、绿槽 v74 休眠留回滚；ECS 18:22:59 停 app → 18:23:47 `200 OK`（约 48s）、哨兵四行 OK、读数 29 表/9 族/166 账号/7 链；两库同批手工补蒙永成同人链 `1:10↔40:316`（BLOOD、source=2、confirmed_by=1；k8s id=9 / ECS id=10，见 §9 附注） |
| 2026-11-22 | `family-tree:v76-local` | 母族证据化 v76b：母亲为本族亲生女（族内父边证据）时本族以其子女母族显示；4b 换 jar 层（基座继承 v75-local） | 否（无迁移变化） | k8s 绿槽在任、蓝槽 v75 休眠回滚锚；ECS 同镜像切流（回滚锚 v75-local）。首轮 jar 因未 `clean` 复用旧 class 被哨兵暴露，重打后二次发版 |
| 2026-11-24 | `family-tree:v77-local` | HEAD `2e2f74f` + 未提交短信认证批次（dypns 发码/短信登录/注册验证码，`ALIYUN_SMS_ENABLED` 默认 false，生产未启用）；本机 `./mvnw -B clean package` 全量单测绿；4b 换 jar 层（基座继承 v76-local，jar sha `04613e14…` 四处一致） | 否（迁移目录未动，最高 V40，历史表顶 `40`） | 上传仅 jar（避 save 761MB）；`.env` FT_IMAGE 切 `v77-local`，回滚锚 v76-local 镜像在实例保留；哨兵四行 OK rc=0；读数 29 表/9 族/167 账号/flyway 顶 40，最新家族名 UTF-8 正常（罗氏/胡氏/李氏）；公网 `/login.html` 200、实例内 nginx80=200、`/actuator/health` 外部 403（预期，仅本机 200）；启动后日志 `ERROR` 级 0 条（判定用 `grep " ERROR "` 匹配级别列，直接 `grep ERROR` 会被 `BIZ_ERROR` 业务文案污染）；login 页短信表单元素与 `login.js?v=20261123` 已出。仓库版 compose/.env.example 未上传（§0 空库陷阱） |
| 2026-11-24 | `family-tree:v78-local` | HEAD `2e2f74f` + 工作区（v77 全部内容 + 公开开关端点 `GET /api/auth/config`、注册页手机号/验证码按开关转必填、密码登录框手机号自动带入短信登录，`login.js?v=20261124b`）；本机 `./mvnw -B clean package` 全量单测绿；4b 换 jar 层（基座继承 v77-local，jar sha `c5cbe5eb…` 本地/实例/镜像内/运行容器四处一致） | 否（无迁移变化，历史表顶 `40`） | 发版前备份 `/opt/family-tree/backup/family_tree-pre-v78-20261124-082327.sql`（381K，backup 目录本次首建；容器名是 `family-tree-mysql` 非 `ft-mysql`，mysqldump 无 `--skip-lock-wait` 选项）；`.env` FT_IMAGE 切 `v78-local`（sed 留 `.bak-pre-v78`），回滚锚 v77-local 保留；哨兵四行 OK rc=0；`/api/auth/config` 返回 `registerSmsVerify:true`（生产 `SMS_VERIFY_ENABLED` 开，注册页已按必填口径显示）；读数 29 表/9 族/167 账号、flyway 顶 40；实例 nginx80=200、公网根 200、`/login.html` 已带 `?v=20261124b`；启动后 ` ERROR ` 仅 2 条且均为公网扫描器打 `/webui/`、`/favicon.ico` 的静态资源噪音（对应门控项 #203 未知路径语义），应用侧无异常。仓库版 compose/.env.example 仍未上传（§0 空库陷阱） |
| 2026-11-24 | `family-tree:v79-local` | HEAD `2e2f74f` + 工作区（v78 全部内容 + AI 配置状态端点 `GET /api/ai/status`（`ai.llm.enabled && AI_API_KEY 非空` 才 configured，需登录）、main.js 初始化按状态隐藏 `#btn-ai` 机器人浮动按钮（查询失败维持现状），`main.js?v=20261124a`；新增 AiControllerTest 4 用例）；本机 `./mvnw -B clean package` 全量单测绿；实例上 4b 换 jar 层（基座继承 v78-local，jar sha `650a602a…` 本地/上传件/镜像内/运行容器四处一致） | 否（无迁移变化，历史表顶 `40`） | 发版前备份 `/opt/family-tree/backup/family_tree-pre-v79-20261124-085123.sql`（390K/29 表）；`.env` FT_IMAGE 切 `v79-local`（sed 留 `.bak-pre-v79`），回滚锚 v78-local 保留；08:52:23 停 app → 08:53:12 `200 OK`（约 49s）；哨兵四行 OK rc=0；读数 29 表/9 族/167 账号、flyway 顶 40、最新家族「罗氏家族」UTF-8 正常；`/api/ai/status` 未登录 401（公网与实例内一致，端点已注册且受登录保护）；实例 nginx80=200、公网根 200、`/index.html` 已带 `?v=20261124a`；v79 启动窗口 ` ERROR ` 0 条。生产未配 `AI_LLM_ENABLED/AI_API_KEY` → AI 未配置态，前端机器人按钮应隐藏。仓库版 compose/.env.example 仍未上传（§0 空库陷阱） |
| 2026-11-24 | `family-tree:v80-local` | HEAD `98018fe`（已 commit+push，发版前置项已按新约定收口：v77~v80 全部代码在库）——v79 全部内容 + 短信登录开关下发前端：`SmsCodeApplicationService.isSmsLoginEnabled` 读 `SMS_LOGIN_ENABLED`，公开端点 `/api/auth/config` 增 `smsLoginEnabled` 字段，`login.js` 拉取为 false 时隐藏「短信验证码登录」切换入口（字段缺失/拉取失败维持现状），`login.js?v=20261124c`；本机 `./mvnw -B clean package` 全量单测绿；实例上 4b 换 jar 层（基座继承 v79-local，jar sha `c39e9763…` 本地/上传件/镜像内/运行容器四处一致） | 否（无迁移变化，历史表顶 `40`） | 发版前备份 `/opt/family-tree/backup/family_tree-pre-v80-20261124-122717.sql`（390,497B/29 表）；`.env` FT_IMAGE 切 `v80-local`（sed 留 `.bak-pre-v80`），回滚锚 v79-local 保留；12:29:24 停 app → 12:30:13 `200 OK`（约 49s）；`/api/auth/config` 返回 `{smsLoginEnabled:true, registerSmsVerify:true}`；`/login.html` 已带 `?v=20261124c`；哨兵四行 OK rc=0；启动窗口 ` ERROR ` 0 条；实例 nginx80=200。生产两开关均开，短信登录入口保持显示。仓库版 compose/.env.example 仍未上传（§0 空库陷阱） |
| 2026-11-24 | `family-tree:v81-local` | HEAD `8395f3b`（commit+push 前置）——首登强制改密自助绑定手机号（`login.html` 改密表单增选填手机号，`login.js` 先 `PUT /api/auth/profile` 绑定、失败可改正/留空重试，再提交改密）+ 短信发码防连点（点击即禁用、限流类拒绝进 60s 冷却）；本机 `./mvnw clean package` 全量单测绿；jar sha `5652347a…` 本地/上传件/镜像内/运行容器四处一致 | 否（无迁移变化，历史表顶 `40`） | **路线三首战**：实例上 `docker build FROM family-tree:v74-baseexport`（不再链式继承上一版镜像，Config 继承 baseexport 正确、层数 2）；发版前备份 `/opt/family-tree/backup/family_tree-pre-v81-20261124-132756.sql`（478,869B）；`.env` FT_IMAGE 切 `v81-local`（留 `.bak-pre-v81`），回滚锚 v80-local；13:29:05 停 app → 13:29:53 `200 OK`（约 48s）；哨兵四行 OK rc=0；`/api/auth/config`=`{smsLoginEnabled:true, registerSmsVerify:true}`；读数 29 表/9 族/167 账号/flyway 40；`login.html` 已带 `?v=20261124d` 且 `change-phone` 字段已出（实例内与公网 IP 双侧）；启动窗口 `" ERROR "` 0 条（注：`family-tree.log` 为跨版本累计文件，整文件 grep 会得到历史计数，判级必须按启动窗口时间戳切片，时间为 UTC）；nginx80=200、公网根 200 |
| 2026-11-24 | `family-tree:v81-local`（k3s 蓝绿演练，同镜像） | 无新代码（HEAD 不变，`1d2ae02` 登记在途）；机制验证=§11 全流程首跑：绿槽 8092 在任 → 蓝槽 8091 旁路拉起（87s Ready）→ 旁路验收全绿 → nginx 切流 8092→8091（reload 零中断）→ 绿槽缩 0 → 绿槽唤回演练（rollout OK、8092 health UP、期间蓝槽持续 200）→ 复归休眠锚 | 否（同镜像无迁移变化） | 切流后入口复验：root80=200、config 双开关 true、`?v=20261124d`、公网 200；~~蓝槽 stdout `" ERROR "` 0 条~~（当日登记原文，**19:50 更正为假绿**：profile `mysql` 下 stdout 只有启动横幅，业务日志一条不在里面。改按 §9 末条口径读文件，实数 = 演练窗口 08:00–09:00 UTC 切片 `family-tree-error.log` **2 条**、当日（UTC）累计 **150 条**，`No static resource` 计数同为 150 → **扫描器未知路径噪音 100%，应用异常 0 条**）；内存 avail 切流后 ~1.8G；conf 备份 `.bak.pre-k3s-*`（8090 版）与 `.bak.pre-blue-*`（8092 版）两级回滚链完整。compose app 本次仍 Up（同日稍后退役，见下一行） |
| 2026-11-24 | （无新代码）compose app 退役 + 历史镜像清理 | HEAD 不变；实例侧动作：`docker stop family-tree-app`（容器保留、`Exited (143)`，镜像 tag 已是 `family-tree:v81-local`），在任应用改由 k3s 蓝槽 :8091 承载；mysql/redis 与命名卷不动（§0） | 否 | 清理前把历史 jar 归档 `/opt/family-tree/backup/jars-pre-retire-20261124/`（与 13:12 的 `jars-pre-prune-20261124/` 两份）。实例现存镜像仅 `family-tree:v81-local`（455MB；containerd 侧同 sha256 `c502bea6…`）+ `family-tree:v74-baseexport`（318MB）——**v79/v80 镜像已不在实例上**，上一行写的「回滚锚 v80-local」自此只等于归档 jar，回滚更早版本 = 取 jar 走路线三重建。**（2026-11-25 发 v82 时复核：`v74-baseexport` 也已不在实例上，`docker images` 只剩 `v81-local` 一个 tag，路线三的基座断链随之失效，见 §4c）** |

| 2026-11-25 | `family-tree:v82-local` | HEAD `c1517ef` + **工作区 21 个文件未 commit 就上生产**（注解式限流 `@RateLimit`+`RateLimiter`（Caffeine 本地窗口与 Redis 账本双写、判定取大）、`ClientIpResolver` 口径修正并被 `BaseController.getClientIp`/`CsrfFilter` 采用、`LoginAttemptService` Redis 容错、`application.yml` 新增 `spring.data.redis.timeout`/`connect-timeout=500ms`、web 模块引入 caffeine、新增 3 个测试类 24 用例）；本机 `./mvnw -B test` = **417（application）+ 95（web）全绿** | 否（本版未动迁移，jar 内 `db/migration/mysql` 顶版 V40 == 历史表顶 `40`、`success=1`） | **实例侧构建（§4c，本机 Docker daemon 没起）**：只传 jar（137,878,209B / sha256 `e896a563…`，本地＝实例 `/root/ft-build/app.jar`＝镜像内 `/app/app.jar` 三处一致）→ `FROM family-tree:v81-local` 换 jar 层，镜像 `b0ff143c…`、层数 2→3、Entrypoint/Env/WorkDir/Expose 与 v81 逐字同 → `docker save \| k3s ctr -n k8s.io images import -`（containerd `a30a9aed…`，567MiB）；绿槽 apply→rollout ~90s；20:48:04 备份 conf `.bak.pre-v82-20261125_204804` → sed 6 处 8091→8092（`left_8091=0`）→ `nginx -t` OK → reload 零中断；蓝槽 `scale 0` 转休眠锚（v81 镜像 docker+containerd 双在 = 回滚锚）；哨兵 `mode=k3s base=http://127.0.0.1:8092` 四行 OK rc=0；入口复验 root80=200、`/api/auth/config` 双开关 true、`login.html` 200（`login.js?v=20261124d` 未变，本版无前端改动）、公网 `8.153.176.50` 200；日志切片按 **UTC** `12:44` 之后 `" ERROR "` **0 条**（线程名 `http-nio-8092` 仅 6 行，全是本次探针）；**§7b 限流首上生产即实测**：同一 `X-Real-IP: 203.0.113.242` 连打 34 次全按窗口计，第 35 次 `429 + Retry-After: 39` + 体 `{"code":429,"message":"登录尝试过于频繁，请稍后再试"}`，Redis 侧 `rate:limit:auth-ip:203.0.113.242:29926847` 值 35、`ttl=117`；换 IP 立即恢复；`operation_log` 里本版真实 LOGIN 的 `ip_address` 仍是真实公网（117.154.99.118 / 223.104.121.31）→ 环回对端采信 `X-Real-IP` 的链在 nginx 后成立。**偏差记账**：① 代码当时未 commit（用户只点名"部署"），**已于 2026-11-25 补账：commit `4223351`（含本批 21 文件，本机 `./mvnw -B clean test` 六模块全绿复核）已 push，镜像 ↔ commit 复现闭环**；② §4b 留存的 `v74-baseexport` 已不在实例上（见 §10 更正），本版退回链式继承 `v81-local`；③ 切流「不掉线」这一项今晚量不到——最后一次真实 LOGIN 是 09:45 CST、会话 TTL 30m，`spring:session:*` 键数 0 属空闲而非丢会话，需用户浏览器实测（§7 末段那条人工验收）；④ 探针在 `operation_log` 留下 123 行 `LOGIN_FAIL`（`__probe__`/`__probe2`/`__probe3`，IP 为 TEST-NET 段）不清理，保留审计原样；短窗 `rate:limit:auth-ip:203.0.113.2*` 与 `login:attempt:__probe*` 已 DEL，长窗 `auth-ip-slow:*` 故意留着（删了等于把刚建立的 10 分钟额度放回去） |
| 2026-11-25 | `family-tree:v83-local` | HEAD `ba169c8`（发版自带 commit+push：`e0fa38b` 开关批次——`/api/auth/config` 下发 `registerEnabled`+login.js 注册入口显隐、send-code REGISTER 场景补断言 `REGISTER_ENABLED`、`/api/ai/status` 纳入三级生效链、GEDCOM 导入接节点配额断言（`login.js?v=20261125a`）；`ba169c8` 巡检扩容——数据巡检 12→23 项只读探测，新增 `findFinding` 3 参重载承载 `InspectionQuota`，SQL H2+MySQL 双方言实测） | 否（本版未动迁移，jar 内 `db/migration/mysql` 顶版 V40 == 历史表顶 `40`） | 发版前备份 `backups/family_tree_20261125_220719.sql.gz`（57K/29 表）；本机 `./mvnw -B clean package` 全绿，jar sha256 `7e4667b8…`（本地＝实例 `/root/ft-build/app.jar`＝镜像内 `/app/app.jar` 三处一致；jar 内 `InspectionCheckEnum` javap 23 常量，防旧 class 复用）；**§4c 实例侧构建**：`FROM family-tree:v82-local` 换 jar 层，镜像 `cbfadf0d…`、层数=上版+1 → `docker save \| k3s ctr -n k8s.io images import -`（containerd `99c8eb26…`，698.6MiB）；蓝槽 apply→rollout ~86s；旁路验收：health UP、config 三开关 `registerEnabled:true` 已下发、`login.js?v=20261125a`、读数 29 表/10 族/171 账号/flyway 顶 40、蓝槽启动窗口（UTC 14:10 后）error 日志 0 条；22:13 备份 conf `.bak.pre-v83-<ts>` → sed 6 处 8092→8091（`old_left=0`/`new_cnt=6`）→ `nginx -t`+reload 零中断；绿槽 `scale 0` 转休眠锚（v82 docker+containerd 双在＝回滚锚）；哨兵 `mode=k3s base=http://127.0.0.1:8091` 四行 OK rc=0；入口 root80=200、公网 200、两处 `?v=20261125a`；**切流不掉线实证**：登录用户会话轮询线程名 14:12:49 还是 `http-nio-8092`、14:13:00 起为 `http-nio-8091`，同一 redis 会话跨槽存活。巡检 23 项接口需平台管理员登录，curl 不可测，由单测+H2 实测覆盖。 |
| 2026-11-25 | `v84/v85/v86-local`（基座路线三轮连发演练，同 jar=在任 v83 `7e4667b8…`，无新代码） | 用户口令"处理完成之后连续给我部署ecs 3次 验证基座的问题"。**§4c 新路线 `FROM family-tree:app-base` 首三次实战**：R1 v84→绿槽 8092（22:28 切流）、R2 v85→蓝槽 8091（22:30）、R3 v86→绿槽 8092（22:31 末切）。每轮实测：build 0~3s（COPY 层命中缓存，三 tag 同镜像 ID `2fb7760bc751`）、`docker save \| k3s ctr import` 12~14s、apply→rollout ~86s、旁路验收四项（health UP/config 三开关/`?v=20261125a`/读数 29-10-171-40）全绿、nginx 6 处 sed+reload 零中断（conf 备份 `.bak.pre-r1/r2/r3-<ts>` 三份）、切流后线程名逐轮易主（14:27→`http-nio-8092`、14:30→`8091`、14:31→`8092`）、哨兵每轮 4×OK rc=0、公网三轮持续 200。体积恒定实证：三镜像均 **330MB**（=基座 192 + jar 层 138，不再 +138/版递增）；同 jar 时层复用，docker/containerd 侧三 tag 零增量。演练窗口 error 日志无新增（14 时段仅 2 条 `favicon.ico` No-static=在任 v83 窗口 #203 噪音，早于 R1） | 否（同 jar，flyway 顶 40 不变） | 终态：**绿槽 8092 `v86-local` 在任 | 蓝槽 `v85-local` scale 0 休眠锚 | `v84-local` 次级**（三者同镜像同 sha，回滚任一切换皆等价）；链式旧镜像 v81/v82/v83 保留未动（prune 仍需点名）；磁盘 20G/53%（较演练前 +1G=基座备份 63MB+jar 上传件 131MB+containerd 清单等，镜像层复用未涨）；内存 avail ~2.0G；compose mysql/redis Up 3d 未受影响。仓库清单 `deploy/ecs/k8s/deployment-{blue,green}.yaml` image tag 已同步（blue=v85/green=v86） |
| 2026-11-26 | （无新代码）历史镜像清理（用户点名"清除历史发布不用的镜像，保留最新版本即可"） | 删除 docker `v82-local`/`v83-local`/`v84-local` + containerd `v81~v84-local` 四标签，并 `k3s ctr content prune references` 回收孤儿 blob。**保留边界**：docker `app-base`（发版基座，路线依赖）、`v86-local`（在任）、`v85-local`（蓝槽休眠锚 manifest 引用，与 v86 同镜像零占用）、`v81-local`（仅因 Exited 急锚容器 `family-tree-app` 的父镜像而留——连它一起删需点名 rm 该容器） | 否 | 删前把镜像内 jar 抽档 `/opt/family-tree/backup/jars-pre-prune-20261126/`（v81 `5652347a…` / v82 `e896a563…` / v83 `7e4667b8…`，sha 与历次发版登记逐一吻合）→ 回滚更早版本=取档 jar 走 §4c `FROM app-base` 重建。清理后 disk 20G/52%（余 19G）、containerd 目录 1.2G；哨兵 4×OK rc=0、在任绿槽 health UP |
| 2026-11-26 | `family-tree:v87-local` | HEAD `c1323f2`（发版自带 commit+push）：导出开关旁路修复——`/api/auth/me` 家族块下发 `dataExportEnabled`/`gedcomImportEnabled`（三级生效链 isEnabled）、main.js 按开关隐藏 PDF/GEDCOM 导出与 GEDCOM 导入共 6 入口（工具栏+侧边栏，判定在角色显示之后）、modal-tools.js `exportGedcom` 改 fetch+content-type 判别（错误 JSON 不再存成假 .ged），`main.js`/`modal-tools.js` bump `?v=20261126a` | 否（本版未动迁移，jar 内顶版 V40 == 历史表顶 `40`、`success=1`） | 发版前备份 `backups/family_tree_20261126_202908.sql.gz`（60K/29 表）；本机 `./mvnw clean package` 全量单测绿，jar sha256 `49b2dddff897…` 三点一致（本机 target＝实例 `/root/ft-build/app.jar`＝镜像内 `/app/app.jar` 掏检）；**§4c `FROM family-tree:app-base`** 构建 `ebb64f52…` 330MB、history=基座 import 层+COPY jar 层；`docker save \| k3s ctr import`（containerd `6a329d40…`）。蓝槽 apply（tag v85→v87）→rollout ~87s；旁路验收 8091：health UP、config 三开关 true、login.html 200、index 两 js `?v=20261126a`、**下发资产内容实证**（modal-tools `application/json` 判别×1、main `dataExportEnabled`×2）、匿名 `/api/auth/me`=401；error.log 蓝槽启动窗口零新增（19:40 CST 前仅 #203 扫描器噪音）。20:32 备份 conf `.bak.pre-v87-<ts>` → sed 6 处 8092→8091（old_left=0/new_cnt=6）→ `nginx -t`+reload 零中断；绿槽 `scale 0` 转休眠锚（v86 docker+containerd 双在＝回滚锚）；哨兵 `mode=k3s base=http://127.0.0.1:8091` 四行 OK rc=0；入口 root80/login=200、在任 index `main.js?v=20261126a`、公网 200；**切流强证据**：12:33:01 UTC error.log `favicon.ico` 条目线程名=`http-nio-8091-exec-5`（新流量落蓝槽）。仓库 manifest blue tag 已同步 v87 |
| 2026-11-26 | `family-tree:v88-local` | HEAD `6527598`（发版自带 commit+push）：详情弹窗按钮列对齐——`.affinity-title .btn-sm` 右移 13px（= 行卡片 1px 边框 + 12px 内边距，标题行本身无边框/内边距），使「关联亲族」与 `.spouse-row`/`.affinity-row` 内的婚姻设置/解除关联/切换查看内容右缘齐平；index/admin/portal 三页 `style.css?v=20261126b`（`modal-node.js` 未改故未 bump） | 否（本版纯静态，jar 内顶版 V40 == 历史表顶 `40`） | 发版前备份 `backups/family_tree_20261126_210908.sql.gz`（gzip -t 过、29 表、中文无乱码）；`./mvnw -B test` 688 用例 0 失败 + `./mvnw -B clean package -DskipTests`（带 clean）→ jar 137,889,855B / sha256 `23027ae1…` **三点一致**（本机 target＝实例 `/root/ft-build/app.jar`＝镜像内 `/app/app.jar` 掏检）。**§4c `FROM family-tree:app-base`**：镜像 `8164049460f5` 330MB、`docker history` 层数=2（基座 import + COPY jar）、Config 与基座一致；`docker save \| k3s ctr import` 13s（containerd `b4fd8eed…`）。**发绿槽** apply（tag v86→v88，实例 `/opt/family-tree/k8s/deployment-green.yaml` 同步）→rollout 86s；旁路验收 8092：health UP、config 三开关 true、login.html 200、匿名 `/api/auth/me`=401，**三页均下发 `style.css?v=20261126b`** 且 `curl .../css/style.css` 尾部实证含 `margin-right: 13px`；绿槽启动窗口（13:13 UTC 之后）error.log ` ERROR ` 0 条。21:15 备份 conf `.bak.pre-v88-20261126-211507` → sed 6 处 8091→8092（old_left=0/new_cnt=6）→ `nginx -t`+reload 零中断；入口 root80/login/config=200、经 nginx 的 index 与 CSS 同为新版、公网 200；**切流强证据**：末 400 行线程名 7/7 = `http-nio-8092`（无 8091 残留）；蓝槽 `scale 0` 转休眠锚（v87 docker+containerd 双在＝回滚锚）；哨兵 `mode=k3s base=http://127.0.0.1:8092` 四行 OK rc=0；切流后读数 29 表/family 10/account 171 与预检逐字一致（注：`MAX(version)=9` 属 VARCHAR 字符串序假象，顶版仍 40）。内存 avail 2.0G、disk 21G/55%。仓库 manifest green tag 已同步 v88。**未做**：登录后真机点击流复核（本机登录需 Redis，Docker Desktop 保持关闭），仅静态资产下发与几何实测双重证据 |
| 2026-11-27 | `family-tree:v89-local` | HEAD `28c02a0`（平台管理员 A/B 图谱批次：A 治理型跨族图谱、B 单家族人物网络；跨族边颜色/异常样式修复；`admin-graph.js?v=20261126d`） | 否（本版未动迁移，jar 内顶版 V40 == 历史表顶 `40`、`success=1`） | **发布时间=2026-11-27 12:28:09 CST / 04:28:09 UTC；发布内容=平台管理员 A 治理型跨族图谱与 B 单家族人物网络上线，含跨族边颜色、异常虚线与筛选修复。** 本机 `./mvnw -B clean package` 全量 **708/708** 通过，jar 137,925,755B / sha256 `8f3e07d3…` 三点一致（本机 target＝实例 `/root/ft-build/app.jar`＝镜像内 `/app/app.jar`）；发版前备份 `backups/family_tree_20261127_110227.sql.gz`（60K/29 表/gzip 完整/中文正常）。**§4c `FROM family-tree:app-base`**：镜像 `14dcf6293da2` 330MB、history=2、Config 与基座一致；`docker save \| k3s ctr import`（containerd manifest `6f1d2043…`）。**发蓝槽** apply（tag v87→v89）→rollout ~86s；旁路验收 8091：health UP、config 三开关 true、admin.html 与 graph.js 版本 `20261126d`、TYPE_CODES/数值过滤/issues 虚线/姻亲颜色资产实证、匿名 overview/family/network 三端点均 401、读数 29 表/10 族/171 账号/flyway 顶 40、启动窗口 error 0。**04:28:09 UTC 切流**：备份 conf `.bak.pre-v89-20261127_042809` → sed 6 处 8092→8091（old_left=0/new_cnt=6）→ `nginx -t`+reload；入口 root80/login/config/graph.js 与公网 80 全 200。切流前 Redis 活跃会话 1、切流后仍 1（TTL 1791s）；末 80 行线程名 80/80=`http-nio-8091`（末 400 行为 8091=324、8092=76，旧槽停写后迅速归零）。绿槽 `scale 0` 转休眠锚（v88 docker+containerd 双在＝回滚锚）；哨兵 `mode=k3s base=http://127.0.0.1:8091` 四行 OK rc=0；切流后读数与基线逐字一致，内存 avail 2.0G、disk 21G/56%。**唯一 error**=04:28:09.855 切流瞬间探针在旧槽 8092 打到尚未下发的 `/js/ft/admin-graph.js` 产生 1 条 No-static-resource；同一探针随后经新槽返回 200 且内容核验通过，非应用异常。仓库 manifest blue tag 已同步 v89 |
| 2026-11-27 | `family-tree:v90-local` | HEAD `39d0f34`（新增公开邀请短链 `/s/{inviteCode}`；邀请链接与二维码统一短地址；旧长链接兼容；大小写规范化；未知码 404；`modal-family.js?v=20261127a`） | 否（本版未动迁移，jar 内顶版 V40 == 历史表顶 `40`、`success=1`） | **发布时间=2026-11-27 21:48:33 CST / 13:48:33 UTC；发布内容=邀请码短链接服务上线。** 本机 `./mvnw clean package` 全量 **711/711** 通过，短链专项 13 个用例通过，jar sha256 `ac856138…` 三点一致（本机 target＝实例 `/root/ft-build/app.jar`＝镜像内 `/app/app.jar`）。发版前备份 `backups/family_tree_20261127_214242.sql.gz`（gzip 完整、29 表、中文正常，sha256 `2ec9e57c…`）。**§4c `FROM family-tree:app-base`**：镜像 `3a140de5f7eb` 330MB、history=2、镜像内 jar 摘要一致；`docker save \| k3s ctr import`（containerd manifest `6a39513a…`）。**发绿槽** apply（tag v88→v90）→rollout 87s；旁路验收 8092：health UP、config 200、login 200、`modal-family.js?v=20261127a`、有效码/大小写变体 302 且 `Location=/login.html?invite=665AYXS6`、未知码 404、旧长链 200、读数 29 表/10 族/181 账号/flyway 顶 40。**13:48:33 UTC 切流**：备份 conf `.bak.pre-v90-20261127_214833` → sed 6 处 8091→8092（old_left=0/new_cnt=6）→ `nginx -t`+reload；入口 root80/login/config/短链与公网 80 全通过，公网短链最终到达 `https://msjzp.xyz/login.html?invite=665AYXS6`。切流前 Redis 活跃会话 2、切流后仍 2（TTL 1799s/1732s）；切流后 42 条应用日志均为 `http-nio-8092`，无 8091 残留。蓝槽 `scale 0` 转休眠锚（v89 docker+containerd 双在＝回滚锚）；哨兵 `mode=k3s base=http://127.0.0.1:8092` 四行 OK rc=0；切流后 error.log 新增 0 条。仓库 manifest green tag 已同步 v90 |
| 2026-11-28 | `family-tree:v94-local` | 未提交工作区，HEAD `853795d`；新增公开 Home、匿名/已登录根路由分流、登录页返回首页、注册开关与邀请码入口；本机 `./mvnw -B clean package` 747 用例全绿 | 否（无新增迁移，Flyway V41 `success=1`） | **发布时间=2026-11-28 17:33:16 CST / 09:33:16 UTC；发布内容=公开 Home 与会话感知根路由上线。** jar SHA256 `8d0fe214…` 本地/实例/镜像内一致；发版前备份 `backups/family_tree_20261128_172114.sql.gz`。`FROM family-tree:app-base` 镜像 `c050a3f7…`、containerd manifest `c681e9a8…`。绿槽 8092 apply Ready 后旁路验收通过；nginx 六处 8091→8092，备份 `.bak.pre-v94-20261128_173316`；蓝槽 v93 缩容 0 为回滚锚。哨兵 rc=0；公网 IP 根/Home/登录/config 通过，匿名受保护端点 401；读数 32 表/19 族/205 账号/Flyway V41 success。切流前后 Redis 活跃会话均 0，不作会话连续性结论。绿槽仅有 favicon 静态资源噪音 1 条，切流瞬间旧蓝槽 home.html 静态资源噪音 1 条。生产浏览器视觉验收被 `ERR_BLOCKED_BY_CLIENT` 阻断；本批未 commit/push，详见 `RELEASE-LOG.md` v94 |
| 2026-11-28 | `family-tree:v95-local` | 发布时为未提交工作区，HEAD `853795d`（v91~v94 未提交基线 + 本批）；发布后补提交 `f0ccb6a` + 登记 `e07b1d6` 已 push；登录态可进 Home（`GET /home` 固定进公开页，不再按会话分流）、新增不建会话也不轮换 CSRF 的 `GET /api/auth/status`、`PUBLIC_API_PATHS` 纳入该端点、导航文案「产品首页」统一为「首页」并在 index 下拉/抽屉、portal、admin 补齐首页入口、五页 `style.css?v=20261128d`；本机 `./mvnw -B clean package` **752** 用例全绿 | 否（无新增迁移，jar 内 MySQL 顶版 V41 == 历史表顶 `41`、`success=1`） | **发布时间=2026-11-28 18:08:37 CST / 10:08:37 UTC；发布内容=登录态 Home 与「首页」导航统一。** jar SHA256 `56ec457a…` 本地/实例/镜像内三点一致，`javap` 复核 `publicHome`/`authStatus` 在字节码内；发版前备份 `backups/family_tree_20261128_180031.sql.gz`（74,848B/32 表/gzip 完整/中文正常）。`FROM family-tree:app-base` 镜像 `be3ba6fa83e1` 330MB、层数 2、containerd manifest `d6eb71ba…`。蓝槽 8091 apply→rollout 86s，旁路验收：health 200、config 三开关 true、匿名 `/home` 200、`/api/auth/status` 回 `loggedIn:false`、index/admin/portal/login 四页 `产品首页` 0 处且含 `href="/home"`、四个匿名受保护端点 401、蓝槽启动窗口 `" ERROR "` 0 条。nginx 六处 8092→8091（old_left=0/new_cnt=6），备份 `.bak.pre-v95-20261128_180837`→`nginx -t`+reload 零中断；绿槽 v94 缩 0 为回滚锚。入口本机与公网 IP 双侧 `/ /home /index.html /login.html /admin.html /portal.html` 全 200、CSS 已是 `20261128d`（v94 只能出 `20261128b`＝新流量落蓝槽）。哨兵 `mode=k3s base=http://127.0.0.1:8091` 四行 OK rc=0；读数 32 表/19 族/205 账号/Flyway 41 与预检逐字一致；切流后 `" ERROR "` 0 条；内存 avail 2.0G、disk 64%。**会话连续性**：切流前后 Redis 活跃会话均为 1、同一键摘要 `5466dff3…`（TTL 119s→94s 自然递减＝空闲未续期），只证数据跨槽存活，不作端到端不掉线结论。发布后按用户口令「push」补提交：功能 `f0ccb6a` + 登记 `e07b1d6` 已 push `origin/main`，v95 镜像自此可由 commit 复现，详见 `RELEASE-LOG.md` v95 |

> **v74 数据订正附注（蒙斌/胡兰亲族案例，两库同口径）**：`family_node_link` 补 `1:54↔39:296`（手工链，ECS 缺）、`1:12↔40:305`、`1:21↔39:298`、`1:21↔40:328`、`1:56↔39:304`（`link_type=1`，`source=1 ACCOUNT`，ECS 首条为 `source=2/confirmed_by=1`）；`family_relation` 补 family 1 母边 `12→20`、`12→21`、`54→56`（`relation_type=1`，`user_id=1`）。回滚镜像不回滚这份数据（多行数据新代码不依赖旧行为，旧代码读到也只是多显示亲族行，不报错）。

v73 一层的实测数（下次发版拿它当对照，别看旧文档里的数）：

- 停机窗口：15:47 停 app → 16:00:03 `app 200 OK`，**约 13 分钟**，其中 11 分钟卡在 flyway 镜像拉取（应用本身 40.4s 起来）
- 切换前后业务读数一字不差：`29 表 / family 9 / account 166 / max(family.id)=40 / 最新家族「罗氏家族」`；`is_widowed tinyint(1) default 0` 在位
- `ft-sentinel.sh` 四行全 OK、`rc=0`（mysql/redis TCP 通、health 200、假凭据登录 2s 返回）；`http://8.153.176.50/` 200、`/login.html` 200、实例内 443 自测 200
- 日志文件里 15:55 之后 ERROR = 0；redis `dbsize=1`（只有哨兵留下的 `login:attempt:__sentinel__`，说明事故期间 + TTL 30m 后旧会话本就已过期，不是这次重启弄丢的）
- 应用日志时间戳是 **UTC**（`07:59:56` = CST 15:59:56），比对停机窗口时别按本地时间读
- **日志只在宿主文件里，两个面的「容器日志」都不能当验收**：profile 仍是 `mysql` → logback 只挂文件 appender（`ecs` profile 才挂 CONSOLE）。compose 形态 `docker logs family-tree-app` 是空的；**k3s 形态更坏——`k3s kubectl logs` 不是空而是有内容**（Spring 启动横幅 + 一条 logback 内部 WARN），看着像有日志，实则运行期一条业务日志都不在里面，`grep -c " ERROR "` 恒为 0 = 假绿。验收口径统一为读文件：`/opt/family-tree/logs/family-tree.log`（全级别）与 `family-tree-error.log`（仅 ERROR），按日切成 `*.YYYY-MM-DD.log`；蓝绿两槽共用同一 hostPath（清单 `volumeMounts: logs=/app/logs` ↔ `hostPath: /opt/family-tree/logs`），谁在任谁写，线程名 `http-nio-809x-exec-*` 可反查是哪个槽。

> 脏工作区构建出来的镜像**不可从任何 commit 复现**。要么发版前先 commit，要么在第 4 步把 `git status --porcelain` 一并存档；本表只记事实，不代表认可长期跳版本发布。

## 10. 实例上这些东西的来历

| 路径 | 作用 |
|---|---|
| `/opt/family-tree/ft-sentinel.sh` | 第 3/6/7 步的验收脚本 |
| `/usr/local/sbin/ft-firewall-guard.sh` | 网桥 → trusted 的幂等重绑 |
| `/etc/systemd/system/ft-firewall-guard.{service,timer}` | 开机 60s + 每 5min 兜底（面板重启 firewalld 会自己长回来） |
| `/etc/firewalld/zones/trusted.xml` | `--permanent` 落盘的那个网桥名（仅作二次保险） |
| `/opt/family-tree/RELEASE-SOP.md` | 本文件的实例侧副本（systemd unit 的 `Docs:` 指这儿）；仓库改了要重传 |
| `/opt/family-tree/RELEASE-LOG.md` | 2026-11-27 起的独立发版台账；每次发版追加记录并重传 |

脚本的**源头在仓库** [`ops/`](ops)（改完重传实例，别只在 `/tmp` 里留一份）：

| 仓库文件 | 实例落位 | 用途 |
|---|---|---|
| `ops/ft-preflight.sh` | `/tmp/`（一次性） | 第 1 步只读预检 |
| `ops/ft-release-check.sh` | `/tmp/` | 发版过程中的状态复查（容器/镜像、Flyway 读数、业务基线、`is_widowed`、80/443 健康、日志尾部） |
| `ops/ft-firewall-guard.sh` | `/usr/local/sbin/` | 网桥自愈 |
| `ops/ft-firewall-guard.{service,timer}` | `/etc/systemd/system/` | guard 的开机/定时触发 |
| `ops/ft-sentinel.sh` | `/opt/family-tree/` | 验收哨兵（2026-11-24 重写为形态自适应，入库 `681d974`；实例上旧 compose-only 版留 `/opt/family-tree/ft-sentinel.sh.bak-20261124`，`md5 10d5ab9a…`，重装后实例 md5 = 仓库 `e9d7c6f2…`） |
| `ops/ft-v7-checksum-fix.sh` | `/tmp/` | 第 5 节的 SQL 兜底（带旧值守卫，只对 V7 这一行） |

## 11. ECS 单机 k3s 蓝绿发版（2026-11-24 起新增部署面）

ECS 上另装了单机 k3s v1.30.14（cgroup v1 主机锁 1.30；严禁升 1.31+）。compose 栈继续承担 MySQL(:3308)/Redis(:6381) 数据面；应用层在 k3s 蓝绿双槽（蓝 :8091 / 绿 :8092）间零停机互切。当前在任=**蓝槽 8091（`v95-local`，2026-11-28 10:08:37 UTC 切流；登录态 Home 与「首页」导航统一）**，绿槽 `v94-local` replicas=0 休眠锚兼回滚镜像（docker 与 containerd 两侧都在）。v91~v95 的功能基线已于 2026-11-28 发布后统一入库：功能 commit `f0ccb6a`、登记 commit `e07b1d6`，均在 `origin/main`。**compose app 已于 2026-11-24 退役**（容器 Exited 保留、其父镜像 `v81-local` 暂保留）。

> ⚠️ 退役后的 compose app **不是版本回滚锚**：它的镜像已经是 `family-tree:v81-local`，`docker start family-tree-app` + nginx 切回 8090 只是把「应用跑在哪个面」换回去（拓扑回滚），代码版本仍是当前版。要回旧版本得按 §9 的镜像/jar 记录重建（v79/v80 镜像已 prune，只有归档 jar）。

### 11.1 一次性环境（已就绪，重建实例才需要重跑）

- k3s 安装：本机下载二进制+sha256 校验→workbench upload→`INSTALL_K3S_SKIP_DOWNLOAD=true INSTALL_K3S_SKIP_SELINUX_RPM=true INSTALL_K3S_EXEC="--disable=traefik --disable=servicelb --write-kubeconfig-mode=644"`；系统镜像走 `/etc/rancher/k3s/registries.yaml` daocloud 镜像。
- namespace `prod`；secret：`k3s kubectl -n prod create secret generic ft-app-env --from-env-file=/opt/family-tree/k8s/green.env`（env 源自 `docker inspect family-tree-app`，改写 DB→`127.0.0.1:3308`、Redis→`127.0.0.1:6381`，**删掉裸 `-XX:` 行否则 key 名非法**；green.env chmod 600，严禁入库）。
- 清单入库：本目录 [`k8s/`](k8s)（`deployment-green.yaml`/`deployment-blue.yaml`/`app-env.keys.txt` 仅键名），实例落位 `/opt/family-tree/k8s/`。
- 网络约束（实测）：pod overlay 无 SNAT、pod→宿主 eth0 端口不通 → app pod 必须 `hostNetwork: true + dnsPolicy: Default`；镜像 `imagePullPolicy: Never`，每版需导入 containerd。

### 11.2 每次发版（蓝绿互切，零停机）

> **2026-11-27 授权规则**：用户已明确后续 ECS 常规发版按既有 SOP 自动执行，不再逐次确认切流/部署；`clean package`、备份、旁路验收、六处全量切换、旧槽缩容、哨兵/公网复验、SOP/manifest 登记、commit+push 仍是硬门槛；用户明确要求暂不提交的批次除外，并须在独立台账注明。破坏性迁移、清理生产资源、修改安全/网络配置等超范围动作仍按原有点名授权边界处理。

1. 第 1~4 步照旧（预检/备份/jar 按 §4c `FROM family-tree:app-base` 构建 `family-tree:vNN-local`，体积恒定 ~330MB）。
2. 导入 k3s：`docker save family-tree:vNN-local | k3s ctr -n k8s.io images import -`
3. 起**空闲槽**（当前在任的反面）：`sed 's/green/blue/g; s/8092/8091/g' deployment-green.yaml > deployment-blue.yaml`（反向同理），改 image tag 后 `k3s kubectl -n prod apply -f` + `rollout status --timeout=180s`（实测 ~87s）。
4. 旁路验收打空闲槽端口（8091/8092）：health UP、`/api/auth/config`、`login.html ?v=`、读数。日志按 §9 的口径读**宿主文件**（`/opt/family-tree/logs/family-tree-error.log` 切启动时间戳之后的切片），**不要用 `k3s kubectl logs` 的 stdout 判 ERROR**——profile 是 `mysql`，stdout 里只有启动横幅，`grep " ERROR "` 恒 0 属假绿。
5. 切流：备份 conf → `sed -i 's|127.0.0.1:809X|127.0.0.1:809Y|g' /etc/nginx/conf.d/family-tree.conf`（6 处全量，sed 后要拿 `grep -c 127.0.0.1:809X` 复确认 =0、新端口 =6）→ `nginx -t && systemctl reload nginx` → 入口复验（root80/config/login.html/公网）。会话在 Redis，切流不掉线——**但这条要能验才有意义**：`docker exec family-tree-redis redis-cli --scan --pattern "spring:session:sessions:*" | wc -l` 得 >0 才有活会话可对照；用户空闲（会话 TTL 30m）时键数本来就是 0，既不能当证据也不能当反证，那种窗口只能靠「流量确实落到新槽」间接判：`grep -oE "http-nio-809[12]" /opt/family-tree/logs/family-tree.log | tail -400 | sort | uniq -c` 看线程名（2026-11-25 20:48 那次切流即属此类：入口四项 200、末 400 行里 351 行是 `8092`，`spring:session` 0 键是因为最后一次真实 LOGIN 在 09:45 CST）。
6. 旧槽 `scale --replicas=0` 转休眠锚（内存即回 ~700MB）。compose 数据面全程不动。
7. 登记 [`RELEASE-LOG.md`](RELEASE-LOG.md)：记录实际切流完成的 **CST/UTC 发布时间**、用户可读的 **发布内容**、commit、镜像/jar 摘要、备份、验收结果、在任槽位与回滚锚；台账与 SOP 同步上传实例后按 ECS 发版约定 commit+push。

### 11.3 回滚

- 秒级：nginx 反向 sed（或 `cp` 最近一份 `.bak.pre-*-<ts>`）+ reload 回旧槽。
- 旧槽已休眠：`scale --replicas=1`（冷启 ≤2min，实测 rollout OK 后探针 UP）再切回。
- 终极兜底：compose app :8090 已退役但可随时复活（`docker start family-tree-app` 等 ~50s health，再切回 `.bak.pre-k3s-*` conf + reload）。**注意这是拓扑回滚不是版本回滚**——它的镜像 tag 跟着最后一次 compose 发版走（现在是 `v81-local`），复活后跑的还是当前版本；真要退版本得改 `.env` 的 `FT_IMAGE` 指向旧镜像，而旧镜像可能已被 prune（见 §9 末行），此时只能取归档 jar 走路线三重建。
- 切流/回滚之后一律重跑 `/opt/family-tree/ft-sentinel.sh`（自适应形态，期望首行 `mode=k3s` + 四行 OK、`rc=0`）。它报 FAIL 时**先确认闸门本身是否与拓扑匹配**，再动生产（§3 的反面教材）。
- **含破坏性 Flyway 迁移的版本禁用蓝绿重叠**（新槽跑迁移时旧槽还在写），走 §6 停机换版路线。

### 11.4 已知挂起项

metrics-server 0/1（pod 网段打不到宿主 10250，不影响发布链）；overlay 无 SNAT 不修；~~`v74-baseexport` 单副本待 `docker save` 异备份~~（baseexport 已断链作废，**2026-11-25 由 `family-tree:app-base` 接替**，其实例本地 gzip 备份已落 `/opt/family-tree/backup/family-tree-app-base-20261125.tar.gz`——⚠仍与镜像库同盘同机，误删实例/整盘故障时无异地副本，异机落点待点名）；compose app 已于 2026-11-24 退役（容器仍在，见 §11.3 关于它不是版本锚的说明）。
