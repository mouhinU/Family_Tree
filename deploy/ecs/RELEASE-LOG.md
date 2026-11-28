# Family Tree ECS 发版记录

本文件是 Family Tree ECS 生产环境的独立发版台账，按发布时间倒序登记。发版操作流程见 [RELEASE-SOP.md](RELEASE-SOP.md)。

## 登记要求

从 2026-11-27 起，每次发版必须登记以下信息：

- **发布时间**：实际切流完成的 CST 与 UTC 时间，精确到秒，不能只写日期。
- **发布内容**：用户可读的功能、修复或配置变更摘要。
- **版本与代码**：发布版本、commit、镜像及 jar 的 SHA256。
- **部署过程**：发版前数据库备份、部署槽位、切流时刻、回滚锚。
- **验收结果**：测试数量与结果、旁路验收、哨兵、公网检查、会话连续性及日志检查。
- **遗留事项**：未完成验收、已知问题或切流期预期异常；没有则填写“无”。

不得在台账中记录密码、AccessKey、会话 Cookie、完整个人信息或导出数据。

---

## 运维变更 — docker/k3s 数据根迁数据盘（scope ③）+ 真实重启演练 + 旧副本回收

| 项目 | 内容 |
|---|---|
| 变更时间 | 2026-11-28 20:36–20:54 CST / 12:36–12:54 UTC（停机窗 20:37:43–20:43，约 5.5min） |
| 变更内容 | `/var/lib/docker`（4.6G）与 `/var/lib/rancher/k3s`（4.0G）经 bind 落到 `/dev/vdb1`；整实例重启演练验证开机自愈；回收系统盘旧副本 |
| 代码版本 | 无功能代码变更（镜像与 jar 仍是 v95 的 `f0ccb6a` 基线） |
| 发布镜像 | 不变：`family-tree:v95-local`（蓝绿两侧同镜像） |
| 数据库备份 | 停机前全量 dump 落数据盘：`/opt/family-tree/backups/family_tree_20261128_203626.sql.gz`（74,879B，sha256 `8b6fd98f67f6…`，32 表，GZIP_OK） |
| 数据库变更 | 无 Flyway 版本、无表结构变更；仅镜像/容器存储的物理位置迁移（`cp -a` 98s） |
| 部署槽位 | 未切流：nginx 仍指向绿槽 8092（沿用上一条的在任态） |
| 在任版本 | `family-tree-app-green` / `family-tree:v95-local` / replicas 1 |
| 回滚锚 | `family-tree-app-blue` / `family-tree:v95-local` / replicas 0；⚠️ scope ③ 的 `.pre-datadisk` 原数据根**已在 20:54 删除**，回退只能反向 `cp -a`（需再次停服） |
| 系统配置 | `/etc/fstab` 追加两行 bind（**不带 `nofail`**，挂不上即挡服务）+ `/etc/systemd/system/{docker,containerd,k3s}.service.d/datadisk.conf` = `RequiresMountsFor=/data`；fstab 备份 `/etc/fstab.bak.scope3-<ts>` |

### 变更内容明细

- 落位仍用 bind，不改 `daemon.json`、不改 k3s `--data-dir`，仓库侧每一处路径字符串保持原样；SELinux `Disabled`，无 label 处理
- 一次性脚本三份（本地 `/tmp/scope3-{copy,apply,start}.sh`，sha256 `b09ea29e…` / `9f06d215…` / `186121e2…`）都带前置守卫：目标目录须为空、`.pre-datadisk` 不得已存在、服务须已停、`COPY_ALL_DONE` 标记须在；启动脚本还先断言两条 bind 已在 `/proc/mounts`，否则拒绝拉起
- 回收：`.old-20261128_202100` 四目录（删前 `comm` 比对 only-in-old=0）、两个不再引用的 named volume（`.Containers` 为空）、`.pre-datadisk` 两份原数据根、`Created` 状态残留容器 `serene_easley`

### 验收结果

- 三个坑及处置已固化进 SOP §14.3：k3s 停止后 `containerd-shim-runc-v2` 孤儿仍占 8092；libnetwork 报 `network <id> does not exist` 且重启 daemon 无效；compose 复用旧 sandbox 导致 `3308/6381` 无监听，须 `--force-recreate`
- `df`：`/dev/vda3` 24G/64% → **14G/38%**；`/dev/vdb1` 9.7G/21%
- `findmnt -T`：`/var/lib/docker`、`/var/lib/rancher/k3s`、`/opt/family-tree/data`、`/opt/family-tree/logs` 的 SOURCE 均为 `/dev/vdb1[/…]`（dev=64785）；`docker info` 镜像 13 个 family-tree tag 全在
- `ft-sentinel.sh`：`mode=k3s base=http://127.0.0.1:8092` + 四行 OK，`rc=0`（清理后复跑仍 rc=0）
- 读数对照：**迁移后与重启后两次均为** 32 表 / 19 家族 / 205 账号 / 246 节点 / 211 成员 / 29 跨族链、`success=1` 计 41，与基线逐字一致；照片 2 文件 / 1.2M 与迁移前副本计数一致
- 真实重启演练：`reboot` 20:49:38 → 实例 20:49:52 回来 → `ft-boot-recover` 20:50:32 起跑，**20:50:38 mysql healthy + redis PONG、20:52:21 应用槽 8092 health=200、rc=0**，全程无人工；§12 的 `ZONE_CONFLICT`/网络 ID 错配未复现
- 会话连续性：**未保持**——重启后 Redis `dbsize` 3 → 2，AOF 未捞回旧 key，在线用户需重新登录（既定代价）
- 日志：当前绿槽 pod 重启后窗口内 `" ERROR "` 计数 0
- 公网：`/`、`/home`、`/login.html` 均 200，前端指纹仍 `style.css?v=20261128d`（v95 未变）

### 遗留事项

- 备份、数据库、照片同处 `vdb1` 一块盘，**无异地副本**；建议补云快照或对象存储副本
- scope ③ 回滚副本已删，挪回系统盘需再次停机（SOP §14.4）
- 数据根手术只在整机停机窗口做过一次，实例上无 `rsync`，不要拿在线 `cp -a` 赌一致性

---

## 运维变更 — 日志/数据/备份落位到数据盘（含一次同版本蓝绿切流）

| 项目 | 内容 |
|---|---|
| 变更时间 | 2026-11-28 20:21–20:29 CST / 12:21–12:29 UTC |
| 变更内容 | 新增数据盘 bind 挂载（logs、data、backup、backups 四目录迁到 `/dev/vdb1`）；compose 的 mysql/redis 从 named volume 切到 `./data/mysql`、`./data/redis`；为让 pod 的 hostPath 真正指向数据盘，把流量从蓝槽 8091 切到绿槽 8092（同镜像 `v95-local`，非版本发布）；`ft-boot-recover` 增加数据盘守卫与槽位自适应 |
| 代码版本 | 无功能代码变更（镜像与 jar 仍是 v95 的 `f0ccb6a` 基线） |
| 发布镜像 | 不变：`family-tree:v95-local`（蓝绿两侧同镜像） |
| 数据库备份 | 切数据面前在数据盘另出一份 mysqldump（`/data/family-tree/backup/`），与当日 03:00 cron 产物并存 |
| 数据库变更 | 无 Flyway 版本、无表结构变更；仅数据目录物理位置迁移（InnoDB 191M + AOF 7.0M，复制约 1s） |
| 部署槽位 | nginx 8091 → 8092（6 处全量）；配置备份 `/etc/nginx/conf.d/family-tree.conf.bak.pre-datadisk-<ts>` |
| 在任版本 | `family-tree-app-green` / `family-tree:v95-local` / replicas 1 |
| 回滚锚 | `family-tree-app-blue` / `family-tree:v95-local` / replicas 0；清单备份 `deployment-green.yaml.bak.pre-datadisk-<ts>`；旧 named volume `family-tree_mysql-data`、`family-tree_redis-data` 当时保留未删（20:54 回收，见上一条） |
| 系统配置 | `/etc/fstab` 追加四条 bind（备份 `/etc/fstab.bak.pre-datadisk-<ts>`）；compose 备份 `docker-compose.yml.bak.pre-datadisk-<ts>` |

### 变更内容明细

- `/data/family-tree/{logs,data,backup,backups}` bind 到 `/opt/family-tree/` 同名目录，选项 `bind,nofail`；`/data` 本身不带 `nofail`，数据盘挂不上则挡开机而不是让 mysqld 对着空目录初始化
- 数据面迁移期间 compose 拒绝接管无标签的 `family-tree_default`，处置为「摘容器 → `docker network rm` → 由 compose 重建 → 把退役的 `family-tree-app` 重新接回」
- `ops/ft-boot-recover.sh`（sha256 `4a35c8d1744666a3…`）：`ensure_datadisk_mounts` + `guard_mysql_datadir`（`ibdata1` 缺失即中止）+ 应用槽改为探测 8091/8092 并只对 `replicas>0` 的 deployment `rollout restart`

### 验收结果

- `findmnt`：四个 `/opt/family-tree/{logs,data,backup,backups}` 的 SOURCE 均为 `/dev/vdb1[/family-tree/…]`
- `df`：`/dev/vda3` 40G/24G/64%（未回收 `.old` 副本与旧卷，仍待授权清理）；`/dev/vdb1` 49G/1.5G/4%
- `ft-sentinel.sh`：`mode=k3s base=http://127.0.0.1:8092` + 四行 OK，`rc=0`
- 生产检查：公网 `/`、`/home`、`/login.html` 均 200，前端指纹仍 `style.css?v=20261128d`（v95 未变）
- 读数对照：32 表 / 19 家族 / 205 账号 / 246 节点 / 211 成员 / 29 跨族链，迁移前后一致；`flyway_schema_history` 中 `success=1` 计 41，未重跑
- 会话连续性：**未保持**——数据面重启使 Redis `dbsize=0`，在线用户需重新登录（本轮唯一用户可见影响）
- 流量切换证据：切流后绿槽 pod `1/1 Running`、`8092/actuator/health=200`，蓝槽缩容后 `8091` 不再出现在哨兵链路
- 自愈演练：`systemctl restart ft-boot-recover.service` 空跑 `ExecMainStatus=0`，日志末行 `应用槽 8092 health=200`，零副作用

### 遗留事项

- ~~`.old-20261128_202100` 四份原目录（约 1.25G）与两个不再引用的 named volume（约 200M）仍在系统盘上，回收需点名授权~~ —— 20:54 已回收（见上一条）
- ~~`/var/lib/docker`（4.6G）与 k3s image store（4.0G）的 data-root 迁盘属下一窗口~~ —— 同日 20:36–20:43 已完成（见上一条）；备份/数据库/照片同盘无异地副本这一条**仍然成立**
- ~~真实重启路径仍未演练（仅幂等空跑），见 SOP §12.5~~ —— 20:49 已完成整实例重启演练并通过（SOP §14.5）

---

## v95 — 登录态 Home 与「首页」导航统一

| 项目 | 内容 |
|---|---|
| 发布时间 | 2026-11-28 18:08:37 CST / 2026-11-28 10:08:37 UTC |
| 发布内容 | 已登录用户可直接访问公开 Home 且不会掉出登录态；新增不轮换 CSRF 令牌的登录状态接口；「产品首页」导航文案统一为「首页」，并在应用「更多」菜单、抽屉导航、过渡页与平台管理页补齐首页入口 |
| 代码版本 | 发布时为未提交工作区（HEAD `853795d`）；**2026-11-28 发布后经用户口令「push」补提交：功能 commit `f0ccb6a`、登记 commit `e07b1d6`，均已 push `origin/main`**。v95 含 v91/v92/v93/v94 未提交功能基线，自此并入 `f0ccb6a` |
| 发布镜像 | `family-tree:v95-local`；镜像 ID `sha256:be3ba6fa83e1cda736aefc35ea4a8a96e3e0e4576437f0f980979deeabdde046`；基座 `family-tree:app-base`，层数 2 |
| jar 校验 | SHA256 `56ec457a39ef1cd12e64eb25ab3c312de7cd3629e9148abfbf4361e8761d9564`；138,012,694 字节；本地 target、实例 `/root/ft-build/app.jar`、镜像内 `/app/app.jar` 三处一致 |
| containerd 清单 | `sha256:d6eb71ba8037f82fbc2ea6062586732c32c112e3326df3226fedbad31f939449` |
| 数据库备份 | `/opt/family-tree/backups/family_tree_20261128_180031.sql.gz`；74,848 字节；SHA256 `1bcc54c13de6e4c8df13a50fb0e4548f595ded35a47264eeb40e4ddc35330589`；`gzip -t` 通过、32 张表、中文正常 |
| 数据库变更 | 无新增迁移；jar 内 MySQL 迁移顶版 V41 == 历史表顶 `41`、`success=1`，故无需 `repair` |
| 部署槽位 | 蓝槽 `127.0.0.1:8091`；nginx 六处上游由 8092 切换为 8091（旧引用剩余 0、新引用 6） |
| 在任版本 | `family-tree-app-blue`：`v95-local`，replicas=1；Pod Ready 1/1、0 重启；apply→rollout 86s |
| 回滚锚 | 绿槽 `family-tree-app-green`：`v94-local`，replicas=0；docker 与 containerd 均保留 |
| nginx 备份 | `/etc/nginx/conf.d/family-tree.conf.bak.pre-v95-20261128_180837` |

### 发布内容明细

- `HomeController.publicHome()` 新增 `GET /home`：匿名与已登录都可进入公开 Home，内部转发到 `/home.html`，响应带 `Cache-Control: no-store`；已登录用户访问 Home 不读也不写会话属性，因此不会踢出登录。
- `AuthController.authStatus()` 新增 `GET /api/auth/status`：只回 `loggedIn` 布尔值，`request.getSession(false)` 不新建会话、也不轮换 CSRF 令牌；`/api/auth/config` 与 `/api/auth/status` 一并加入 `WebMvcConfig.PUBLIC_API_PATHS`，登录拦截与家族状态拦截共用同一份公开清单。
- Home 前端按 `/api/auth/status` 切换 `[data-auth-state]` 与 `[data-registration-entry]`：已登录时隐藏注册/邀请入口并给出进入工作台的入口，拉取失败维持现状。
- 导航文案统一：`index.html`「更多」下拉与抽屉导航、`portal.html`、`admin.html` 的 `产品首页 / 了解产品首页` 全部改为「首页」，仓库内静态资产已无 `产品首页` 残留。
- 五页（index/admin/portal/login/home）`style.css` bump 到 `?v=20261128d`，并补齐 `.header-more-item`、`.drawer-nav-item` 的块级/弹性布局与 `text-decoration: none`。

### 验收结果

- 全量测试：`./mvnw -B clean package` 六模块 **752** 个用例（common 19 / domain 121 / infrastructure 39 / application 444 / web 129 + web 汇总 19），0 失败、0 错误、0 跳过；新增 `HomeControllerTest` 5 例与 `AuthStatusTest` 3 例全绿。
- 产物核验：jar 内 `HomeController.class` 含 `publicHome`、`AuthController.class` 含 `authStatus`（`javap` 复核，防旧 class 复用）；jar 内 `index/admin/portal/login/home` 五个页面 `产品首页` 计数均为 0、`href="/home"` 计数分别为 2/1/1/1/2、`style.css?v=20261128d` 全部到位。
- 本地浏览器验证：H2 + fakeredis 隔离环境（仅 `127.0.0.1:8097`）完成匿名与登录态两种 `GET /home`、`/` 根路由分流、会话跨 Home 往返存活、`/api/auth/status` 不轮换令牌（`/me` 取令牌→调 `/status`→同令牌 `POST /api/auth/logout` 返回 200）、1280 与 390×844 视口无控制台报错。
- 旁路验收（蓝槽 8091，切流前）：`/actuator/health` 200；`/api/auth/config` 三开关 `registerEnabled/smsLoginEnabled/registerSmsVerify` 均为 `true`；匿名 `/home` 200 且标题为「族谱 — 让家族的记忆，代代相传」、`style.css?v=20261128d`；匿名 `/api/auth/status` 回 `loggedIn:false`；`/index.html` 200 且 0 处 `产品首页`、2 处 `href="/home"`。
- 鉴权验收（蓝槽旁路）：匿名 `/api/auth/me`、`/api/node/list`、`/api/admin/graph/overview`、`/api/link-proposals` 均 HTTP 401。
- 文案下发验收（蓝槽旁路）：`admin.html`、`portal.html`、`login.html` 均 0 处 `产品首页` 且各含 1 处 `href="/home"`。
- 切流：18:08:37 CST 备份 conf → sed 六处 8092→8091（旧引用 0、新引用 6）→ `nginx -t` OK → reload 零中断。
- 入口复验（本机 nginx 与公网 IP 双侧）：`/`、`/home`、`/index.html`、`/login.html`、`/admin.html`、`/portal.html` 全 200；`/api/auth/status` 回 `loggedIn:false`；经 nginx 与公网 IP 的 `index.html` 均为 `style.css?v=20261128d`（v94 只能回 `20261128b`，据此判定新流量已落到蓝槽）；公网 `/home` 200。
- 会话连续性：切流前后 Redis 活跃会话数均为 1，切流后同一会话键仍存在（key 摘要 `5466dff3…`，TTL 由 119s 自然递减至 94s 属空闲未续期）。本次可证明会话数据跨槽存活，但该用户没有向新槽发过请求，故不作为「不掉线」的端到端实证。
- 哨兵：`mode=k3s base=http://127.0.0.1:8091`，MySQL / Redis / health / 登录冒烟四行 OK，`rc=0`。
- 数据库读数：切流后 32 表 / family 19 / account 205 / Flyway 顶版 `41` `success=1`、最新家族「周氏家族」UTF-8 正常——与发版前预检逐字一致。
- 日志：蓝槽启动窗口（UTC 10:05 之后）`" ERROR "` 0 条；切流时刻（UTC 10:08:37）之后 error 日志 `" ERROR "` 0 条；发版窗口最后一条 ERROR 仍是 09:46 UTC 绿槽的扫描器 `Bin/ScreenConnect.ClientSetup.msi` 静态资源噪音（对应门控项 #203）。
- 资源：内存 avail 2,073MB、磁盘 64%（余 14G）。

### 遗留事项

- 发布当时按用户要求未 commit；发布后用户口令「push」，本批已提交并推送（功能 `f0ccb6a`、登记 `e07b1d6`）。v95 镜像可由 `f0ccb6a` 走 §4c `clean package` + `FROM family-tree:app-base` 复现；v91/v92/v93/v94 的历史镜像仍无法由各自时点的 commit 复现（代码内容已统一并入 `f0ccb6a`）。
- 未做生产登录态浏览器复核（需真实生产账号，属生产写入，未授权）；登录态 Home 的行为以本地隔离环境 E2E + 生产匿名 HTTP 双重证据替代。
- `msjzp.xyz` 仍受既有备案接入限制，本轮验证走公网 IP 80。
- Service Worker 未改动：导航仍为 network-first、离线兜底仍为 `/index.html`。

---

## v94 — 公开 Home 页面与会话感知根路由

| 项目 | 内容 |
|---|---|
| 发布时间 | 2026-11-28 17:33:16 CST / 2026-11-28 09:33:16 UTC |
| 发布内容 | 新增无需登录即可浏览的 Home 页面；匿名 `/` 进入 Home，已登录 `/` 仍进入族谱工作台；登录页增加“返回首页”；公开页接入注册开关与邀请码入口 |
| 代码版本 | 未提交工作区；HEAD `853795d`；v94 随 v91/v92/v93 未提交功能基线构建，用户明确要求本批不提交、不推送 |
| 发布镜像 | `family-tree:v94-local`；镜像 ID `sha256:c050a3f727cda82c7e2621e27bcbf9d580bd2508d23746a4a1fe68417bcd01b9` |
| jar 校验 | SHA256 `8d0fe2140056037aca048d1a27af29a1c58744a36bfa3e84453b2a9061bb9e3f`；138,011,714 字节；本地、实例、镜像内一致 |
| containerd 清单 | `sha256:c681e9a8aaf6f013716598b33a4e40e38855c2d300902318c80f72d760577f77` |
| 数据库备份 | `/opt/family-tree/backups/family_tree_20261128_172114.sql.gz`；74,689 字节；SHA256 `8dfb56ba2f16d9d27cd2b2789b7082c30e3c52e01efd3492068aabc3b1723775`；gzip 完整、32 张表 |
| 数据库变更 | 无新增迁移；Flyway V41 `success=1`、失败迁移 0 条；32 表 / 19 家族 / 205 账号 / 2 个有手机号账号 |
| 部署槽位 | 绿槽 `127.0.0.1:8092`；nginx 六处上游由 8091 切换为 8092 |
| 在任版本 | `family-tree-app-green`：`v94-local`，replicas=1；Pod Ready 1/1、0 重启 |
| 回滚锚 | 蓝槽 `family-tree-app-blue`：`v93-local`，replicas=0；docker 与 containerd 均保留 |
| nginx 备份 | `/etc/nginx/conf.d/family-tree.conf.bak.pre-v94-20261128_173316` |

### 发布内容明细

- 新增公开 Home：产品价值、功能预览、三步上手、隐私说明、邀请码入口与常见问题，仅使用通用示意内容，不读取或展示真实家族、人物、关系及照片数据。
- 根路径按服务端会话状态内部转发：匿名用户到 `/home.html`，已登录用户到 `/index.html`；根响应设置 `Cache-Control: no-store`。
- 登录页新增“返回首页”；邀请码自动去除首尾空白并转大写，进入登录页后切换注册页并回填；`mode=register` 可直接进入注册入口，注册关闭配置仍优先生效。
- 修复 URL 驱动注册 Tab 的初始化顺序，确保自动切换发生在 Tab 点击监听器注册之后。
- Home 使用外链脚本与既有 CSP，响应式导航、键盘焦点、Escape 关闭、减少动态效果偏好与语义化 FAQ 均已接入；未新增匿名业务数据接口。
- `sw.js` 未改动：导航仍为 network-first，离线兜底仍为 `/index.html`；本次不改变既有离线工作台策略。

### 验收结果

- 全量测试：`./mvnw -B clean package` 共 **747** 个用例，0 失败、0 错误、0 跳过。
- 本地浏览器验证：H2 + fakeredis 隔离环境完成桌面/移动布局、移动菜单、FAQ、注册关闭与配置失败提示、非法/有效邀请码、注册入口、登录、建族及已登录根路由验证；未访问或写入生产数据。
- 产物自检：`node --check`、`git diff --check` 通过；发布 jar 内 Home/login 五个关键静态资产与源码逐字一致。
- 镜像自检：基于 `family-tree:app-base` 构建，镜像层数 2，镜像内 jar SHA256 与发布 jar 一致；containerd 导入成功。
- 旁路验收：绿槽 health `UP`，平台配置三开关保持 `true`；匿名 `/` 返回 Home，`/home.html`、Home CSS/JS、登录页及登录 JS 均返回 200 且版本正确；根响应带 `no-store`。
- 接口验收：匿名访问 `/api/auth/me`、`/api/node/list`、`/api/link-proposals`、`/api/admin/link-proposals`、`/api/admin/graph/overview` 均返回 HTTP 401 / code 401。
- 资产摘要：生产下发的 `home.html`、`home.css`、`home.js`、`login.html`、`login.js` 分别为 `fbf1f6c5…`、`f6a9deeb…`、`0caba208…`、`840a85f3…`、`b2e41285…`，与本地源码一致。
- 数据库验收：Flyway V41 成功、失败迁移 0 条；32 表 / 19 家族 / 205 账号 / 2 个有手机号账号，与发版前基线一致。
- 生产检查：nginx 旧 8091 引用剩余 0、新 8092 引用 6；绿槽稳定后连续 5/5 次匿名根请求命中 v94 Home，本机经公网 IP 复核根、Home、登录页均返回 200 且资产版本正确；蓝槽缩容后仍为 200/UP。
- 会话连续性：切流前后 Redis 活跃会话均为 0，排序会话键 SHA256 均为空集合摘要 `e3b0c442…`；本次没有活跃会话可作“不掉线”结论。
- 哨兵：MySQL、Redis、健康检查、登录冒烟四项通过，退出码 0；健康基址为 8092。
- 日志：绿槽启动后仅有 `/favicon.ico` 静态资源 ERROR 1 条，属既有未知路径问题 #203；切流瞬间旧蓝槽 `/home.html` ERROR 1 条，未发现应用异常。

### 遗留事项

- 本次按用户要求未提交、未推送代码；当前镜像不能由某个 commit 直接复现。
- 生产浏览器视觉验收未完成：Chrome DevTools MCP 访问公网 IP 时返回 `ERR_BLOCKED_BY_CLIENT`，未改用其他方式绕过；本地浏览器验证与生产静态/运行态验收分开记录。
- Home 离线行为未重新验证；既有 Service Worker 离线兜底仍指向工作台 `/index.html`。
- `msjzp.xyz` 仍受既有备案接入限制；公网 IP 80 验证通过，未修改 DNS、安全组或网络配置。
- 未使用生产账号写入回归数据。

---

## v93 — 平台基础族谱牌位卡样式对齐

| 项目 | 内容 |
|---|---|
| 发布时间 | 2026-11-28 15:49:12 CST / 2026-11-28 07:49:12 UTC |
| 发布内容 | 平台管理跨族图谱中，点击家族后展示的基础族谱改用与主族谱一致的竖排牌位卡、双线框、绶带、辈分、生卒年与 deceased/missing 样式 |
| 代码版本 | 未提交工作区；HEAD `853795d`；v93 随 v91/v92 未提交功能基线构建，用户明确要求本批不提交、不推送 |
| 发布镜像 | `family-tree:v93-local`；镜像 ID `sha256:8d9959644623ff0c319bffd688c773451055fb110aadefd32aeb5dbaaa78e0ac` |
| jar 校验 | SHA256 `3d86341b3f4b6bf1c428734fd5e7a1ca58d8a9f11e73eb9b1ee064a4d1a53fe3`；138,000,240 字节；本地、实例、镜像内一致 |
| containerd 清单 | `sha256:604baf981301ff9f353f34baee9e6cff4943a8bae8f19541ef4debd090980d1e` |
| 数据库备份 | `/opt/family-tree/backups/family_tree_20261128_154317.sql.gz`；69,733 字节；SHA256 `e2012656f7c8f7b9c6daab74c5e0dbdafb4f6348b31035292c657ba7c3270c33`；gzip 完整、32 张表 |
| 数据库变更 | 无新增迁移；Flyway V41 `success=1`，失败迁移 0 条；32 表 / 15 家族 / 196 账号 / 17 条正式跨族链接 |
| 部署槽位 | 蓝槽 `127.0.0.1:8091`；nginx 六处上游由 8092 切换为 8091 |
| 在任版本 | `family-tree-app-blue`：`v93-local`，replicas=1；Pod Ready 1/1、0 重启 |
| 回滚锚 | 绿槽 `family-tree-app-green`：`v92-local`，replicas=0；docker 与 containerd 均保留 |
| nginx 备份 | `/etc/nginx/conf.d/family-tree.conf.bak.pre-v93-20261128_154912` |

### 发布内容明细

- 基础族谱节点由横向信息卡调整为主族谱同款 64×132 竖排牌位卡，保留 14px 夫妻间距。
- 增加双线内框、左侧绶带、辈分、竖排姓名、底部性别与生卒年；在世与 deceased 使用不同纸色和墨色，缺失信息使用虚线弱化样式。
- 夫妻并排使用心形分隔，姓名过长时按字符数收缩字号；服务端返回文本继续经过 HTML 转义。
- `admin-graph.js` 缓存版本更新为 `20261128f`，普通用户主族谱渲染逻辑未改动。

### 验收结果

- 全量测试：`./mvnw -B clean package` 共 **744** 个用例，0 失败、0 错误、0 跳过。
- 产物自检：jar 内 `admin.html` 与 `admin-graph.js` 分别和仓库源文件逐字一致，SHA256 为 `81420bcd…` 与 `adef4783…`；镜像内 jar 与发布 jar 逐字一致；`node --check` 与 `git diff --check` 通过。
- 旁路验收：蓝槽 health `UP`，`admin.html` 下发 `admin-graph.js?v=20261128f`；牌位卡尺寸、双线框、绶带、deceased/missing 样式标记均存在；平台配置三开关保持 `true`。
- 接口验收：匿名访问 `/api/link-proposals`、`/api/admin/link-proposals`、`/api/admin/graph/overview` 均返回 HTTP 401 / code 401。
- 数据库验收：Flyway V41 成功标记为 1，失败迁移 0 条；32 表 / 15 家族 / 196 账号 / 17 条正式链接，与发版前基线一致。
- 生产检查：nginx 80 根路径、登录页、Admin 页、图谱脚本、配置端点均通过；公网 IP 80 对应入口与配置端点均通过。
- 会话连续性：切流前后 Redis 活跃会话均为 2，排序会话键 SHA256 均为 `0335cdf8abfcdaf3b35c8f56ef9eeb08162232510da11bdfcac598114333b582`。
- 流量切换证据：nginx 旧 8092 引用剩余 0、新 8091 引用 6；从 07:49:30 UTC 起的稳定窗口记录 46 条 `http-nio-8091` 请求、0 条 `http-nio-8092` 请求。
- 哨兵：MySQL、Redis、健康检查、登录冒烟四项通过，退出码 0；当前健康基址为 8091。
- 日志：蓝槽 Pod 07:45:39 UTC 启动后 `http-nio-8091` ERROR 新增 0 条；同期绿槽扫描器未知路径 ERROR 不计入蓝槽应用验收。

### 遗留事项

- 本次按用户要求未提交、未推送代码；当前镜像不能由某个 commit 直接复现。
- 生产平台提案开关保持既有安全默认，未在发布过程中代为开启。
- `msjzp.xyz` 仍受既有备案接入限制；公网 IP 80 验证通过，未修改 DNS、安全组或网络配置。
- 浏览器视觉验收未完成：`browser-use` CLI 不可用，未擅自切换其他浏览器自动化工具；本版完成的是静态资产内容、镜像与生产运行态验收，不声称已通过浏览器视觉回归。
- 未使用生产账号写入回归数据。

---

## v92 — 图谱基础树与节点链接数配色

| 项目 | 内容 |
|---|---|
| 发布时间 | 2026-11-28 15:09:13 CST / 2026-11-28 07:09:13 UTC |
| 发布内容 | 普通端与 Admin 端 Controller 分包、跨族链接提案治理随 v91 未提交批次一并发布；平台跨族图谱支持点击家族查看基础族谱，节点按当前视图跨族链接数量分级着色 |
| 代码版本 | 未提交工作区；HEAD `853795d`，v91 功能基线 `39d0f34`；用户明确要求本次不提交代码 |
| 发布镜像 | `family-tree:v92-local`；镜像 ID `sha256:19b8220532c95a7cb8553abb3cc533a9ff982357079765ffc32f9139d113ef72` |
| jar 校验 | SHA256 `943361b95bca79a4040a5e0a3d7030a457632663e0d2a648d7a04c5905246390`；137,999,567 字节，本地、实例、镜像内一致 |
| containerd 清单 | `sha256:b2cac8d126a1439b5151692462743ff8680fa7ebf5b925452fb940d9da62adb0` |
| 数据库备份 | `/opt/family-tree/backups/family_tree_20261128_150313.sql.gz`；69,704 字节；SHA256 `aec1a314f72192207eefe9c99161f2d961c0040c5ea3c14d79665f88e9d324ed`；gzip 完整、UTF-8 可读、32 表 |
| 数据库变更 | 无新增迁移；Flyway V41 `success=1`，失败迁移 0 条；32 表 / 15 家族 / 196 账号 / 17 条正式跨族链接 |
| 部署槽位 | 绿槽 `127.0.0.1:8092`；nginx 六处上游由 8091 切换为 8092 |
| 在任版本 | `family-tree-app-green`：`v92-local`，replicas=1 |
| 回滚锚 | 蓝槽 `family-tree-app-blue`：`v91-local`，replicas=0；docker 与 containerd 均保留 |
| nginx 备份 | `/etc/nginx/conf.d/family-tree.conf.bak.pre-v92-20261128_150913` |

### 发布内容明细

- 跨族治理图谱点击家族节点后，在当前详情区通过“基础族谱 / 跨族链接”页签切换；基础树仅展示姓名、性别、生卒年和世代。
- 基础树复用家族人物网络数据，支持夫妻并排、亲子/收养层级、多根、孤立节点与循环关系兜底，不重复渲染节点。
- 治理视图家族节点按当前可见跨族链接数量分为 `0`、`1`、`2–3`、`4–6`、`7–11`、`12+` 六档；异常节点保持红色。
- 链接类型筛选后重新计算节点颜色，图例和悬浮提示同步显示当前视图链接数、总链接数与异常数；人物网络模式保留原配色语义。
- v91 提案治理批次随本次发布：普通用户与平台管理员 Controller 分包，手工跨族链接改为双端投票提案与平台审核，正式链接撤销使用 POST。

### 验收结果

- 全量测试：`./mvnw -B clean package` 共 **744** 个用例，0 失败、0 错误、0 跳过。
- 隔离浏览器回归：提案创建、双端投票、撤回、重新同意、评论、通过、驳回和取消流程通过；基础树夫妻层级、循环兜底、文本转义和空树行为通过；六档节点颜色、异常红色、筛选重算与 tooltip 通过。未向生产写入回归数据。
- 旁路验收：绿槽 health `UP`，平台配置三开关保持 `true`，`admin.html` 下发 `admin-graph.js?v=20261128e`，基础树与链接数配色资产内容断言通过。
- 接口验收：匿名访问用户端与 Admin 端提案列表均返回 HTTP 401 / code 401。
- 数据库验收：Flyway V41 `success=1`，失败迁移 0 条，32 表；业务基线为 15 家族、196 账号、17 条正式跨族链接。
- 镜像自检：镜像层数 2，Entrypoint、User、WorkDir 与 v91 一致，镜像内 jar 摘要与发布 jar 逐字一致。
- 生产检查：nginx 80 根路径、登录页、Admin 页、图谱脚本、配置端点均通过；公网 IP 80 根路径、登录页、Admin 页、图谱脚本与配置端点均通过。
- 会话连续性：切流前后 Redis 活跃会话均为 1，会话键排序 SHA256 前后均为 `c47058b07c0ac4695e194e2917cb37d8f9e50e5fecc5aeb4a75e2e9d0aac065b`；TTL 分别为 1,777,264 与 1,797,101。
- 流量切换证据：nginx 旧端口引用 0、新端口引用 6；切流后窗口记录 28 条 `http-nio-8092`，另有 1 条旧槽在途日志；蓝槽于 15:09:59 CST 缩容后，最终窗口 49 条应用日志全部为 `http-nio-8092`，无 8091 残留。
- 哨兵：MySQL、Redis、健康检查、登录冒烟四项通过，退出码 0。
- 日志：按 UTC 切片，绿槽 07:04:55 启动至验收窗口应用 ERROR 日志新增 0 条。

### 遗留事项

- 本次按用户要求未提交、未推送代码；当前镜像不能由某个 commit 直接复现。
- 生产平台提案开关保持既有安全默认，未在发布过程中代为开启；需管理员在平台配置中显式启用后，用户才能发起提案。
- `msjzp.xyz` 的域名入口仍受既有备案接入限制；公网 IP 80 验证通过，未修改 DNS、安全组或网络配置。
- 未使用生产账号写入测试数据；生产验收采用匿名权限、健康、配置、会话键与日志证据。

---

## v91 — 跨族链接提案治理

| 项目 | 内容 |
|---|---|
| 发布时间 | 2026-11-28 09:32:17 CST / 2026-11-28 01:32:17 UTC |
| 发布内容 | 手工跨族关联改为「两端 N 个有效节点投票 + 平台审核」提案制；用户端与平台管理端提案页面、投票/撤回/取消/评论/审核/过期失效流程上线；普通端与 Admin 端 Controller 分包 |
| 代码版本 | 未提交工作区；基线 `39d0f34`，用户明确要求本次不提交代码 |
| 发布镜像 | `family-tree:v91-local`；镜像 ID `sha256:08bb7199b1017c726c2c174669f1ad642a419e95317892abe78a8e9e43f74821` |
| jar 校验 | SHA256 `45847f5292cd04ab1222cf8418b6bd3d4f7e3d726725b079cc30c06051ee4b1f`；137,996,984 字节，本地、实例、镜像内一致 |
| containerd 清单 | `sha256:45310c9f7617eeca84501e6af41d3b3f2e83ab666a9e1d1f4ab405cf8e5db345` |
| 数据库备份 | `/opt/family-tree/backups/family_tree_20261128_092856.sql.gz`；SHA256 `c67bb897c239ab69aa9a9df921e9426110e477d6521f4899d2880d59f8f10d03`；gzip 完整、29 表 |
| 数据库变更 | Flyway V41 成功；新增 `family_node_link_proposal`、`family_node_link_proposal_vote`、`family_node_link_proposal_event` 三表，并为 `family_node_link` 增加 `proposal_id` 列；共 32 表，迁移成功标记为 1 |
| 部署槽位 | 蓝槽 `127.0.0.1:8091`；nginx 六处上游由 8092 切换为 8091 |
| 在任版本 | `family-tree-app-blue`：`v91-local`，replicas=1 |
| 回滚锚 | 绿槽 `family-tree-app-green`：`v90-local`，replicas=0；docker 与 containerd 均保留 |
| nginx 备份 | `/etc/nginx/conf.d/family-tree.conf.bak.pre-v91-20261128_093217` |

### 发布内容明细

- 家族管理员不能直接创建正式手工跨族链接，必须发起提案；正式链接在平台审核通过后建立。
- 平台最低同意数与家族要求共同决定有效门槛，取两者较大值；两端分别统计不同节点的有效投票。
- 投票账号须为对应家族的现有成员并绑定本家族节点；退出家族、解绑/换绑、节点或家族状态失效会使投票或提案失效。
- 支持提案列表与详情、同意、撤回同意、发起人取消、管理员评论、通过、驳回、到期和自动失效处理。
- 正式链接撤销改为 POST 接口；既有 `ACCOUNT` 自动链接与存量正式链接保持原状。
- 普通用户 Controller 与平台管理员 Controller 分别放入 `controller.user` 和 `controller.admin` 包，原 URL 映射保持不变，提案相关接口按新规则调整。

### 验收结果

- 全量测试：`./mvnw -B clean package` 共 **744** 个用例，0 失败、0 错误、0 跳过。
- 隔离验证：H2 Flyway V1–V41 启动成功；浏览器覆盖发起提案、双端投票、达到待审核、撤回后回退、重新同意、管理员评论与通过、驳回意见必填及驳回落库。
- 旁路验收：蓝槽 health `UP`，登录页 200，平台配置三开关保持 `true`，`index.html`/`admin.html` 下发 `20261128b` 静态版本。
- 接口验收：匿名访问用户端与 Admin 端提案列表均返回 401；前端提案资产包含新 API。
- 数据库验收：V41 `success=1`，三张新表存在且初始为空，`family_node_link.proposal_id` 列存在；业务基线为 10 家族、181 账号、8 条正式链接。
- 镜像自检：镜像层数 2，Entrypoint、User、WorkDir 与基座一致，镜像内 jar 摘要与发布 jar 逐字一致。
- 生产检查：nginx 80 根路径与登录页均 200，配置端点正常，静态版本为新版，提案接口受登录保护；公网 IP 80 根路径与登录页均 200。
- 会话连续性：切流前后 Redis 活跃会话均为 0，无活会话可对照；会话存储仍为 Redis，代码与配置未改。
- 流量切换证据：nginx 六处上游全部切至 8091，旧端口引用为 0；验收日志同时含切流前绿槽与切流后蓝槽线程，哨兵确认当前健康基址为 8091。
- 哨兵：MySQL、Redis、健康检查、登录冒烟四项通过，退出码 0。
- 日志：蓝槽启动至切流验收窗口应用 ERROR 日志新增 0 条。

### 遗留事项

- 本次按用户要求未提交、未推送代码；当前镜像不能由某个 commit 直接复现。
- 生产平台提案开关保持既有安全默认，未在发布过程中代为开启；需管理员在平台配置中显式启用后，用户才能发起提案。
- `msjzp.xyz` 的域名入口仍返回 403，属既有备案接入限制；公网 IP 80 验证通过，未修改 DNS、安全组或网络配置。
- 真实 Redis 登录后操作流已在原隔离浏览器验证中受本机 Redis 缺失限制；本次生产仅做匿名权限、健康与会话键检查，未使用生产账号写入测试数据。

---

## v90 — 邀请码短链接服务

| 项目 | 内容 |
|---|---|
| 发布时间 | 2026-11-27 21:48:33 CST / 2026-11-27 13:48:33 UTC |
| 发布内容 | 新增公开邀请短链 `/s/{inviteCode}`；邀请链接与二维码统一使用短地址，旧长链接保持兼容 |
| 代码版本 | `39d0f34` |
| 发布镜像 | `family-tree:v90-local`；镜像 ID `sha256:3a140de5f7eb792374fd399c4afa6c600bb07a5335098a9b3b8160421248f3ed` |
| jar 校验 | SHA256 `ac856138f23e5a40b05ceadc7957e7a47a7f15f3c016d3e181a83cc2240e6e8c`；本地、实例、镜像内一致 |
| containerd 清单 | `sha256:6a39513a515846927878bf6bc890d6d99ac521e1882dd47629d627ff78dfab83` |
| 数据库备份 | `/opt/family-tree/backups/family_tree_20261127_214242.sql.gz`；SHA256 `2ec9e57c3cb130ea195c2d94e0b7e6c6ad99398649bd5cab2f38f57d01582a99` |
| 数据库变更 | 无新增表、无迁移变更；29 表 / 10 家族 / 181 账号 / Flyway V40 |
| 部署槽位 | 绿槽 `127.0.0.1:8092`；nginx 六处上游由 8091 切换为 8092 |
| 在任版本 | `family-tree-app-green`：`v90-local`，replicas=1 |
| 回滚锚 | 蓝槽 `family-tree-app-blue`：`v89-local`，replicas=0；docker 与 containerd 均保留 |
| nginx 备份 | `/etc/nginx/conf.d/family-tree.conf.bak.pre-v90-20261127_214833` |

### 发布内容明细

- 有效邀请码访问 `/s/{inviteCode}` 时返回 302，跳转到 `/login.html?invite={规范邀请码}`。
- 邀请码按 `trim` 与大写规范化，大小写变体可访问；未知邀请码返回 404。
- 短链响应设置 `Cache-Control: no-store`，避免跳转结果被缓存。
- 后端生成的邀请链接、二维码及前端生成的邀请链接统一改为短地址。
- 原有 `/login.html?invite={inviteCode}` 长链接继续可用。

### 验收结果

- 全量测试：711 个用例，0 失败、0 错误、0 跳过；短链专项 13 个用例通过。
- 旁路验收：健康检查、平台配置、登录页、静态资源版本、短链有效码/大小写变体/未知码、旧长链接及业务基线均通过。
- 镜像自检：镜像层数与 v89 一致，镜像内 jar SHA256 与发布 jar 一致。
- 生产检查：入口、登录页、配置端点、短链及公网 80 均通过；公网短链最终到达 `https://msjzp.xyz/login.html?invite=665AYXS6`。
- 会话连续性：切流前后 Redis 活跃会话均为 2，切流后 TTL 分别为 1799 秒与 1732 秒。
- 流量切换证据：切流后 42 条应用日志均为 `http-nio-8092`，无 8091 残留。
- 哨兵：MySQL、Redis、健康检查、登录冒烟四项通过，退出码 0。
- 日志：切流后应用错误日志新增 0 条。

### 遗留事项

- 无。

---

## v89 — 平台管理员 A/B 图谱

| 项目 | 内容 |
|---|---|
| 发布时间 | 2026-11-27 12:28:09 CST / 2026-11-27 04:28:09 UTC |
| 发布内容 | 平台管理员 A 治理型跨族图谱与 B 单家族人物网络上线；修复跨族边颜色、异常虚线与筛选显示 |
| 代码版本 | `28c02a0`；SOP 登记 `367e647` |
| 发布镜像 | `family-tree:v89-local`；镜像 ID `sha256:14dcf6293da24f16d15b1eede4b08263ee8eb4b85a5edbda207e5e518ad1d52a` |
| jar 校验 | SHA256 `8f3e07d365afc0d1f0be6537a7ae0f3ee044c956a74093bca5cb692f9a09b0fe`；本地、实例、镜像内一致 |
| containerd 清单 | `sha256:6f1d2043ed2310f58d8e6f08cd09135f583db2a912879cab452f57a4d984a390` |
| 数据库备份 | `/opt/family-tree/backups/family_tree_20261127_110227.sql.gz`；SHA256 `b877239e31780339e5454499c2ae9984a0ffd49e3a193f659b4f6d7445864bdf` |
| 数据库变更 | 无新增表、无迁移变更；29 表 / 10 家族 / 171 账号 / Flyway V40 |
| 部署槽位 | 蓝槽 `127.0.0.1:8091`；nginx 六处上游由 8092 切换为 8091 |
| 在任版本 | `family-tree-app-blue`：`v89-local`，replicas=1 |
| 回滚锚 | 绿槽 `family-tree-app-green`：`v88-local`，replicas=0；docker 与 containerd 均保留 |
| nginx 备份 | `/etc/nginx/conf.d/family-tree.conf.bak.pre-v89-20261127_042809` |

### 发布内容明细

- A：平台管理员治理型跨族图谱，提供家族聚合边、质量巡检与问题筛选。
- B：单家族人物网络，展示族内关系与跨族 link，保持原树状渲染逻辑不变。
- 修复跨族链路按数值 `linkType` 筛选，血缘边与姻亲边使用不同颜色。
- 修复异常跨族链路的虚线样式，并支持仅查看异常链路。

### 验收结果

- 全量测试：708 个用例，0 失败、0 错误、0 跳过。
- A/B 阶段回归报告：`docs/test-report-phase-a-graph.md`、`docs/test-report-phase-b-graph.md`。
- 旁路验收：健康检查、平台配置、静态资源版本、图谱资产内容、匿名权限及业务基线均通过。
- 生产检查：入口、登录页、配置端点、图谱脚本及公网 80 均返回 200。
- 会话连续性：切流前后 Redis 活跃会话均为 1，TTL 1791 秒。
- 流量切换证据：切流后最近 80 行日志均为 `http-nio-8091`。
- 哨兵：MySQL、Redis、健康检查、登录冒烟四项通过，退出码 0。
- 日志：应用启动窗口无新增应用异常。

### 遗留事项

- 切流瞬间旧槽曾返回 1 次 `/js/ft/admin-graph.js` 404；同一探针经新槽立即返回 200 且内容核验通过，判定为切流期预期瞬态。
- 无其他未完成验收或已知发布问题。

---

## 后续版本登记模板

复制以下模板，在本节之前新增发布记录：

```markdown
## vNN — 版本摘要

| 项目 | 内容 |
|---|---|
| 发布时间 | YYYY-MM-DD HH:mm:ss CST / YYYY-MM-DD HH:mm:ss UTC |
| 发布内容 | 用户可读的功能、修复或配置变更摘要 |
| 代码版本 | commit SHA |
| 发布镜像 | family-tree:vNN-local；镜像 ID SHA256 |
| jar 校验 | SHA256；本地、实例、镜像内是否一致 |
| containerd 清单 | SHA256 |
| 数据库备份 | 实例路径；大小；SHA256 |
| 数据库变更 | 无 / Flyway 版本；是否新增表；是否破坏性迁移 |
| 部署槽位 | 蓝槽 8091 / 绿槽 8092；nginx 切换方向 |
| 在任版本 | Deployment 名称；镜像；replicas |
| 回滚锚 | Deployment 名称；镜像；replicas |
| nginx 备份 | 配置备份路径 |

### 发布内容明细

- 功能或修复项 1
- 功能或修复项 2

### 验收结果

- 全量测试：数量与结果
- 回归报告：仓库相对路径
- 旁路验收：结果
- 生产检查：入口、公网与关键端点结果
- 会话连续性：活跃会话对照或说明无活会话可测
- 流量切换证据：线程名或请求证据
- 哨兵与日志：结果

### 遗留事项

- 未完成验收、已知问题或预期瞬态；没有则写“无”
```
