#!/usr/bin/env bash
# ==========================================
# 族谱管理系统 — 应用重启脚本
#
# 安全重启应用容器，包含健康检查和自动回滚。
#
# 用法：
#   ./restart.sh              # 重启应用
#   ./restart.sh --force      # 强制重启（跳过健康检查）
# ==========================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
FORCE="${1:-}"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

cd "$PROJECT_DIR"

# 记录当前运行版本
CURRENT_IMAGE=$(docker inspect --format='{{.Config.Image}}' family-tree 2>/dev/null || echo "unknown")
log "当前镜像: ${CURRENT_IMAGE}"

# 重启
log "重启应用容器..."
docker compose restart family-tree

if [ "$FORCE" = "--force" ]; then
    log "强制重启完成（跳过健康检查）"
    exit 0
fi

# 等待启动
log "等待应用启动（最多 60 秒）..."
for i in $(seq 1 12); do
    sleep 5
    if bash "${SCRIPT_DIR}/healthcheck.sh" 2>/dev/null; then
        log "重启成功，应用已通过健康检查"
        exit 0
    fi
    log "  等待中... ($((i * 5))s)"
done

log "错误: 应用在 60 秒内未通过健康检查"
log "查看日志: docker compose logs --tail=50 family-tree"
log "回滚: ${SCRIPT_DIR}/rollback.sh <上一版本号>"
exit 1
