#!/usr/bin/env bash
# ==========================================
# 族谱管理系统 — 数据库备份脚本
#
# 用法：
#   ./backup.sh              # 交互式选择 H2 或 MySQL
#   ./backup.sh mysql        # 备份 MySQL
#   ./backup.sh h2           # 备份 H2 文件
#   ./backup.sh mysql /path  # 指定备份目录
#
# 定时备份（每天凌晨 3 点）：
#   0 3 * * * /path/to/backup.sh mysql /backup/db >> /var/log/family-tree-backup.log 2>&1
# ==========================================
set -euo pipefail

# ========== 配置 ==========
BACKUP_DIR="${2:-/backup/family-tree}"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RETENTION_DAYS=30

# MySQL 连接参数（从环境变量或默认值）
MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_DB="${MYSQL_DATABASE:-family_tree}"
MYSQL_USER="${DB_USERNAME:-family_tree_app}"
MYSQL_PASS="${DB_PASSWORD:-}"

# H2 数据文件路径
H2_DATA_DIR="${H2_DATA_DIR:-/app/data}"

# ========== 函数 ==========
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

ensure_dir() {
    if [ ! -d "$BACKUP_DIR" ]; then
        mkdir -p "$BACKUP_DIR"
        log "创建备份目录: $BACKUP_DIR"
    fi
}

cleanup_old_backups() {
    log "清理 ${RETENTION_DAYS} 天前的备份文件..."
    find "$BACKUP_DIR" -name "*.sql.gz" -o -name "*.h2-backup.zip" | while read -r f; do
        if [ "$(find "$f" -mtime +${RETENTION_DAYS} -print 2>/dev/null)" ]; then
            rm -f "$f"
            log "已删除过期备份: $f"
        fi
    done
}

backup_mysql() {
    local dump_file="${BACKUP_DIR}/mysql_${MYSQL_DB}_${TIMESTAMP}.sql"

    log "开始备份 MySQL 数据库: ${MYSQL_DB}@${MYSQL_HOST}:${MYSQL_PORT}"

    local mysql_args="-h${MYSQL_HOST} -P${MYSQL_PORT} -u${MYSQL_USER}"
    if [ -n "$MYSQL_PASS" ]; then
        mysql_args="${mysql_args} -p${MYSQL_PASS}"
    fi

    # 导出（含存储过程、触发器、数据）
    mysqldump $mysql_args \
        --single-transaction \
        --routines \
        --triggers \
        --set-gtid-purged=OFF \
        --default-character-set=utf8mb4 \
        "$MYSQL_DB" > "$dump_file"

    # 压缩
    gzip "$dump_file"
    log "MySQL 备份完成: ${dump_file}.gz ($(du -h "${dump_file}.gz" | cut -f1))"
}

backup_h2() {
    local zip_file="${BACKUP_DIR}/h2_${TIMESTAMP}.zip"

    log "开始备份 H2 数据文件: ${H2_DATA_DIR}"

    if [ ! -d "$H2_DATA_DIR" ]; then
        log "错误: H2 数据目录不存在: $H2_DATA_DIR"
        exit 1
    fi

    # 打包 H2 数据库文件
    (cd "$H2_DATA_DIR" && zip -r "$zip_file" . -x "*.lock.db" "*.trace.db" "*.log.db")
    log "H2 备份完成: ${zip_file} ($(du -h "$zip_file" | cut -f1))"
}

# ========== 主流程 ==========
ensure_dir

case "${1:-}" in
    mysql)
        backup_mysql
        ;;
    h2)
        backup_h2
        ;;
    "")
        echo "用法: $0 {mysql|h2} [备份目录]"
        echo ""
        echo "示例:"
        echo "  $0 mysql              # 备份 MySQL 到默认目录"
        echo "  $0 h2 /backup/h2      # 备份 H2 到指定目录"
        exit 1
        ;;
    *)
        log "未知参数: $1"
        exit 1
        ;;
esac

cleanup_old_backups
log "备份任务完成"
