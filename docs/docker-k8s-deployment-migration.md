# 部署形态演进纪实：Docker → Kubernetes，以及回到 Docker 的完整预案

> 覆盖 2026-11-21 全天四步演进（compose 专属栈 → k8s 应用层 → k8s 数据层 → prod/test 双 namespace），
> 含每一步的操作序列、验证口径、踩坑实录，以及「如何退回 Docker 部署」的分场景预案。
> 日常发版/备份/test 拉起的最小 SOP 在 [k8s/README.md](../k8s/README.md)，本文是它的背景与决策全貌。

---

## 〇、当前状态速览（2026-11-21 晚）

| 项 | 状态 |
|---|---|
| 生产运行时 | Docker Desktop k8s（kind 模式，**版本固定 1.35.0**，context `docker-desktop`） |
| 生产 namespace | `prod`：应用双槽 `family-tree-app-blue` / `family-tree-app-green`（selector 蓝绿，v71 起新名；此前旧名 `family-tree-app`/`family-tree-app-b`）+ `mysql` + `redis` 全部在集群内 |
| 测试 namespace | `test`：同构全套但**默认 0 副本休眠**，按需拉起做回归 |
| 应用镜像 | `family-tree:v70-local`（DD registry-mirror 直接供给本地镜像，无需 push） |
| 流量入口 | `ft-k8s-proxy`(socat, 8090→NodePort 30090) ← cloudflared 隧道，全程零改动 |
| 宿主机兼容口 | 3308/6381 = `ft-k8s-mysql-proxy`/`ft-k8s-redis-proxy` → NodePort 30308/30381 |
| compose 栈 | 数据栈 `family-tree-mysql`/`family-tree-redis` **已停用保留**，是冻结回滚副本 |
| 旧 namespace | `family-tree` 已于 2026-11-21 20:15 点名授权后删除（保险 PVC 一并回收） |
| Flyway | V1~V40，库 `family_tree`（prod）/ `family_tree_test`（test） |

**红线（先看）：**
1. ⚠ 严禁在 DD 里执行 `Reset Kubernetes Cluster` —— kind 节点数据卷里有全部 PV，等于**连生产数据库一起清空**。
2. ⚠ 回归 test battery 只准打 `test` namespace（宿主 8091），严禁打 prod。
3. ⚠ compose 数据栈是 11-21 切换时点的**冻结快照**，回切 Docker 前必须先做 k8s→compose 的反向 dump/restore，否则丢切换后的全部写入。
4. ⚠ ConfigMap/数据源切换必须确认 `kubectl apply` 输出 `configured`（不是 `unchanged`），并以目标实例的行为探针为准。

---

## 一、演进时间线

| 时间 | 阶段 | 内容 |
|---|---|---|
| 11-21 07:56 | compose 专属化 | 生产从共享 `knowledge-mysql`(3307)/`bw-redis`(6380) 迁出为专属 compose 栈 `family-tree-mysql`(3308)/`family-tree-redis`(6381)，dump/pipe 迁移并核数一致 |
| 11-21 09:15 | v67 固化 | jar 内 application-mysql.yml 默认值对齐专属栈（3308/family_tree_app/空口令），回切式部署 `.env` FT_IMAGE |
| 11-21 15:45 | **k8s Phase 1** | 应用进集群（数据层仍在宿主 compose），PVC 播种照片/日志，ft-k8s-proxy 保 8090 入口 |
| 11-21 16:40 | **k8s Phase 2** | mysql/redis StatefulSet 进集群，dump/restore 字节校验迁移，compose 数据栈停用留作回滚副本 |
| 11-21 17:45 | 双 namespace | `family-tree`→`prod` 迁移（k8s 不支持 ns 改名，走建新→迁移→删旧），新建休眠 `test` 栈 |

---

## 二、为什么要上 k8s（以及为什么值得回退时想清楚）

- 动机：多环境隔离（prod/test）、声明式发布与回滚、健康探针自愈、为后续扩副本/滚动升级留口。
- 代价（都已在本仓实测）：DD kind 单节点只有 4GB VM、入口要靠 socat 补宿主机端口、distroless 无 shell 探针只能 httpGet、
  LoadBalancer/cloud-provider 不可用、`Reset` 一键毁生产的操作风险被放大。
- 结论：小规模单机部署下 k8s 收益是**流程化**而非性能；回退到 Docker 的成本可控（见第六章预案），
  因为数据层永远只有一套在写、回退路径 = 反向 dump/restore + 换入口。

---

## 三、Phase 0~1：应用进集群（数据层仍在 compose）

### 3.1 前置：把 DD k8s 拉起来且必须钉在 1.35.0

现象与根因（确定性 bug，别浪费时间重试）：DD 4.100 默认 kind 1.37.0，kubeadm 1.36+ 拒绝 kind 写入的
`kubeadm.k8s.io/v1beta3` 配置，集群永远初始化失败（证据在 kind.log / com.docker.backend.log）。

```bash
docker desktop stop
# 编辑 ~/Library/Group Containers/group.com.docker/settings-store.json
#   "KubernetesNodesVersion": "1.35.0"     ← 1.35 是接受 v1beta3 的最高版本
docker desktop start
```

DD 自动拉 `kindest/node:v1.35.0`（~1.3GB）并建集群；compose 容器会随之全部重启（注意 137 退出史，见第九章坑）。
验证：`kubectl config current-context` == `docker-desktop`，`kubectl get nodes` == `desktop-control-plane` Ready。

### 3.2 关键环境事实（决定了整个网络方案）

- pod 能**直接看见本地 docker 镜像**（DD registry-mirror），`family-tree:v70-local` 首拉 32s，无需本地 registry；
  `imagePullPolicy: Always` 可感知重建。
- pod 访问宿主机服务走 `host.docker.internal:<端口>`（192.168.65.254）；但 bridge 网关只能碰 0.0.0.0 绑定的端口，
  compose 的 mysql/redis 绑 127.0.0.1，所以 Phase 1 只能经宿主机 8090/3308/6381 端口族。
- 宿主机**打不通** NodePort 和 pod IP；`type: LoadBalancer` 的 cloud-provider 建 envoy 容器必败。
  → 一切入口统一用 **kind 网络内的 alpine/socat 容器按容器名转发**：

```bash
docker run -d --name ft-k8s-proxy --restart=unless-stopped --network kind -p 8090:8090 \
  alpine/socat TCP-LISTEN:8090,fork,reuseaddr TCP:desktop-control-plane:30090
```

- 因此 compose 的 `family-tree-app` 必须先 `docker stop` 且置 `--restart=no`（否则 DD 重启后抢 8090）；
  **cloudflared 隧道完全不用改**（它只认 127.0.0.1:8090）。

### 3.3 清单与 PVC 播种

清单见 `k8s/namespace.yaml`（现为 prod）、`configmap.yaml`、`deployment-blue.yaml`（当时为 `deployment.yaml`，蓝绿拆分后另有 `deployment-green.yaml`）、`service.yaml`、`pvc.yaml`。
要点：单副本 + `strategy: Recreate`（PVC RWO 不允许双挂）、distroless 探针只能 httpGet、
startupProbe `5s×60` 容忍最长 3.5min 冷启动。照片/日志原来是 bind mount，kind 节点看不见宿主机目录，
**一次性播种进 local-path PVC**（tar→helper pod→kubectl cp→解包→清 macOS `._*`，命令在 k8s/README.md）。
从此 `./docker-data` 冻结，备份照片必须走 PVC helper。

### 3.4 切流与验证

`kubectl apply` 全套 → 等 pod 1/1 → 电池：`/login.html` 200、index gedcom 计数、`/api/auth/me` 401、
隧道 200、`kubectl logs` 0 ERROR。回滚演练（Phase 1c）：停 ft-k8s-proxy → `docker start` compose app →
60s 内恢复，验证通过后再切回。**这一步的回滚很简单，因为数据层从没动过。**

---

## 四、Phase 2：数据层进集群（compose 数据栈变冻结副本）

### 4.1 起 StatefulSet

`k8s/mysql.yaml`（mysql:8.0，args 对齐 utf8mb4/utf8mb4_unicode_ci，MYSQL_DATABASE/USER/PASSWORD +
MYSQL_ROOT_PASSWORD 来自 secret `family-tree-db`，5Gi volumeClaimTemplate，limits 1Gi）、
`k8s/redis.yaml`（redis:8.8.0，`--appendonly yes`，1Gi，limits 192Mi）。
内存预算：app 1Gi + mysql 1Gi + redis 192Mi + kind 开销 ≈ 顶满 3.8Gi allocatable——**这就是 test 栈必须休眠的原因**。
ClusterIP Service `mysql:3306`/`redis:6379`，供 ConfigMap 用 namespace 本地 DNS。

secret 用 CLI 从 .env 注入、绝不落盘也不回显：

```bash
kubectl -n prod create secret generic family-tree-db \
  --from-literal=DB_PASSWORD="$(grep ^DB_PASSWORD= .env | cut -d= -f2-)" \
  --from-literal=MYSQL_ROOT_PASSWORD="$(grep ^MYSQL_ROOT_PASSWORD= .env | cut -d= -f2-)"
```

### 4.2 dump/restore 迁移（字节校验是硬要求）

1. 冻结应用：`kubectl -n prod scale deploy family-tree-app-blue family-tree-app-green --replicas=0`（旧名时期为 `family-tree-app`/`family-tree-app-b`）。
2. 从 compose 库 mysqldump（`docker exec family-tree-mysql mysqldump --single-transaction ...`，不带 `--databases`，utf8mb4），存 `backup/`。
3. 灌入 k8s `mysql-0`（`kubectl -n prod exec -i statefulset/mysql -- sh -c 'exec mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" family_tree' < dump.sql`）。
4. **校验：目标库重新 mysqldump，两侧都 `sed '$d'` 后 md5sum 必须逐字节相等。**
   `-- Dump completed on <ts>` 尾行永远不等，不剔掉这个比对毫无意义（本仓实测踩过）。
5. redis **不迁移**：只有会话/登录计次（644K），代价 = 全员登出一次，已接受。以后每次数据层切换都有同样的一次性登出。

### 4.3 切数据源 + 重开宿主机兼容口

改 ConfigMap `DB_URL=jdbc:mysql://mysql:3306/...`、`SPRING_DATA_REDIS_HOST=redis` → `kubectl apply`
**必须看到 `configured`**；然后 scale app 回 1。实测血泪：第一次切换 `apply` 返回 `unchanged`（文件根本没改），
rollout 却"成功"，静态 `/login.html` 探针 200 完全掩盖了没切的事实——**静态页探针不碰数据库，不能作数据源证据**。

宿主机 3308/6381 兼容：NodePort `mysql-nodeport` 30308 / `redis-nodeport` 30381 + 两个 socat 容器。
顺序有讲究：**先停 compose 数据栈（释放 127.0.0.1:3308/6381 绑定），再起 socat 代理**，反序必 bind 冲突。

### 4.4 最终验证（探针到目标实例）

- 行为探针：坏登录一次，`login:attempt:__probe__` 必须落在 **k8s redis**（`kubectl -n prod exec statefulset/redis -- redis-cli GET ...`），用完删 key；
- 写路径探针：经宿主机 3308 查 `family_tree.operation_log` 行数只增不减（当时 934→935）；
- 全电池绿：local/index/隧道 200、gedcom 标记、401 门、0 ERROR。
- 双向演练都做过：compose 栈冷启恢复 933 行 + redis PONG，然后再切回 k8s，SOP 各跑通一遍才固化。

---

## 五、双 namespace 拆分：family-tree → prod + 新建 test

k8s **没有** namespace 改名 API，只能建新→迁移→删旧；且 **NodePort 是全集群唯一**，必须先删旧 ns 的
Service 才能把 30090/30308/30381 让给 prod——这就是 ft-k8s-proxy 们全程零改动的原理。

序列（每一步都有字节校验兜底）：

1. `kubectl create namespace prod` + secret 重建（同 4.1 命令）。
2. 全部 `k8s/*.yaml` 的 `namespace:` 批量改 prod，apply（sts 先起，空实例）。
3. MySQL 数据迁移：冻结旧 ns app → dump 旧 ns（`backup/renamespace-20261121_172521.sql`，354031B）→ restore 进 prod ns → `sed '$d'` md5 字节校验。
4. PVC 照片/日志搬家：两侧各起 kindest/node helper pod，tar 出→`kubectl cp` 宿主机中转→tar 入（4 data + 2 logs 文件），清 `._*`。
5. 删旧 ns 三个 Service（释放 NodePort）→ apply prod 的 deployment+service → 全电池验证（operation_log 935、探针 key 落 prod redis、隧道 200、0 ERROR）。
6. 旧 ns 归零（sts/deploy scale 0），**PVC 暂留作保险**；`kubectl delete namespace family-tree` 需用户点名授权（权限分类器会拦未授权的不可逆删除）——2026-11-21 20:15 已获点名授权执行完毕，保险 PVC 随 ns 一并回收。

`test` namespace：`k8s/test/` 全套同构清单，差异 = 三 workload `replicas: 0` 休眠、库名 `family_tree_test`
（首启由 MYSQL_DATABASE 自动建库并授权 family_tree_app，**不需要账号嫁接**）、limits 减半
（app 768Mi / mysql 512Mi / redis 128Mi）、NodePort 30091/30309/30382 → 宿主 8091/3309/6382 按需
port-forward 或 socat。local-path 的 PVC 平时显示 Pending 是 WaitForFirstConsumer，**不是故障**。
拉起/灌数/回收三步流在 k8s/README.md「test 环境」章节。

---

## 六、回退预案：如何回到 Docker 部署

### 6.0 先分清三档回退，选最小代价的那档

| 场景 | 回退档位 | 数据要不要动 |
|---|---|---|
| 新版本应用行为异常 | **A. 只回应用版本**（k8s 内改 tag 回切，或起旧 compose 容器） | 不动 |
| k8s 应用层整体不可信（探针/DD k8s 坏） | **B. 应用退回 compose，数据层留 k8s** | 反向 dump 不需要，但 ConfigMap/入口要换 |
| 彻底退出 k8s（DD 升级顾虑/运维偏好） | **C. 全栈退回 compose 专属数据栈** | **必须**先把 k8s 库回灌 compose 库 |

档位 A（最常用，秒级）：

```bash
sed -i '' -E 's|image: family-tree:v[0-9]+-local|image: family-tree:vOLD-local|' k8s/deployment-blue.yaml
kubectl apply -f k8s/deployment-blue.yaml && kubectl -n prod rollout status deployment/family-tree-app-blue --timeout=420s
```

### 6.1 档位 B：应用回 compose、数据层留 k8s

前提：compose 的 `family-tree-app` 服务定义还在（`docker-compose.yml`），且 k8s 数据层的 NodePort 兼容口
3308/6381 健在（应用配置 `host.docker.internal:3308/6381` 恰好走它们）。

```bash
kubectl -n prod scale deploy family-tree-app-blue family-tree-app-green --replicas=0   # 先停 k8s 应用，避免双活
docker stop ft-k8s-proxy                                        # 让出 8090
docker compose --profile mysql up -d family-tree-app           # .env FT_IMAGE 选版本
# 验证：8090/login.html 200、登录探针 login:attempt key 落在 k8s redis、operation_log 只增
```

回 k8s 方向即镜像操作：停 compose app、起 ft-k8s-proxy、scale k8s app 回 1。**数据层没换，不涉及 dump。**

### 6.2 档位 C：全栈退回 compose（正式预案，按序执行，每步有校验）

> 核心不变量：**切换后新增的写入只存在于 k8s MySQL**。compose 库是 11-21 的冻结快照，
> 直接回切 = 丢快照之后所有用户数据。所以第一步永远是反向同步。

1. **冻结入口**（防迁移期间写入漂移）：

```bash
kubectl -n prod scale deploy family-tree-app-blue family-tree-app-green --replicas=0
docker stop ft-k8s-proxy    # 8090 下线，宁可短暂不可用也不双写
```

2. **从 k8s 库 dump**（发版备份同款命令）：

```bash
kubectl -n prod exec statefulset/mysql -- sh -c \
  'exec mysqldump --single-transaction --routines --triggers --events --set-gtid-purged=OFF \
   --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" family_tree' \
  > backup/pre-docker-rollback-$(date +%Y%m%d_%H%M%S).sql
```

3. **起 compose 数据栈并回灌**（口令同 `.env`；库重建再导入，禁止半灌）：

```bash
docker start family-tree-mysql family-tree-redis
docker exec family-tree-mysql mysql --default-character-set=utf8mb4 -uroot -p"$(grep ^MYSQL_ROOT_PASSWORD= .env | cut -d= -f2-)" \
  -e "DROP DATABASE family_tree; CREATE DATABASE family_tree CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
docker exec -i family-tree-mysql mysql --default-character-set=utf8mb4 \
  -uroot -p"$(grep ^MYSQL_ROOT_PASSWORD= .env | cut -d= -f2-)" family_tree < backup/pre-docker-rollback-XXXX.sql
```

4. **字节校验**（同 4.2 第 4 步：compose 侧重 dump、两侧 `sed '$d'` md5sum 必须相等）。

5. **端口交还**：停 k8s 数据层与兼容代理（**释放 127.0.0.1:3308/6381 给 compose 的端口绑定**，
   顺序反了必 bind 冲突——socat 容器是 `docker stop`，不是删）：

```bash
docker stop ft-k8s-mysql-proxy ft-k8s-redis-proxy
kubectl -n prod scale statefulset/mysql statefulset/redis --replicas=0   # 留 PV 数据作二次保险，不删 PVC
```

6. **起 compose 应用**：`.env` 里 `FT_IMAGE` 指目标版本 → `docker compose --profile mysql up -d`
   （compose 时代口径：app 走服务名 mysql/redis，见 v67 固化后的 application-mysql.yml 默认值）。

7. **验证 + 收尾**：`/login.html` 200（HTTP 探活判启动，boot 可到 3.5min，别用日志 awk）、隧道 200、
   坏登录探针 key 落 **compose redis**（`docker exec family-tree-redis redis-cli ...`）、operation_log 行数与
   dump 校验值一致且继续只增。**一次性登出是预期行为**（redis 从不迁移）。
   留档：`kubectl -n prod get pvc` 确认 k8s 侧 PV 数据未删，双向都有底。

### 6.3 回退后再想上 k8s

按第四/五章反向执行即可，前提是把当前生效库当作源做正向 dump/restore——两侧 SOP 已在 11-21 各演练过一遍。
唯一注意点：k8s mysql-0 停用期间若 compose 库有写入，重新上切前必须再同步一次，**永远以"当前在写的那套"为源**。

---

## 七、日常运维速查

- **发版（k8s）**：备份 prod 库 → `./mvnw -B package` → 构建 `vNN-local`（永远复用上版 `/tmp/ft-vNN-ctx/Dockerfile`，
  勿凭记忆重写）→ 镜像内预检 → 发到空闲槽（`k8s/deployment-blue.yaml`/`deployment-green.yaml` 择空闲者）→ 隔离预检 → selector 切流 → 电池验证 → 镜像只留近三代（SOP 见 `k8s/README.md`「零停机发版」）。
- **备份**：MySQL = `kubectl -n prod exec statefulset/mysql -- mysqldump …`（见 6.2 第 2 步）；
  照片 = helper pod 挂 PVC tar 出；**`./docker-data` 是冻结目录，别再备份它当新的用**。
- **test 回归**：k8s/README.md「test 环境」三步流；电池只打 8091。
- 启动判定只用 HTTP 探活；MySQL CLI 一律 `--default-character-set=utf8mb4`；口令一律 `.env` grep 注入不回显。

---

## 八、踩坑实录（全部真实发生过）

1. **kind 1.37 + kubeadm v1beta3 初始化必败** → 固定 1.35.0（见 3.1）。
2. **`apply` 返回 `unchanged` 的"假切换"**：文件没改等于没切，rollout 成功与静态页 200 都不构成数据源证据；
   曾把「叙述先于执行」的状态当成已完成，靠活体 ConfigMap 探针才戳穿。切换三件套：`configured` + 探针 key 落目标实例 + 行数只增。
3. **`sed '$d'` 之前比 md5 全是无效比对**：dump 尾行时间戳永远不同。
4. **socat 与 compose 抢 127.0.0.1:3308/6381**：先停后起，切换方向不同顺序不同。
5. **DD Reset = 毁生产库**：所有 PV（含 mysql-0）住在 kind 节点的 docker 卷里，reclaim policy 为 Delete。
6. **distroless 无 shell**：探针只能 httpGet；进容器看文件用挂 PVC 的 helper pod；`kubectl exec … -- env` 也拿不到有用输出。
7. **pod label 是 `app=family-tree`（v71 起为 `app=family-tree-blue`/`app=family-tree-green`）**，`-l app=family-tree-app` 匹配不到任何东西。
8. **local-path PVC 建出来 Pending 属正常**（WaitForFirstConsumer），test 休眠栈常年 Pending。
9. **compose 管理的容器改名留作回滚会被 `compose up` 按 label 认领删除**（pre-V67 因此消失过）——compose 时代回滚用 `.env` FT_IMAGE 回切，或另起 docker-run 容器。
10. **DD 重启团灭 137**：`restart=no` 的容器永不复活；数据栈/代理都置 `unless-stopped`。
11. **redis 每次切数据层 = 全员登出一次**（会话不随库迁移），发布窗口要认这个账。
12. zsh 里 `status` 是只读变量；本机无 gh CLI、python urllib 证书失败（用 curl）。

---

## 九、清单文件索引

| 路径 | 作用 |
|---|---|
| `k8s/README.md` | 当前态最小 SOP：初始化/发版/备份/迁移回滚/test 拉起回收/DD 坑 |
| `k8s/*.yaml` | prod 清单（namespace/configmap/secret 模板/mysql/redis/data-nodeport/pvc/deployment/service） |
| `k8s/test/*.yaml` | test 休眠清单（同上结构，replicas 0 + 减半配额） |
| `docker-compose.yml` | compose 专属栈（回退档位 B/C 的执行载体；数据栈现停用为冻结副本） |
| `.env`（gitignore） | 口令唯一存放处；FT_IMAGE 为 compose 时代镜像开关 |
| `backup/`（gitignore） | 迁移/发版 dump 存证：`phase2-dryrun-*`、`phase2-prefreeze*`、`renamespace-*`、`pre-vNN-*` |
| `scripts/regression/README.md` | scratch/test 回归环境搭建（已对齐专属栈与 k8s 口径） |
