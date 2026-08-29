#!/usr/bin/env bash
# ==========================================
# 族谱管理系统 — 服务关闭脚本
#
# 停止族谱管理系统服务，支持 Docker 部署和本地 Java 进程两种方式。
# 默认先尝试停止 Docker 容器，未找到容器时回退到查找本地 Java 进程。
#
# 用法：
#   ./stop.sh              # 优雅停止服务（Docker 优先，回退本地进程）
#   ./stop.sh --docker     # 仅停止 Docker Compose 启动的服务
#   ./stop.sh --local      # 仅停止本地 java -jar 启动的进程
#   ./stop.sh --force      # 强制停止（直接 kill，跳过优雅停机）
#
# 退出码：
#   0 = 服务已停止或本就未运行
#   1 = 停止失败
# ==========================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$(dirname "$SCRIPT_DIR")")"
MODE="${1:-auto}"
FORCE=0

if [ "$MODE" = "--force" ]; then
    FORCE=1
    MODE="auto"
fi

APP_PORT="${APP_PORT:-8090}"
TIMEOUT="${STOP_TIMEOUT:-30}"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

# 检查 Docker 容器是否在运行（任一容器存在即视为运行）
is_docker_running() {
    for name in family-tree family-tree-app; do
        if docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "$name"; then
            return 0
        fi
    done
    return 1
}

# 停止 Docker Compose 服务
stop_docker() {
    if ! command -v docker >/dev/null 2>&1; then
        log "未安装 Docker，跳过容器停止"
        return 1
    fi

    if ! is_docker_running; then
        log "未发现运行中的族谱容器"
        return 1
    fi

    cd "$PROJECT_DIR"
    if [ "$FORCE" -eq 1 ]; then
        log "强制停止 Docker 服务..."
        docker compose --profile mysql kill
    else
        log "优雅停止 Docker 服务（超时 ${TIMEOUT}s）..."
        docker compose --profile mysql stop -t "$TIMEOUT"
    fi
    log "Docker 服务已停止"
    return 0
}

# 查找本地监听应用端口的 Java 进程 PID
find_local_pid() {
    lsof -ti tcp:"$APP_PORT" -sTCP:LISTEN 2>/dev/null | head -n 1
}

# 停止本地 Java 进程
stop_local() {
    PID=$(find_local_pid || true)
    if [ -z "$PID" ]; then
        log "未发现监听 ${APP_PORT} 端口的本地进程"
        return 1
    fi

    log "发现本地进程 PID=${PID}"
    if [ "$FORCE" -eq 1 ]; then
        log "强制终止进程..."
        kill -9 "$PID"
        log "进程已强制终止"
        return 0
    fi

    log "发送 SIGTERM，等待优雅退出（最多 ${TIMEOUT}s）..."
    kill "$PID"
    for i in $(seq 1 "$TIMEOUT"); do
        if ! kill -0 "$PID" 2>/dev/null; then
            log "进程已退出（耗时 ${i}s）"
            return 0
        fi
        sleep 1
    done

    log "警告: 进程在 ${TIMEOUT}s 内未退出，强制终止"
    kill -9 "$PID"
    sleep 1
    if kill -0 "$PID" 2>/dev/null; then
        log "错误: 无法终止进程 ${PID}，请手动处理"
        return 1
    fi
    log "进程已强制终止"
    return 0
}

case "$MODE" in
    --docker)
        stop_docker
        ;;
    --local)
        stop_local
        ;;
    auto)
        if stop_docker; then
            exit 0
        fi
        stop_local
        ;;
    *)
        log "错误: 未知参数 ${MODE}"
        echo "用法: $0 [--docker|--local|--force]"
        exit 1
        ;;
esac

# 最终确认
if is_docker_running || [ -n "$(find_local_pid || true)" ]; then
    log "错误: 服务似乎仍在运行，请手动检查"
    exit 1
fi
log "服务关闭完成"
