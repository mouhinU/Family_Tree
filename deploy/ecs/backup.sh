#!/usr/bin/env bash
# 族谱管理系统 — ECS 每日数据库备份
#
# 与 deploy/scripts/backup.sh 的区别：那份面向宿主机装了 mysql 客户端的 compose 部署，
# 本份面向 ECS —— 宿主机没有 mysqldump，客户端在容器里，所以走 docker exec。
#
# 安装（ecs-setup.sh 部署完后手工执行一次）：
#   crontab -u root -e
#   0 3 * * * /opt/family-tree/backup.sh >> /var/log/family-tree-backup.log 2>&1
#
# 注意：备份文件与数据库在同一块盘（/dev/vda3）。只靠这个文件不等于有异地副本，
# 请另外在 ECS 控制台配自动快照策略，或把 backups 目录同步到 OSS。
set -euo pipefail

BACKUP_DIR=${BACKUP_DIR:-/opt/family-tree/backups}
RETENTION_DAYS=${RETENTION_DAYS:-7}
TS=$(date +%Y%m%d_%H%M%S)

mkdir -p "${BACKUP_DIR}"
umask 077

# --set-gtid-purged=OFF：dump 里不含 GTID 语句，同版本可直接回灌
docker exec family-tree-mysql sh -c \
  'exec mysqldump --single-transaction --routines --triggers --events --set-gtid-purged=OFF --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" family_tree' \
  2>/dev/null | gzip > "${BACKUP_DIR}/family_tree_${TS}.sql.gz"

OUT="${BACKUP_DIR}/family_tree_${TS}.sql.gz"
SIZE=$(du -h "${OUT}" | cut -f1)
TABLES=$(gzip -dc "${OUT}" | grep -c "^CREATE TABLE")
# 空备份比没备份更危险：gzip 成功但内容是空的，只有这里能发现
[ "${TABLES}" -gt 0 ] || { echo "[$(date '+%F %T')] 备份异常：CREATE TABLE 计数为 0"; exit 1; }
echo "[$(date '+%F %T')] 备份成功 family_tree_${TS}.sql.gz size=${SIZE} tables=${TABLES}"

find "${BACKUP_DIR}" -name "family_tree_*.sql.gz" -mtime +"${RETENTION_DAYS}" -delete
echo "[$(date '+%F %T')] 当前保留 $(ls -1 "${BACKUP_DIR}" | wc -l) 份（${RETENTION_DAYS} 天内）"
