# ECS 发版 SOP（阿里云单机形态）

> 适用范围：阿里云 ECS `i-uf62f6628at8ehgwonbg`（cn-shanghai，`8.153.176.50`）上 `/opt/family-tree/` 的 compose 三容器栈。
> **不适用于**本机 Docker Desktop k8s 那套（术语叫 `prod`；ECS 这套叫 `ecs` 环境）。两边数据从同一份 dump 分叉，切流量前必须先冻结写入再重新 dump/restore，见「数据与备份」。
>
> 首次部署（建 `.env`、装 nginx、导数据）看 [`README.md`](README.md)；本文件只讲**已有一台在跑、要换新镜像**的重复性动作。

## 0. 边界：哪些动作不能在这条流程里顺手做

| 动作 | 状态 | 原因 |
|---|---|---|
| `docker compose up/down`、改 `.env` 的 `FT_IMAGE`、`repair` | 本 SOP 内，照做 | 就是发版本身 |
| `./ecs-setup.sh tobind`（命名卷 → 宿主 bind 目录） | **暂缓，需点名授权** | 实例上 compose 仍是命名卷（`family-tree_mysql-data` / `family-tree_redis-data`）。仓库版 compose 已改 bind 挂载，**直接传上去 = mysql 挂到空目录 = 安静建一套空 schema 并正常启动**（V7 修好后连报错都没有），等于把生产库换成空库还看不出来 |
| 把 `SPRING_PROFILES_ACTIVE` 从 `mysql` 切到 `mysql,ecs` / `mysql,prod` | **暂缓** | `prod` 在明文 HTTP 下下发 `Secure` Cookie → 登录 200 但下一个请求就 401；`ecs` 会打开 `forward-headers-strategy`，而 nginx 的 XFF / 内网白名单口径还没复核，有误锁公网用户的风险 |
| 安全组、DNS、备案、宝塔面板 | **不碰** | 由用户在控制台操作 |
| `git commit` / `push` | **不做** | 需用户点名 |

## 1. 预检（只读，5 分钟）

```bash
# 实例现状一屏打印：容器/profile/存储形态/Flyway 与业务读数/备份时间/磁盘内存/健康/镜像清单/cron
workbench upload deploy/ecs/ops/ft-preflight.sh /tmp/ --instance-id <i-xxx> --region cn-shanghai -f
workbench exec -i <i-xxx> -r cn-shanghai --timeout 120 -c 'bash /tmp/ft-preflight.sh'
```

必须逐条落纸，否则后面的「验收」没有对照物：

1. **在跑的是哪个镜像、compose 是哪种存储形态**：`docker ps` 的 tag + `docker volume ls`。形态决定第 5 步能不能传 compose（见第 0 节）。
2. **Flyway 基线**：`SELECT version, checksum FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;`、表数、关键表行数（本例 `29 表 / family 9 / account 166`）。行数是第 7 步的对照，**不是**「不该变」——真实用户写入会让它涨。
3. **最近一次备份**：`ls -lt /opt/family-tree/backups | head`，必须是今天的，否则先补第 2 步。
4. **镜像 tar 的落点与磁盘余量**：`/root/ft-migrate/`，`df -h /`（177MB 的 tar + load 后的 500MB 镜像层）。

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

`/opt/family-tree/ft-sentinel.sh` = 第 3、6、7 步的机器可读版本：mysql/redis 的 TCP 可达（从 app 命名空间）、`/actuator/health` = 200、登录接口 25s 内返回。任一项失败退出码 1。

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
/opt/family-tree/ft-sentinel.sh          # 期望三行 OK，rc=0
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

浏览器侧还有一项脚本量不到的：**真的登录一次、开一次族谱树**。会话在 redis 上，切镜像不该登出；若「登录成功、下一个请求就未登录」，那是 Cookie 属性（`Secure`）问题，见第 0 节 profile 行。

## 8. 回滚

改回 `FT_IMAGE` 再 `./ecs-setup.sh app` 就行——**但第 5 步做过 `repair` 的话，旧镜像对不上被改写后的历史表，回滚同样是「起不来」**。所以：

- 回滚到 repair 之前的版本 = 再跑一次 `repair`，用的是**旧那份迁移目录**（`migrations/mysql` 得留一份旧版本的 tar，或从旧镜像里 `jar xf` 掏）。这也是第 4 步坚持「tar 与镜像同一份代码」的原因。
- 真正干净的回滚锚点是第 2 步的 dump：`restore.sh mysql <备份文件>` 会覆盖现有数据，属生产写入，需点名。
- 窗口期别 `docker rm -f` 正在启动的 app 容器：迁移中途 kill 会在 `flyway_schema_history` 留 `success=0`，下次启动报 `Detected failed migration to version N`，还得再 repair。

## 9. 发版记录

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

> **v74 数据订正附注（蒙斌/胡兰亲族案例，两库同口径）**：`family_node_link` 补 `1:54↔39:296`（手工链，ECS 缺）、`1:12↔40:305`、`1:21↔39:298`、`1:21↔40:328`、`1:56↔39:304`（`link_type=1`，`source=1 ACCOUNT`，ECS 首条为 `source=2/confirmed_by=1`）；`family_relation` 补 family 1 母边 `12→20`、`12→21`、`54→56`（`relation_type=1`，`user_id=1`）。回滚镜像不回滚这份数据（多行数据新代码不依赖旧行为，旧代码读到也只是多显示亲族行，不报错）。

v73 一层的实测数（下次发版拿它当对照，别看旧文档里的数）：

- 停机窗口：15:47 停 app → 16:00:03 `app 200 OK`，**约 13 分钟**，其中 11 分钟卡在 flyway 镜像拉取（应用本身 40.4s 起来）
- 切换前后业务读数一字不差：`29 表 / family 9 / account 166 / max(family.id)=40 / 最新家族「罗氏家族」`；`is_widowed tinyint(1) default 0` 在位
- `ft-sentinel.sh` 四行全 OK、`rc=0`（mysql/redis TCP 通、health 200、假凭据登录 2s 返回）；`http://8.153.176.50/` 200、`/login.html` 200、实例内 443 自测 200
- 日志文件里 15:55 之后 ERROR = 0；redis `dbsize=1`（只有哨兵留下的 `login:attempt:__sentinel__`，说明事故期间 + TTL 30m 后旧会话本就已过期，不是这次重启弄丢的）
- 应用日志时间戳是 **UTC**（`07:59:56` = CST 15:59:56），比对停机窗口时别按本地时间读
- profile 仍是 `mysql`，所以 logback 只写文件、`docker logs` 是空的（`ecs` profile 才挂 CONSOLE）

> 脏工作区构建出来的镜像**不可从任何 commit 复现**。要么发版前先 commit，要么在第 4 步把 `git status --porcelain` 一并存档；本表只记事实，不代表认可长期跳版本发布。

## 10. 实例上这些东西的来历

| 路径 | 作用 |
|---|---|
| `/opt/family-tree/ft-sentinel.sh` | 第 3/6/7 步的验收脚本 |
| `/usr/local/sbin/ft-firewall-guard.sh` | 网桥 → trusted 的幂等重绑 |
| `/etc/systemd/system/ft-firewall-guard.{service,timer}` | 开机 60s + 每 5min 兜底（面板重启 firewalld 会自己长回来） |
| `/etc/firewalld/zones/trusted.xml` | `--permanent` 落盘的那个网桥名（仅作二次保险） |
| `/opt/family-tree/RELEASE-SOP.md` | 本文件的实例侧副本（systemd unit 的 `Docs:` 指这儿）；仓库改了要重传 |

脚本的**源头在仓库** [`ops/`](ops)（改完重传实例，别只在 `/tmp` 里留一份）：

| 仓库文件 | 实例落位 | 用途 |
|---|---|---|
| `ops/ft-preflight.sh` | `/tmp/`（一次性） | 第 1 步只读预检 |
| `ops/ft-release-check.sh` | `/tmp/` | 发版过程中的状态复查（容器/镜像、Flyway 读数、业务基线、`is_widowed`、80/443 健康、日志尾部） |
| `ops/ft-firewall-guard.sh` | `/usr/local/sbin/` | 网桥自愈 |
| `ops/ft-firewall-guard.{service,timer}` | `/etc/systemd/system/` | guard 的开机/定时触发 |
| `ops/ft-sentinel.sh` | `/opt/family-tree/` | 验收哨兵 |
| `ops/ft-v7-checksum-fix.sh` | `/tmp/` | 第 5 节的 SQL 兜底（带旧值守卫，只对 V7 这一行） |
