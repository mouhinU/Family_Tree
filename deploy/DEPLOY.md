# 族谱管理系统 — 部署指南

## 环境要求

服务器最低配置：2 核 CPU / 2GB 内存 / 20GB 磁盘。推荐配置：4 核 / 4GB / 50GB SSD。

软件依赖：Docker 24+、Docker Compose v2+。生产模式额外需要域名和 SSL 证书（可用 Let's Encrypt 免费申请）。

---

## 一、快速体验（H2 内嵌数据库）

适合个人使用或数据量较小的场景，无需额外安装数据库。

```bash
# 1. 创建部署目录
mkdir -p /opt/family-tree && cd /opt/family-tree

# 2. 下载 docker-compose.yml
curl -O https://raw.githubusercontent.com/mouhinU/Family_Tree/main/docker-compose.yml

# 3. 启动
docker compose up -d

# 4. 访问 http://your-server-ip:8090
```

数据存储在 Docker 卷 `family-tree-data` 中。如需持久化到宿主机，可在 docker-compose.yml 中将 `family-tree-data` 改为 bind
mount（如 `./data:/app/data`）。

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

# 使用 MySQL 模式
SPRING_PROFILES_ACTIVE=mysql

# MySQL 配置（请修改为强密码）
MYSQL_ROOT_PASSWORD=StrongRootP@ssw0rd
MYSQL_DATABASE=family_tree
DB_USERNAME=family_tree_app
DB_PASSWORD=StrongAppP@ssw0rd
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

# 拉取新镜像并重启
docker compose pull
docker compose up -d
```

### 3.2 回滚版本

```bash
# 方式一：使用回滚脚本
chmod +x deploy/scripts/rollback.sh
./deploy/scripts/rollback.sh 1.0.0

# 方式二：手动操作
sed -i 's/IMAGE_TAG=.*/IMAGE_TAG=1.0.0/' .env
docker compose pull
docker compose up -d
```

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
