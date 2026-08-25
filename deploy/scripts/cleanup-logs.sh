#!/usr/bin/env bash
# ==========================================
# 族谱管理系统 — 日志清理脚本
#
# 清理 Docker 容器日志和应用日志文件。
# Docker 日志已在 docker-compose.yml 中配置了 json-file 轮转，
# 本脚本用于清理意外膨胀的日志文件。
#
# 用法：
#   ./cleanup-logs.sh              # 清理超过 7 天的日志
#   ./cleanup-logs.sh 30           # 清理超过 30 天的日志
#   ./cleanup-logs.sh 7 /app/logs  # 指定日志目录
# ==========================================
set -euo pipefail

RETENTION_DAYS="${1:-7}"
LOG_DIR="${2:-/app/logs}"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

# 清理应用日志文件
if [ -d "$LOG_DIR" ]; then
    log "清理 ${RETENTION_DAYS} 天前的应用日志: ${LOG_DIR}"
    find "$LOG_DIR" -name "*.log" -mtime +"$RETENTION_DAYS" -print -delete 2>/dev/null | while read -r f; do
        log "  已删除: $f"
    done
    find "$LOG_DIR" -name "*.log.gz" -mtime +"$RETENTION_DAYS" -print -delete 2>/dev/null | while read -r f; do
        log "  已删除: $f"
    done
else
    log "日志目录不存在: $LOG_DIR"
fi

# 清理 Docker 容器日志（截断当前日志文件）
if command -v docker &>/dev/null; then
    log "清理 Docker 容器日志..."
    for container in family-tree family-tree-mysql family-tree-grafana family-tree-prometheus; do
        if docker ps -a --format '{{.Names}}' | grep -q "^${container}$"; then
            LOG_PATH=$(docker inspect --format='{{.LogPath}}' "$container" 2>/dev/null || echo "")
            if [ -n "$LOG_PATH" ] && [ -f "$LOG_PATH" ]; then
                SIZE_BEFORE=$(du -h "$LOG_PATH" | cut -f1)
                # 截断日志文件（不删除，避免 Docker 持有文件句柄问题）
                truncate -s 0 "$LOG_PATH" 2>/dev/null || true
                log "  ${container}: ${SIZE_BEFORE} -> 0"
            fi
        fi
    done
fi

log "日志清理完成"
