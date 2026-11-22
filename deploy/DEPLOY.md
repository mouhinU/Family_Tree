# 族谱管理系统 — 部署指南

## 环境要求

服务器最低配置：2 核 CPU / 2GB 内存 / 20GB 磁盘。推荐配置：4 核 / 4GB / 50GB SSD。

软件依赖：Docker 24+、Docker Compose v2+。生产模式额外需要域名和 SSL 证书（可用 Let's Encrypt 免费申请）。

> 「2GB 内存」是理论下限：本文件第二节沿用根 `docker-compose.yml` 的默认配额（app 1536m + mysql 1024m + redis 128m = 2688m），在标称 2GiB 的机型上会被 OOM 杀掉（阿里云 `ecs.e-c1m1.large` 实测可用 1870MiB）。
> 这类小机器请用 [`deploy/ecs/`](ecs/README.md)（配额 768/512/96 + 分阶段部署脚本）。

---

## 一、快速体验（H2 内嵌数据库）

适合个人使用或数据量较小的场景，无需外部数据库进程。

```bash
# 1. 创建部署目录
mkdir -p /opt/family-tree && cd /opt/family-tree

# 2. 下载 docker-compose.yml
curl -O https://raw.githubusercontent.com/mouhinU/Family_Tree/main/docker-compose.yml

# 3. 启动（H2 模式挂在 compose profile 上，必须带 --profile h2；裸 `up -d` 一个容器都不会起）
docker compose --profile h2 up -d

# 4. 访问 http://your-server-ip:8090
```

启动的是三个容器：`family-tree`（应用）、`family-tree-redis`、`family-tree-healthcheck`。MySQL 模式起的应用容器名是 `family-tree-app`，两套模式互斥，别同时起（8090 端口会撞）。

- redis 不是可选的：会话外置（`spring-boot-starter-session-data-redis`）与登录失败锁定都落在它上面。
  实测没有 redis 时应用照样启动、`/actuator/health` 照样 200（Lettuce 懒连接），但登录会卡约 60s 后返回
  `{"code":500,"message":"系统内部错误"}`（HTTP 状态码仍是 200，别看状态码判断）。
- 数据库形态由 compose 里各服务硬编码的 `SPRING_PROFILES_ACTIVE` 决定，**不要**在 `.env` 里设这个变量。
  历史坑：以前 `.env.example` 给的是 `SPRING_PROFILES_ACTIVE=prod`，而 prod 的数据源是 MySQL 且没有 redis 地址，
  于是这一节「H2 快速体验」从来没真正走过 H2（填了还会登录即挂）。
- H2 库文件在容器内 `/app/data/family_tree.mv.db`，落在 `family-tree-data` 卷里（宿主上是
  `<项目名>_family-tree-data`）。如需持久化到宿主机，把该卷改成 bind mount（如 `./data:/app/data`）；
  `docker compose down -v` 会删命名卷，改动前先导数据。

---

## 二、生产部署（MySQL）

### 2.1 准备环境

```bash
mkdir -p /opt/family-tree && cd /opt/family-tree

# 下载必要文件
curl -O https://raw.githubusercontent.com/mouhinU/Family_Tree/main/docker-compose.yml
curl -O https://raw.githubusercontent.com/mouhinU/Family_Tree/main/.env.example
cp .env.example .env
```

### 2.2 配置环境变量

编辑 `.env` 文件：

```bash
# 锁定镜像版本（重要！避免 latest 导致意外升级）
IMAGE_TAG=1.0.0

# 这里不要设 SPRING_PROFILES_ACTIVE：两个应用服务各在自己的 compose 块里写死了
# （--profile h2 → SPRING_PROFILES_ACTIVE=h2，--profile mysql → =mysql），
# 它属于服务定义而不是运行时变量；保留这一行只会让人以为改它能换数据库。
# （根 .env.example 里的同名行已删除，理由见第一节的历史坑。）

# MySQL 配置（请修改为强密码）
MYSQL_ROOT_PASSWORD=StrongRootP@ssw0rd
MYSQL_DATABASE=family_tree
DB_USERNAME=family_tree_app
DB_PASSWORD=StrongAppP@ssw0rd

# 平台管理员白名单：逗号分隔的登录用户名，与家族角色（OWNER/ADMIN/MEMBER）无关
# 为空时 /admin.html 入口隐藏且 /api/admin/** 全部拒绝；修改后需重启应用容器
PLATFORM_ADMIN_USERNAMES=admin

# 家族相册根目录：必须落在 family-tree-data 卷内（默认 /app/data/photos 已满足），
# 实际布局 {dir}/{familyId}/{userId}_{uuid}.{ext}，按家族分目录便于容量统计与归档清理
FAMILY_TREE_PHOTO_DIR=/app/data/photos
```

### 2.3 启动服务

```bash
docker compose --profile mysql up -d
```

验证所有容器正常运行：

```bash
docker compose ps
# 确认 family-tree-app 和 family-tree-mysql 状态为 running (healthy)
```

### 2.4 配置 Nginx 反向代理

生产环境不应直接暴露 8090 端口。下载 Nginx 配置模板：

```bash
curl -O https://raw.githubusercontent.com/mouhinU/Family_Tree/main/deploy/nginx/family-tree.conf
sudo cp family-tree.conf /etc/nginx/conf.d/
```

编辑配置文件，替换以下变量：

- `${DOMAIN}` → 你的域名（如 `tree.example.com`）
- `${SSL_CERT_PATH}` → SSL 证书路径
- `${SSL_KEY_PATH}` → SSL 私钥路径

申请 SSL 证书（Let's Encrypt）：

```bash
# 安装 certbot
sudo apt install certbot python3-certbot-nginx

# 申请证书
sudo certbot --nginx -d tree.example.com
```

验证并重载 Nginx：

```bash
sudo nginx -t && sudo systemctl reload nginx
```

---

## 三、版本管理与回滚

### 3.1 升级版本

```bash
# 修改 .env 中的 IMAGE_TAG
sed -i 's/IMAGE_TAG=.*/IMAGE_TAG=1.1.0/' .env

# 拉取新镜像并重启（--profile 要和部署形态一致，不带的话两个应用服务都不在启用集合里，命令空转）
docker compose --profile mysql pull
docker compose --profile mysql up -d
```

### 3.2 回滚版本

```bash
# 方式一：使用回滚脚本（默认按 MySQL 形态执行 pull/up；H2 形态用 FT_COMPOSE_PROFILE=h2 前缀）
chmod +x deploy/scripts/rollback.sh
./deploy/scripts/rollback.sh 1.0.0

# 方式二：手动操作
sed -i 's/IMAGE_TAG=.*/IMAGE_TAG=1.0.0/' .env
docker compose --profile mysql pull
docker compose --profile mysql up -d
```

> `deploy/scripts/` 下的 `restart.sh` / `stop.sh` / `rollback.sh` 同样按 profile 编排，默认 MySQL 形态；
> 换 H2 形态时加 `FT_COMPOSE_PROFILE=h2`（compose 原生变量 `COMPOSE_PROFILES` 也认）。

---

## 四、数据库备份与恢复

### 4.1 定时备份

```bash
chmod +x deploy/scripts/backup.sh

# MySQL 备份
./deploy/scripts/backup.sh mysql /backup/family-tree

# 添加 crontab 定时任务（每天凌晨 3 点）
echo "0 3 * * * cd /opt/family-tree && ./deploy/scripts/backup.sh mysql /backup/family-tree >> /var/log/family-tree-backup.log 2>&1" | sudo crontab -
```

### 4.2 恢复数据

```bash
chmod +x deploy/scripts/restore.sh

# 恢复 MySQL（会覆盖现有数据，请确认！）
./deploy/scripts/restore.sh mysql /backup/family-tree/mysql_family_tree_20260823_030000.sql.gz

# 恢复 H2
./deploy/scripts/restore.sh h2 /backup/family-tree/h2_20260823_030000.zip
```

---

## 五、监控与告警

### 5.1 启用 Prometheus + Grafana

```bash
docker compose --profile mysql --profile monitoring up -d
```

- Prometheus: `http://your-server:9090`（建议通过 Nginx 限制访问）
- Grafana: `http://your-server:3000`（默认账号 admin/admin，首次登录请修改密码）

Grafana 已预配置 Prometheus 数据源和 Family Tree 监控面板，包含 JVM 内存、GC 暂停、HTTP 请求速率/延迟、缓存命中率等指标。

### 5.2 Actuator 端点

应用暴露以下管理端点：

- `/actuator/health` — 健康状态
- `/actuator/info` — 应用信息
- `/actuator/metrics` — 指标列表
- `/actuator/prometheus` — Prometheus 格式指标

Nginx 配置已限制 `/actuator/` 仅允许内网 IP 访问（127.0.0.1、10.0.0.0/8、172.16.0.0/12、192.168.0.0/16）。

---

## 六、运维脚本

所有脚本位于 `deploy/scripts/` 目录：

| 脚本                | 用途    | 用法                                |
|-------------------|-------|-----------------------------------|
| `backup.sh`       | 数据库备份 | `./backup.sh mysql [备份目录]`        |
| `restore.sh`      | 数据库恢复 | `./restore.sh mysql <备份文件>`       |
| `healthcheck.sh`  | 健康检查  | `./healthcheck.sh [应用地址]`         |
| `cleanup-logs.sh` | 日志清理  | `./cleanup-logs.sh [保留天数] [日志目录]` |
| `restart.sh`      | 安全重启  | `./restart.sh [--force]`          |
| `rollback.sh`     | 版本回滚  | `./rollback.sh <版本号>`             |

---

## 七、日志管理

### 7.1 查看日志

```bash
# 应用日志
docker compose logs -f family-tree

# MySQL 日志
docker compose logs -f mysql

# 最近 100 行
docker compose logs --tail=100 family-tree
```

### 7.2 日志文件位置

应用日志写入 Docker 卷 `family-tree-logs`，包含：

- `family-tree.log` — 全量日志（INFO 及以上）
- `family-tree-error.log` — 仅错误日志

日志按日滚动，保留 30 天，总大小上限 1GB。

### 7.3 清理日志

```bash
# 清理超过 7 天的日志
./deploy/scripts/cleanup-logs.sh 7
```

Docker 容器日志已通过 json-file 驱动配置了自动轮转（50MB/文件，最多 5 个文件）。

---

## 八、安全清单

部署上线前请逐项确认：

1. MySQL 密码使用强密码（16 位以上，含大小写+数字+特殊字符）
2. `.env` 文件权限设为 600（`chmod 600 .env`）
3. Nginx 已配置 HTTPS，HTTP 自动跳转 HTTPS
4. H2 Console 在生产环境已禁用（prod/mysql profile 默认禁用）
5. Actuator 端点仅内网可访问（Nginx 配置中已限制）
6. 数据库备份已配置定时任务
7. 镜像版本已锁定（.env 中 `IMAGE_TAG` 不使用 `latest`）
8. 防火墙仅开放 80、443 端口

---

## 九、阿里云 ECS 单机部署（deploy/ecs）

面向 2 vCPU / 2GiB 的小机型，与本文第二节的差别集中在三处，其余口径一致：

| 维度 | 本文第二节（根 compose） | `deploy/ecs/` |
|---|---|---|
| 内存配额 | app 1536m / mysql 1024m / redis 128m | app 768m / mysql 512m / redis 96m，另关 performance_schema、限 redis maxmemory |
| 数据持久化 | 命名卷（`family-tree-data` 等），`docker compose down -v` 会删掉 | 全部宿主 bind 挂载 `/opt/family-tree/data/{mysql,redis,photos}` + `logs`，`down -v` 毁不掉；命名卷老环境用 `ecs-setup.sh tobind` 一次性搬迁 |
| 镜像来源 | `ghcr.io` + `IMAGE_TAG` | 本机 `docker save` → 上传 → `docker load`，`.env` 里 `FT_IMAGE` 锁定 tag |
| 暴露方式 | 域名 + HTTPS + 301 | 公网 IP + 明文 HTTP（备案接入商不是阿里云，80/443 被网关 403 拦截） |
| Spring profile | `prod`（cookie `Secure` + `SameSite=Strict`） | `mysql,ecs` → 新增的 `application-ecs.yml`（`secure: false` + `SameSite=Lax` + Tomcat/Hikari 小配额 + stdout 日志） |

部署与升级步骤、备份口径、Workbench CLI 与 distroless 的坑，见 [`deploy/ecs/README.md`](ecs/README.md)；
已有环境换镜像的一轮完整动作（预检读数 → 备份校验 → 容器网络自检 → 构建导入 → repair 判定 → 切换 → 验收电池 → 回滚）见 [`deploy/ecs/RELEASE-SOP.md`](ecs/RELEASE-SOP.md)。

对第八节安全清单的两处例外（当前状态，非建议做法）：

- 第 3 项「Nginx 已配置 HTTPS」：`deploy/ecs/nginx-family-tree.conf` 只有 HTTP，且刻意不下发 HSTS。也因此不能用 `prod` profile —— 实测它在明文 HTTP 下仍下发 `Set-Cookie: ...; Secure`，浏览器不再回传会话，表现为「登录成功、下一个请求就未登录」；改用 `mysql,ecs`。
- 第 8 项「仅开放 80、443」：ECS 上 3308/6381 只绑 `127.0.0.1`，公网不可达，等价满足。

