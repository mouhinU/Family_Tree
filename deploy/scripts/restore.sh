#!/usr/bin/env bash
# ==========================================
# 族谱管理系统 — 数据库恢复脚本
#
# 用法：
#   ./restore.sh mysql /backup/family-tree/mysql_family_tree_20260823_030000.sql.gz
#   ./restore.sh h2 /backup/family-tree/h2_20260823_030000.zip
#
# 注意：恢复操作会覆盖现有数据，请提前备份当前数据！
# ==========================================
set -euo pipefail

# ========== 配置 ==========
MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_DB="${MYSQL_DATABASE:-family_tree}"
MYSQL_USER="${DB_USERNAME:-family_tree_app}"
MYSQL_PASS="${DB_PASSWORD:-}"
H2_DATA_DIR="${H2_DATA_DIR:-/app/data}"

# ========== 函数 ==========
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

restore_mysql() {
    local backup_file="$1"

    if [ ! -f "$backup_file" ]; then
        log "错误: 备份文件不存在: $backup_file"
        exit 1
    fi

    log "警告: 即将恢复 MySQL 数据库 ${MYSQL_DB}，现有数据将被覆盖！"
    read -r -p "确认恢复? (yes/no): " confirm
    if [ "$confirm" != "yes" ]; then
        log "已取消恢复操作"
        exit 0
    fi

    local mysql_args="-h${MYSQL_HOST} -P${MYSQL_PORT} -u${MYSQL_USER}"
    if [ -n "$MYSQL_PASS" ]; then
        mysql_args="${mysql_args} -p${MYSQL_PASS}"
    fi

    log "开始恢复 MySQL 数据库: ${backup_file}"

    # 解压并导入
    if [[ "$backup_file" == *.gz ]]; then
        gunzip -c "$backup_file" | mysql $mysql_args "$MYSQL_DB"
    else
        mysql $mysql_args "$MYSQL_DB" < "$backup_file"
    fi

    log "MySQL 恢复完成"
}

restore_h2() {
    local backup_file="$1"

    if [ ! -f "$backup_file" ]; then
        log "错误: 备份文件不存在: $backup_file"
        exit 1
    fi

    log "警告: 即将恢复 H2 数据目录 ${H2_DATA_DIR}，现有数据将被覆盖！"
    read -r -p "确认恢复? (yes/no): " confirm
    if [ "$confirm" != "yes" ]; then
        log "已取消恢复操作"
        exit 0
    fi

    log "停止应用以释放 H2 文件锁..."
    log "请确保应用已停止，然后按回车继续..."
    read -r

    # 备份当前数据（以防万一）
    if [ -d "$H2_DATA_DIR" ]; then
        local pre_restore="${H2_DATA_DIR}_pre_restore_$(date +%Y%m%d_%H%M%S)"
        mv "$H2_DATA_DIR" "$pre_restore"
        log "已备份当前数据到: $pre_restore"
    fi

    mkdir -p "$H2_DATA_DIR"
    unzip -o "$backup_file" -d "$H2_DATA_DIR"

    log "H2 恢复完成: $H2_DATA_DIR"
}

# ========== 主流程 ==========
case "${1:-}" in
    mysql)
        restore_mysql "${2:?请提供备份文件路径}"
        ;;
    h2)
        restore_h2 "${2:?请提供备份文件路径}"
        ;;
    *)
        echo "用法: $0 {mysql|h2} <备份文件路径>"
        echo ""
        echo "示例:"
        echo "  $0 mysql /backup/family-tree/mysql_family_tree_20260823_030000.sql.gz"
        echo "  $0 h2 /backup/family-tree/h2_20260823_030000.zip"
        exit 1
        ;;
esac

log "恢复任务完成"
