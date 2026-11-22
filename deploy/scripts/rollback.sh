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
# deploy/scripts/ 的上两级才是仓库根（compose 与 .env 都在那儿），原来只退一级指向 deploy/ 会找不到 .env
PROJECT_DIR="$(dirname "$(dirname "$SCRIPT_DIR")")"
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
# compose 里的两个应用服务分别挂在 --profile h2 与 --profile mysql 上，不带 profile 的 pull/up
# 一个容器都不会动。默认按生产（MySQL）形态走；H2 形态用 FT_COMPOSE_PROFILE=h2 ./rollback.sh <版本>
# （compose 原生认的 COMPOSE_PROFILES 也接受，与 restart.sh/stop.sh 用同一套变量）。
COMPOSE_PROFILE="${FT_COMPOSE_PROFILE:-${COMPOSE_PROFILES:-mysql}}"
# --profile h2 的应用服务是 family-tree；--profile mysql 的是 family-tree-mysql（容器名 family-tree-app）
APP_SERVICE=$([ "$COMPOSE_PROFILE" = "mysql" ] && echo "family-tree-mysql" || echo "family-tree")
docker compose --profile "$COMPOSE_PROFILE" pull
docker compose --profile "$COMPOSE_PROFILE" up -d

# 等待健康检查
log "等待应用启动..."
sleep 10

# 健康检查
if bash "${SCRIPT_DIR}/healthcheck.sh" 2>/dev/null; then
    log "回滚成功: ${CURRENT_VERSION:-latest} -> ${TARGET_VERSION}"
else
    log "警告: 应用未通过健康检查，可能需要手动检查"
    log "查看日志: docker compose --profile $COMPOSE_PROFILE logs --tail=50 $APP_SERVICE"
fi
