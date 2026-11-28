# 阿里云 ECS 部署（deploy/ecs）

单机 docker compose 栈。内存配额是照 **2 vCPU / 1870MiB**（`ecs.e-c1m1.large`）调出来的；实例 2026-11-22 变配到
**`ecs.e-c1m2.large`（2 vCPU / 3563MiB）**后这套值没有改动，属于偏保守的下限，要放宽就改 `.env` 的三个 `*_MEM_LIMIT` 重跑 `data`/`app`。
与仓库根的 `docker-compose.yml`（面向 8GB 宿主 + ghcr 镜像）和 `k8s/`（Docker Desktop 蓝绿）**三套并存，互不覆盖**。

## 文件

| 文件 | 作用 |
|---|---|
| `docker-compose.yml` | mysql + redis + app 三容器，端口只绑 127.0.0.1 |
| `.env.example` | 变量与内存依据；复制成 `.env`（`.gitignore` 已忽略，勿提交） |
| `ecs-setup.sh` | 分阶段部署：`env → images → data → import → app → nginx → backup`（老环境先加跑 `tobind`；改过既有迁移文件的版本先跑 `repair`） |
| `nginx-family-tree.conf` | 宿主 nginx 反代（80 = IP 明文 HTTP，443 = 域名 HTTPS 已装待备案，现状见下） |
| `backup.sh` | 容器内 mysqldump → gzip，保留 7 天 |
| `RELEASE-SOP.md` | 发版/升级 SOP（预检 → 备份 → 容器网络自检 → 构建导入 → repair 判定 → 切换 → 验收 → 回滚），已有环境换镜像照它跑 |
| `ops/` | 配套探针与自愈脚本（预检、状态复查、验收哨兵、网桥 trusted 自愈 + systemd timer、**开机自愈 `ft-boot-recover`**、V7 checksum 兜底），实例落位见 SOP 第 10 节、重启处置见 SOP 第 12 节、磁盘与业务数据落位见 SOP 第 13 节、docker/k3s 数据根迁盘与重启演练见 SOP 第 14 节 |

应用侧还有一份配套配置：`family-tree-web/src/main/resources/application-ecs.yml`（profile `ecs`，随镜像打包），
compose 以 `SPRING_PROFILES_ACTIVE=mysql,ecs` 加载它。

## 前置条件

- ECS 实例已装 Docker + Docker Compose v2（`ecs-setup.sh` 不装这些，只装 nginx）
- 安全组放行 **80 与 443**（443 于 2026-11-22 放行）；22 端口不需要对外开放
- 跑 `nginx` 阶段前，`/etc/nginx/ssl/msjzp.xyz.pem` 与 `.key` 必须已就位（`mkdir -m 700 -p` 建目录，
  权限 644/600）：仓库版配置里有引用它们的 443 块，缺文件 `nginx -t` 直接失败、reload 不会执行
- 一台能构建镜像的本机 + 阿里云 Workbench CLI（`workbench exec/upload`）
  - Workbench 走控制台私有隧道：**目标实例不需要公网 IP、不需要 SSH 密钥**
  - AccessKey Secret 只能由使用者在自己的终端交互式配置，Agent 不经手

## 部署步骤

```bash
# 1) 本机构建并打包镜像（tag 与代码版本一一对应，别用 latest）
cd ../..
docker build -t family-tree:vNNN-local .
mkdir -p /tmp/ft-ecs && cd /tmp/ft-ecs
docker save family-tree:vNNN-local | gzip > ft-vNNN.tar.gz
shasum -a 256 ft-vNNN.tar.gz > ft-vNNN.tar.gz.sha256   # 校验文件与 tar 同名 + .sha256，需一起上传

# 2) 准备数据 dump（从旧环境 mysqldump 出来的 *.sql，放在 /root/ft-migrate/）

# 3) 上传：镜像包 + 4 个文件（.env 不用上传，下一步会生成）
#    这一版若改过已应用的迁移文件，还要多传一个 mysql 迁移目录，见「改过既有迁移文件时怎么发版」
workbench upload /tmp/ft-ecs/ft-vNNN.tar.gz /root/ft-migrate/ --instance-id <i-xxx> --region cn-shanghai
workbench upload /tmp/ft-ecs/ft-vNNN.tar.gz.sha256 /root/ft-migrate/ --instance-id <i-xxx>
workbench upload deploy/ecs/docker-compose.yml /opt/family-tree/ --instance-id <i-xxx>
#    …… ecs-setup.sh / nginx-family-tree.conf / backup.sh 同理

# 4) 实例上按阶段执行（每阶段可重复跑，失败不影响上一步产物）
cd /opt/family-tree && chmod 755 *.sh
./ecs-setup.sh env        # 生成随机口令的 .env（0600，不打印到日志）
./ecs-setup.sh images     # 验 sha256 → docker load → 拉 mysql/redis
./ecs-setup.sh data       # 建 data/ 目录（chown 999），起 mysql/redis，等 healthy
./ecs-setup.sh import     # 导 dump；目标库非空则中止，绝不覆盖
./ecs-setup.sh app        # 起 app，等 /actuator/health 返回 200
./ecs-setup.sh nginx      # 装/配 nginx，摘掉主配置 default_server
./ecs-setup.sh backup     # 安装每日 03:00 备份 cron（装完手工跑一次确认能出文件）
./ecs-setup.sh repair     # 仅当这一版改动了「已经应用过的」迁移文件：重写 flyway_schema_history 的 checksum
./ecs-setup.sh status     # 容器、内存、监听、健康、宿主数据目录、最近备份
```

> 已有一版跑在命名卷上：把新版 compose + 脚本传上去后先 `./ecs-setup.sh tobind` 搬数据，再 `data` → `app`（详见「数据与备份」）。

### 改过既有迁移文件时怎么发版

Flyway 按内容给每个迁移文件算 checksum，存量库的 `flyway_schema_history` 记着旧值；只要动过**已经应用过的**文件，新镜像一启动就 `Migration checksum mismatch` 校验失败。`repair` 阶段借 `flyway/flyway:11.14-alpine`（版本必须与 Boot 4.0.8 管理的 Flyway 11.14.1 一致，否则算出的 checksum 对不上）连 mysql 容器改写历史表，**不碰任何业务数据**。

```bash
# 1) 本机：镜像包 + 与新镜像同一 commit 的 mysql 迁移目录一起传上去
docker save family-tree:vNNN-local | gzip > ft-vNNN.tar.gz
workbench upload /tmp/ft-ecs/ft-vNNN.tar.gz /root/ft-migrate/ --instance-id <i-xxx>   # upload/download 不认 --timeout，只有 exec 认
tar czf /tmp/ft-ecs/migrations-mysql.tgz -C family-tree-web/src/main/resources/db/migration mysqlworkbench upload /tmp/ft-ecs/migrations-mysql.tgz /opt/family-tree/ --instance-id <i-xxx>

# 2) 实例上：解包 → 停应用 → repair → 换 FT_IMAGE → 起新应用
cd /opt/family-tree && tar xzf migrations-mysql.tgz    # 解出 /opt/family-tree/migrations/mysql
docker compose -f docker-compose.yml --env-file .env stop family-tree
./ecs-setup.sh repair       # 只改 flyway_schema_history 的 checksum/description，不碰业务数据
sed -i 's/^FT_IMAGE=.*/FT_IMAGE=family-tree:vNNN-local/' .env
./ecs-setup.sh app
```

**顺序是停应用 → repair → 起新镜像**：`repair` 把历史表的 checksum 改写成与新文件一致，做完这一步旧镜像反倒对不上号（它带的是旧文件），新镜像则需要新历史表才能过校验；夹在中间的窗口期应用是停的，`repair` 本身跑不到 1 分钟。

> `repair` 阶段跑完会把改写后的 V7 checksum 与 `ecs-setup.sh` 里 `EXPECT_V7_CHECKSUM` 的期望值比对：传错版本的迁移目录会直接停在那里，不会把错的 checksum 留在库里。以后若再改动 V7，同步更新那个常量，或 `EXPECT_V7_CHECKSUM= ./ecs-setup.sh repair` 跳过校验。

> 实例上 `registry-1.docker.io` 直连超时，`repair` 阶段首次会去拉 `flyway/flyway:11.14-alpine`，靠 `/etc/docker/daemon.json` 里配的 mirror 可以拉到（本机已实测）；万一拉不动，本机 `docker pull` + `docker save` 成 tar 传上去 `docker load` 即可。

升级发版：改 `.env` 的 `FT_IMAGE` → 重跑 `./ecs-setup.sh app`；若这一版改过既有迁移文件，按上节先 `repair`。
完整的一轮发版动作（预检读数、备份校验、容器网络自检、验收电池、回滚口径）在 [`RELEASE-SOP.md`](RELEASE-SOP.md)。

## 数据与备份

- 数据全在宿主目录（bind 挂载，无命名卷）：`/opt/family-tree/data/mysql`、`data/redis`、`data/photos`、`logs`
  - **2026-11-28 起这些宿主目录本身是数据盘（`/dev/vdb1` → `/data/family-tree/…`）的 bind**，`/opt/family-tree` 下只剩代码与配置；实例侧口径见 [`RELEASE-SOP.md`](RELEASE-SOP.md) 第 13 节
  - **同日再把 `/var/lib/docker` 与 `/var/lib/rancher/k3s` 也 bind 到 `/data/{docker,k3s}`**（SOP 第 14 节），系统盘只剩 OS 与 `/usr`；这两行 fstab **不带 `nofail`**，配合三个 unit 的 `RequiresMountsFor=/data`，挂不上就挡服务而不是让 mysqld/k3s 对着空目录初始化
  - 好处：`docker compose down -v` 毁不掉生产数据；备份/排查/换机直接对着 `./data` 操作
  - 目录属主必须是 uid 999（mysql/redis 官方镜像都以 uid 999 运行），`data` 阶段会建目录并 `chown 999:999` + `chmod 750`
  - 各自挂子目录而不是整个 `./data`：照片/日志与 InnoDB 文件不共用一套属主权限，一次误 `chmod -R` 不会同时带走
- **老环境（命名卷版）升上来先跑一次 `./ecs-setup.sh tobind`**：停容器 → `cp -a` 卷内容到 `data/mysql`、`data/redis` → 校验 `auto.cnf` / `appendonlydir` 标记 → 再 `data`/`app`。目标目录非空即中止，不覆盖；确认数据在之后按提示手工 `docker volume rm` 旧卷腾出约 211MB
- `backup.sh` 的 cron：`0 3 * * *`，产物 `/opt/family-tree/backups/family_tree_*.sql.gz`
- **备份、数据库、照片同盘（数据迁移后是 `/dev/vdb1`，不再是系统盘）**，只靠它不算异地副本：另配 ECS 控制台自动快照，或把 `backups/` 与 `data/photos` 同步到 OSS。**这两项 2026-11-28 由用户明确决定暂不做，按知情接受的现状保留**（见 [`RELEASE-SOP.md`](RELEASE-SOP.md) §14.7）
- 同盘另有一份**镜像/配置回滚锚**：`/opt/family-tree/backups/rollback-20261128/`（在任镜像 tar + jar + 配置快照 + `RESTORE.md`，448M，`SHA256SUMS` 可校验）。它只防误删误改，**不含业务数据、也不防整盘故障**，口径见 SOP §14.4

## 域名 HTTPS：配置已装好，等备案接入生效

`deploy/nginx/family-tree.conf` 是「域名 80 全量 301 到 443」的模板（HSTS、OCSP、acme-challenge），当前**仍不能启用**——它会把现在能用的 IP 访问一起弄挂。本目录的 `nginx-family-tree.conf` 走的是另一条路：80 端口 `default_server` 服务 IP 明文 HTTP（家人在用），443 端口单独一个域名 server 块备用。

2026-11-22 已把 443 块 + 证书装上实例并 reload，实例内自测通过：

- `curl -sk --resolve msjzp.xyz:443:127.0.0.1 https://msjzp.xyz/` → **200**
- `openssl s_client -connect 127.0.0.1:443 -servername msjzp.xyz` → `Verify return code: 0 (ok)`，下发 leaf + DigiCert 中间证书两段，TLSv1.3
- 回归：`http://127.0.0.1/`（Host=IP）→ 200，`/actuator/health` 公网 → 403（内网白名单，符合设计）

但**从公网还验证不了**，且原因和之前想的不同，实测结论是：

- 任何**带 SNI** 的 TLS ClientHello 到 `8.153.176.50:443` 都被中途掐断（`SSL_ERROR_SYSCALL`）——与域名无关，拿 `probe.example.org` 做对照同样失败；不带 SNI 直连 IP 的 TLS 却能正常握手拿到 200，说明包确实到了 nginx。`nc -z` 能连通（TCP 层通，RST 发生在 L7）。
- 域名的**明文 HTTP** 走网关，返回 `403 Non-compliance ICP Filing`（`Server: Beaver`）。

所以 nginx + 证书这侧是对的，只是备案接入生效前无法端到端验证；生效后直接用 `https://msjzp.xyz/` 验即可，不用再排 HTTPS 的工时。

证书：阿里云数字证书管理服务的免费 DV（DigiCert 签发），SAN 含 `msjzp.xyz` 与 `www.msjzp.xyz`，有效期至 **2027-02-19**（89 天）。它是靠 DNS 验证（`_dnsauth` TXT 记录）签出来的，不碰 80/443，因此未备案也能拿到——先前「拦截挡死 HTTP-01，所以备案前签不出证书」的判断只对 Let's Encrypt 成立，DV 邮件/DNS 验证不受影响。不想手动续期就改用实例上已有的 `acme.sh --dns dns_ali`（DNS-01）自动续签。

备案通过后补三件事：80 → 443 的 301、HSTS、`SPRING_PROFILES_ACTIVE` 改回 `mysql,prod`（见下一节）。

## 应用配置：profile `ecs`

compose 用 `SPRING_PROFILES_ACTIVE=mysql,ecs`：`mysql` 给数据源与 Flyway，[`application-ecs.yml`](../../family-tree-web/src/main/resources/application-ecs.yml) 给「nginx 反代 + 明文 HTTP」形态和小机型配额。

### 数据库形态（mysql / h2 按 profile 切换）

`application-ecs.yml` 不含任何 MySQL 专属项，所以 `mysql` 这个位置可以换成 `h2`：

| `.env` 里的值 | 数据源定义 | Flyway 脚本 | 需要起的容器 |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE=mysql,ecs`（默认） | [`application-mysql.yml`](../../family-tree-web/src/main/resources/application-mysql.yml) | `db/migration/mysql` | mysql + redis + app |
| `SPRING_PROFILES_ACTIVE=h2,ecs` | [`application-h2.yml`](../../family-tree-web/src/main/resources/application-h2.yml) | `db/migration/h2` | redis + app |

两侧迁移脚本各 40 个文件，表结构对齐。别把这里的 `h2` 和仓库里那份 `application-dev.yml` 混用：`dev` 是本地 `mvnw spring-boot:run` 的开发形态（库文件写死 `./data/family-tree`、控制台默认开、不带 `ecs` 的反代/Cookie 形态），ECS 上的备用形态是 `h2,ecs`（库文件走 `H2_DATA_PATH`，控制台默认关）。

```bash
# 切到 H2（省掉 mysql 的 512m 配额 / 实测 149Mi）
cd /opt/family-tree
printf 'SPRING_PROFILES_ACTIVE=h2,ecs\n' >> .env
docker compose -f docker-compose.yml --env-file .env stop mysql
docker compose -f docker-compose.yml --env-file .env up -d --no-deps family-tree
```

三个必须知道的限制：

1. **redis 省不掉**：`family-tree-web` 依赖 `spring-boot-starter-session-data-redis`，会话外置；`LoginAttemptService` 又直接用 `StringRedisTemplate` 数登录失败次数。实测（本机一次性容器，2026-11-22）：没有 redis 时应用**照样启动、health 照样 200**（Lettuce 是懒连接），但登录请求会先卡住约 **60s**（连接超时），然后返回 `{"code":500,"message":"系统内部错误"}` —— HTTP 状态码仍是 200，因为异常被 `Result` 包装兜住了。所以症状不是「报错」而是「转圈一分钟然后说系统内部错误」，别和「选错数据库」混为一谈。
2. **切过去是空库**：H2 与 MySQL 之间没有数据搬运路径，`h2` 那 40 条迁移会从零建出一套 schema。现有 `family_tree` 库的数据不会跟过去；`backup.sh` 与 `ecs-setup.sh` 的 `data/import/repair` 阶段也全部按 MySQL 写死，H2 形态下这些阶段不要用。
3. **`ecs-setup.sh` 不认识这个开关**：脚本各阶段仍按 mysql 三容器编排（`status` 会报 mysql 不在）。它就是「内存更小的机器上手起一套自用库」的备用形态，不是第二条受支持的发版路径。

为什么不能直接用 `prod`（同一镜像、同一账号、本机实测）：

| profile | 登录响应头 |
|---|---|
| `mysql,prod` | `Set-Cookie: SESSION=...; Path=/; Secure; HttpOnly; SameSite=Strict` |
| `mysql,ecs` | `Set-Cookie: SESSION=...; Path=/; HttpOnly; SameSite=Lax` |

`Secure` 在明文 HTTP 下浏览器根本不发这个 Cookie，症状是「登录返回成功，下一个请求就未登录」；`SameSite=Strict` 则会把从微信/短信链接点进来的跨站首跳也挡成未登录。状态变更已有 `CsrfFilter` 的 Synchronizer Token 兜底，所以降到 `Lax` 不额外放开 CSRF 面。

`ecs` profile 还顺手把 `com.mouhin.family.tree` 的日志级别从 base 配置的 `debug` 收成 `info`，并在 logback 的 `ecs` 块里加了 CONSOLE —— 否则容器只写文件，`docker logs` 除了启动横幅什么都没有。日志文件仍写 `/app/logs`（宿主 `/opt/family-tree/logs`）。

上 HTTPS 之后（备案生效）：`SPRING_PROFILES_ACTIVE` 改回 `mysql,prod`，反代仍用本目录这份 —— 它已经带域名 443 块，只需补 301 与 HSTS，不用换回 `deploy/nginx/family-tree.conf`（那份模板的 80 全量 301 会把 IP 访问一起弄挂）。

## 已知限制

- **compose 与运行态存储形态不一致时别直接 `up -d`**：本目录是宿主 bind 挂载，若实例上还跑着早期的命名卷版，只重传 compose 就 `docker compose up -d` 会让 mysql 挂上一个空的 `./data/mysql`（看着像「库没了」，其实旧卷完好 —— 先 `docker compose down`，跑 `tobind`，再起即可）。**V7 修好之后这种误操作不再报错**：Flyway 会把空目录当全新库，安静建出一套空 schema 并正常启动，`app` 阶段因此会打一行「family_tree 库 0 张表」的警告 —— 看到它别继续对外服务。
- **改过已经应用过的迁移文件**：checksum 变了会让应用启动即失败（Flyway `validate-on-migrate`），发版要先跑一次 `./ecs-setup.sh repair`，步骤见上节。
- **迁移中途杀进程**会在 `flyway_schema_history` 留下 `success=0` 行，下次启动报 `Detected failed migration to version N`，`repair` 阶段同样能清掉；升级窗口内别 `docker rm -f` 正在启动的 app 容器。
- 明文 HTTP：会话与口令在内网/公网上都是明文，只适合当前「小范围家人自用 + 待备案」的过渡状态。

## 踩过的坑

- `workbench exec` 默认 **`--timeout 30`**：`docker load`/`docker pull`/`sha256sum` 大文件会报超时但**服务端仍在执行且已成功**。长命令显式加 `--timeout 600`，或输出重定向到文件后再 `cat`，并用一条独立短命令复核真实状态。
- `workbench exec` 的每次调用是独立 shell，`cd`/`export` 不跨调用。
- **`workbench exec -c` 传多行脚本会静默失败**：远端 `/bin/sh` 收到整段脚本文本，却只执行第一个命令（甚至完全不执行），而 CLI 侧照样打印成功 —— 这个会话里「备份 + 覆盖 conf + reload」就是这么假装做完的，实际 `/etc/nginx/conf.d/family-tree.conf` 一字未改、`.bak` 根本不存在。**只用单行 `&&` 链**（一条消息里放多条 exec 也安全），并且每条写完立刻用**独立的一条 exec 查询真实状态**（`sha256sum`、`ls -l`、`grep -c`），别信回显。
- `workbench upload` 撞到远端同名文件会进交互式 `[y/N]`，非交互场景直接 `Error: upload canceled by user`（`-f/--force` 能覆盖，但更稳的是先传 `/tmp` 再服务端 `cp -a` 落位，这样覆盖动作本身也在备份之后、还能顺手 `nginx -t`）。
- 应用镜像是 **distroless**，容器里没有 `sh`：进不去 `kubectl/docker exec`，存活检查在宿主 curl；compose 里也没法用 curl 做 healthcheck。
- `conf.d/*.conf` 在 `http{}` 末尾才 include，顺序晚于主配置里的 stock server 块；不摘掉主配置的 `default_server`，请求会命中 nginx 默认页（`ecs-setup.sh nginx` 已自动处理并备份原文件）。
- **在 macOS 上试 bind 挂载会假失败**：Docker Desktop 的文件共享不区分大小写，mysqld 推出 `lower_case_table_names=2` 与数据字典的 `0` 不符，直接 `Data Dictionary initialization failed` 拒绝启动 —— 跟搬运无关（ECS 的 `/dev/vda3` 是 ext4，区分大小写）。本机验证 `tobind` 要把数据放到 VM 内的非共享路径（如 `/var/...`）再起。
- `FREE` 内存看着够不等于安全：根 compose 的 1536+1024+128 在原来那台 1870MiB 上必然 OOM；现在这台 3563MiB 装得下，但仍按 `.env.example` 的保守值跑（配额依据与实测占用都在那儿），要放宽就显式改 `*_MEM_LIMIT` 再重跑 `data`/`app`。
