#!/usr/bin/env bash
# ==========================================
# 族谱管理系统 — 版本回滚脚本
#
# 将应用回滚到指定的历史版本。
#
# 用法：
#   ./rollback.sh 1.0.0        # 回滚到 v1.0.0
#   ./rollback.sh latest        # 切换到 latest
#
# 原理：修改 .env 中的 IMAGE_TAG，然后 docker compose up -d 拉取对应版本。
# ==========================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
ENV_FILE="${PROJECT_DIR}/.env"

TARGET_VERSION="${1:?请指定目标版本，如: ./rollback.sh 1.0.0}"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

# 检查 .env 文件
if [ ! -f "$ENV_FILE" ]; then
    log "错误: .env 文件不存在: $ENV_FILE"
    log "请先从 .env.example 复制并配置"
    exit 1
fi

# 记录当前版本
CURRENT_VERSION=$(grep "^IMAGE_TAG=" "$ENV_FILE" 2>/dev/null | cut -d= -f2 || echo "latest")
log "当前版本: ${CURRENT_VERSION:-latest}"
log "目标版本: ${TARGET_VERSION}"

if [ "${CURRENT_VERSION:-latest}" = "$TARGET_VERSION" ]; then
    log "当前已是目标版本，无需回滚"
    exit 0
fi

# 更新 .env 中的 IMAGE_TAG
if grep -q "^IMAGE_TAG=" "$ENV_FILE"; then
    sed -i.bak "s/^IMAGE_TAG=.*/IMAGE_TAG=${TARGET_VERSION}/" "$ENV_FILE"
else
    echo "IMAGE_TAG=${TARGET_VERSION}" >> "$ENV_FILE"
fi
rm -f "${ENV_FILE}.bak"

log "已更新 .env: IMAGE_TAG=${TARGET_VERSION}"

# 重新部署
log "拉取新版本镜像并重启..."
cd "$PROJECT_DIR"
docker compose pull
docker compose up -d

# 等待健康检查
log "等待应用启动..."
sleep 10

# 健康检查
if bash "${SCRIPT_DIR}/healthcheck.sh" 2>/dev/null; then
    log "回滚成功: ${CURRENT_VERSION:-latest} -> ${TARGET_VERSION}"
else
    log "警告: 应用未通过健康检查，可能需要手动检查"
    log "回滚命令: docker compose logs family-tree"
fi
