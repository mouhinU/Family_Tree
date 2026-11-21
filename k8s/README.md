# k8s 部署（Docker Desktop Kubernetes，prod/test 双 namespace）

## 拓扑（2026-11-21 命名空间拆分后）

- 集群：Docker Desktop 4.100 kind 模式，**版本固定 1.35.0**（`settings-store.json` 的 `KubernetesNodesVersion`）。
  1.37.0 有 DD bug：kind 写入的 kubeadm 配置是 `v1beta3`，kubeadm 1.36+ 直接拒绝，集群永远初始化失败。
- **`prod` namespace = 线上环境**：`family-tree-app`、`mysql`、`redis` 全部跑在 k8s，应用经集群内 DNS
  `mysql:3306` / `redis:6379` 访问数据层（见 `k8s/configmap.yaml`）。清单文件在 `k8s/*.yaml`。
- **`test` namespace = 测试环境**：结构同 prod 的独立全套栈，但**默认 0 副本休眠**（不占运行时内存），
  按需拉起做回归；库名 `family_tree_test`，与生产数据完全隔离。清单文件在 `k8s/test/*.yaml`。
- 旧 `family-tree` namespace：已归零的空壳（PVC 暂留作保险），待点名授权后 `kubectl delete namespace family-tree` 删除；
  删除会连带回收其 PVC，prod 数据不受影响（早已字节校验迁移完毕）。
- compose 栈的 `family-tree-mysql`/`family-tree-redis`/healthcheck **已停用但保留**（`restart=no`），
  是 Phase 2 切换时点的回滚副本；**切换后新增的数据只存在于 k8s prod MySQL**，回滚前必须先做增量同步（见「数据迁移/回滚」）。
- 端口总表：

| 环境 | 用途 | NodePort | 宿主机 | 转发方式 |
|---|---|---|---|---|
| prod | app | 30090 | `127.0.0.1:8090` | `ft-k8s-proxy`（常驻，cloudflared 隧道指向它） |
| prod | mysql | 30308 | `127.0.0.1:3308` | `ft-k8s-mysql-proxy`（常驻 socat） |
| prod | redis | 30381 | `127.0.0.1:6381` | `ft-k8s-redis-proxy`（常驻 socat） |
| test | app | 30091 | `127.0.0.1:8091` | 按需：`ft-test-proxy` socat 或 `kubectl port-forward` |
| test | mysql | 30309 | `127.0.0.1:3309` | 按需 socat |
| test | redis | 30382 | `127.0.0.1:6382` | 按需 socat |

  `.env` / scratch 克隆 / 备份脚本沿用 3308/6381 口径不变（指向 prod 数据层）。
- 本地镜像可见：DD registry-mirror 直接把 `docker build`/本地已有的镜像供给 k8s，无需 push；
  mysql:8.0、redis:8.8.0 均为本地镜像直接拉起。
- 数据：照片/日志在 PVC（local-path）；MySQL/Redis 数据在各自 StatefulSet 的 volumeClaimTemplate PV。
  **这些 PV 全部住在 kind 节点的 docker 卷里 —— DD 的 `Reset Kubernetes Cluster` 会连生产数据库一起清空，严禁执行；
  也不要删 prod 的 `mysql-data`/`redis`/`family-tree-data` 相关 PVC（reclaim policy 为 Delete）。**
- 内存预算：kind 节点 allocatable ≈3.8Gi。requests 口径下 prod+test 全开可调度，但**limits 是超卖的**——
  test 拉起做重型回归时避免同时给 prod 压流量（详见 test 章节）。

## 文件

| 文件 | 说明 |
|---|---|
| `namespace.yaml` | namespace prod |
| `configmap.yaml` | prod 应用环境变量（DB_URL=`jdbc:mysql://mysql:3306/family_tree?...`，redis=`redis:6379`；profile=mysql 与 compose 口径一致） |
| `secret.yaml.example` | 模板；secret `family-tree-db`（DB_PASSWORD + MYSQL_ROOT_PASSWORD）用 kubectl --from-literal 从 .env 创建，不落盘 |
| `mysql.yaml` / `redis.yaml` | MySQL/Redis StatefulSet + ClusterIP `mysql`/`redis`（utf8mb4/utf8mb4_unicode_ci；appendonly yes） |
| `data-nodeport.yaml` | mysql/redis 的 NodePort 30308/30381，供宿主机 socat 代理转发 |
| `pvc.yaml` | data/logs 两个 PVC（2Gi，storageClass standard） |
| `deployment.yaml` | 单副本、Recreate、distroless 只能 httpGet 探针（startup 5s×60 容忍 3.5min 启动）；**image tag 在此处随部署更新** |
| `service.yaml` | 应用 NodePort 30090 |
| `test/namespace.yaml` | namespace test |
| `test/configmap.yaml` | test 环境变量：库名 `family_tree_test`，redis 同为集群内 DNS |
| `test/mysql.yaml` / `test/redis.yaml` | 同构 StatefulSet，**replicas: 0** 休眠；容量减半（mysql 2Gi/512Mi limit，redis 1Gi/128Mi limit） |
| `test/pvc.yaml` | data/logs 各 1Gi |
| `test/deployment.yaml` | **replicas: 0**；limits 768Mi；image tag 拉起前按需改成目标版本 |
| `test/service.yaml` | 应用 NodePort 30091 |
| `test/data-nodeport.yaml` | mysql/redis NodePort 30309/30382 |

一次性初始化（新环境；数据迁移见「数据迁移/回滚」）：

```bash
kubectl apply -f k8s/namespace.yaml
kubectl -n prod create secret generic family-tree-db \
  --from-literal=DB_PASSWORD="$(grep ^DB_PASSWORD= .env | cut -d= -f2-)" \
  --from-literal=MYSQL_ROOT_PASSWORD="$(grep ^MYSQL_ROOT_PASSWORD= .env | cut -d= -f2-)"
kubectl apply -f k8s/configmap.yaml k8s/mysql.yaml k8s/redis.yaml k8s/data-nodeport.yaml \
  k8s/pvc.yaml k8s/deployment.yaml k8s/service.yaml
# 宿主机端口代理（compose 同名端口容器必须先停）
docker run -d --name ft-k8s-mysql-proxy --restart=unless-stopped --network kind -p 127.0.0.1:3308:3308 \
  alpine/socat TCP-LISTEN:3308,fork,reuseaddr TCP:desktop-control-plane:30308
docker run -d --name ft-k8s-redis-proxy --restart=unless-stopped --network kind -p 127.0.0.1:6381:6381 \
  alpine/socat TCP-LISTEN:6381,fork,reuseaddr TCP:desktop-control-plane:30381
```

播种照片（仅 PV 丢失/重建时执行；注意 macOS tar 会带 `._*`，解包后清掉）：

```bash
tar -czf /tmp/ft-data-seed.tgz -C docker-data .
kubectl -n prod run seedjob --image=kindest/node:v1.35.0 --restart=Never \
  --overrides='{"spec":{"containers":[{"name":"seedjob","image":"kindest/node:v1.35.0","command":["sleep","600"],"volumeMounts":[{"name":"data","mountPath":"/seed"}]}],"volumes":[{"name":"data","persistentVolumeClaim":{"claimName":"family-tree-data"}}]}}'
kubectl -n prod wait --for=condition=ready pod/seedjob --timeout=120s
kubectl cp /tmp/ft-data-seed.tgz prod/seedjob:/tmp/seed.tgz
kubectl -n prod exec seedjob -- sh -c 'mkdir -p /seed/photos && tar -xzf /tmp/seed.tgz -C /seed && find /seed -name "._*" -delete'
kubectl -n prod delete pod seedjob
```

## 日常发版（prod，k8s 路线）

前置：备份生产 MySQL（k8s 里的 `prod/mysql-0`，不再 docker exec compose 容器）→
`./mvnw -B package` → 构建 `family-tree:vNN-local`（复用上版 Dockerfile）→ 镜像内预检。备份命令：

```bash
kubectl -n prod exec statefulset/mysql -- sh -c \
  'exec mysqldump --single-transaction --routines --triggers --events --set-gtid-purged=OFF \
   --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" family_tree' \
  > backup/pre-vNN-$(date +%Y%m%d_%H%M%S).sql
```

然后：

```bash
sed -i '' -E 's|image: family-tree:v[0-9]+-local|image: family-tree:vNN-local|' k8s/deployment.yaml
kubectl apply -f k8s/deployment.yaml
kubectl -n prod rollout status deployment/family-tree-app --timeout=420s
```

验证口径不变：`/login.html` 200、index gedcom 计数、`/api/auth/me` 401、隧道 200、
`kubectl -n prod logs deployment/family-tree-app` 0 ERROR。镜像修剪规则照旧（只留最近三代）。

## test 环境：按需拉起与回收（回归/实验专用，严禁打生产数据）

**拉起（首次或每次回归）：**

```bash
# 1. 起数据层（首次会因 MYSQL_DATABASE 自动建 family_tree_test 并授权 family_tree_app，无需口令嫁接）
kubectl -n test scale statefulset/mysql statefulset/redis --replicas=1
kubectl -n test wait --for=condition=ready pod/mysql-0 pod/redis-0 --timeout=300s
# 2. 灌数据（可选）：拿最新 prod 备份导入 family_tree_test（dump 不含 USE/mysql 库，直接进目标库）
kubectl -n test exec -i statefulset/mysql -- sh -c \
  'exec mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" family_tree_test' \
  < backup/pre-vNN-<时间戳>.sql
# 3. 照片（可选）：同 seedjob 方式挂 test/family-tree-data PVC 解 tar（见上文，-n 换成 test）
# 4. 改 test/deployment.yaml 的 image tag 为被测版本，起应用
kubectl apply -f k8s/test/deployment.yaml
kubectl -n test scale deployment/family-tree-app --replicas=1
# 5. 入口（二选一）：临时调试用 port-forward；稳定回归用 socat（8091，端口冲突时先停旧容器）
kubectl -n test port-forward svc/family-tree 8091:8090 &
#   或：docker run -d --name ft-test-proxy --network kind -p 127.0.0.1:8091:8091 \
#        alpine/socat TCP-LISTEN:8091,fork,reuseaddr TCP:desktop-control-plane:30091
#       （同理 3309→30309、6382→30382 按需加 mysql/redis 代理）
```

启动判定只用 HTTP 探活（boot 可到 3.5min）：`curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8091/login.html` == 200。
回归电池只打 8091/test 库；写测试产生的脏数据可整库重置：
`DROP DATABASE family_tree_test; CREATE DATABASE family_tree_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;` 后重灌备份。

**回收（用完必须）：**

```bash
kubectl -n test scale deployment/family-tree-app --replicas=0
kubectl -n test scale statefulset/mysql statefulset/redis --replicas=0
docker rm -f ft-test-proxy   # 如起过 socat
```

注意：test 与 prod 同开全负载时 VM 内存会顶到 limits 超卖上限——回归期间避免同时压 prod；
test redis 与 prod 独立，探针 key（`login:attempt:*`）互不可见，验证「连的是哪套」按 3309/6382 或 3308/6381 分开探。

## 数据迁移/回滚（prod 口径）

切换/回滚数据层都是 dump→restore，不用文件拷贝（kind 节点看不见宿主机目录，且版本一致性更稳）：

1. 冻结应用：`kubectl -n prod scale deploy family-tree-app --replicas=0`。
2. 从当前生效库 mysqldump（上面备份命令），存 `backup/`（gitignored）。
3. 目标库重建并导入：
   ```bash
   kubectl -n prod exec -i statefulset/mysql -- sh -c \
     'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "DROP DATABASE family_tree; CREATE DATABASE family_tree CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"'
   kubectl -n prod exec -i statefulset/mysql -- sh -c \
     'exec mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" family_tree' < backup/<dump>.sql
   ```
4. 校验：对目标库重新 mysqldump 后 `sed '$d' | md5sum`，与源 dump 同法比对（必须逐字节一致；
   最后一行 `-- Dump completed` 时间戳必须剔除，否则永远不相等）。
5. 切数据源：改 `k8s/configmap.yaml` 的 DB_URL/redis host 后 `kubectl apply`，**必须看到 `configured`
   而不是 `unchanged`**（unchanged=文件与集群同值，等于没切），再 scale 回 1 并等 rollout。
6. 验证：登录页 200、gedcom、`/api/admin/settings` 401、隧道 200；探针登录一次看 redis
   `login:attempt:*` 是否落在目标实例，`operation_log` 行数是否只增不减；用完删除探针 key。

回滚到 compose 数据栈：先把 prod 库 dump 回灌 compose mysql（docker exec family-tree-mysql 恢复，compose
口令同 `.env`），`docker start family-tree-mysql family-tree-redis family-tree-mysql-healthcheck`，
停 `ft-k8s-mysql-proxy`/`ft-k8s-redis-proxy`（否则抢不到 127.0.0.1:3308/6381），ConfigMap 改回
`host.docker.internal:3308/6381` 并 apply+restart app。**切记：切换后 compose 库是冻结副本，直接回切会丢
切换后的写入，必须先做第 1~4 步的反向同步。**

## 备份注意

- 生产库现在是 `prod/mysql-0`（PV 数据在 kind 节点 docker 卷里）；每日/发版备份用上文的 kubectl mysqldump。
- **照片备份仍从 prod PVC 导出**（compose 时代的 `./docker-data` 已冻结，不再随线上写入更新）：
  同 seedjob 方式挂 PVC 起 helper，`kubectl cp` 出 tar 放 `backup/`。
- 日志 PV 仅运行态，丢了不可惜；历史日志仍留在 `./docker-logs`。
- test 环境不备份（数据可随时从 prod dump 重建）。

## 已知 DD 坑（均已实测）

1. kind 1.37 + kubeadm v1beta3 初始化必败 → 固定 1.35.0。
2. `type: LoadBalancer` 的 cloud-provider 建 envoy 容器失败 → 用 `ft-k8s-proxy` socat。
3. 宿主打不通 NodePort/容器 IP → 同上，socat 容器在 `kind` 网络内按容器名转发（数据层同理用
   `ft-k8s-mysql-proxy`/`ft-k8s-redis-proxy`）。
4. distroless 无 shell：k8s 探针必须 httpGet；进容器看文件要用挂 PVC 的 helper pod。
5. **ConfigMap 修改后 `kubectl apply` 返回 `unchanged` 说明改的根本不是集群当前内容——数据源切换必须确认
   `configured` 并以 app 实际行为（redis 探针 key、目标库行数）为准，不能只看 rollout 成功。**
6. `login:attempt:*`、会话等 redis 写入只出现在应用真正连接的那套 redis；验证「切到哪」要探针到目标实例。
7. k8s 不支持 namespace 就地改名：prod/test 均按「建新 ns → 迁移数据 → 删旧 ns」流程得出；
   NodePort 全局唯一，删旧 Service 后才能给新 ns 复用同端口。
8. local-path PVC 建出来是 Pending（WaitForFirstConsumer），首次有 pod 调度才 Bound——test 休眠栈的
   PVC Pending 属正常，不是故障。
