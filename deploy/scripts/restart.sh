#!/usr/bin/env bash
# ==========================================
# 族谱管理系统 — 应用重启脚本
#
# 安全重启族谱管理系统服务，支持 Docker 部署和本地 Java 进程两种方式，
# 重启后自动进行健康检查，失败时给出排查与回滚提示。
#
# 用法：
#   ./restart.sh             # 自动模式（Docker 优先，回退本地进程）
#   ./restart.sh --docker    # 仅重启 Docker Compose 启动的服务
#   ./restart.sh --local     # 仅重启本地 java -jar 启动的进程
#   ./restart.sh --force     # 强制重启（跳过健康检查，本地进程直接 kill）
#
# 退出码：
#   0 = 重启成功
#   1 = 重启失败或健康检查未通过
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
APP_URL="http://localhost:${APP_PORT}"
TIMEOUT="${STOP_TIMEOUT:-30}"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

# 等待健康检查通过，参数为最长等待秒数（每 5 秒探测一次）
wait_health() {
    local max_wait="${1:-60}"
    log "等待应用启动（最多 ${max_wait} 秒）..."
    local i=1
    while [ $((i * 5)) -le "$max_wait" ]; do
        sleep 5
        if bash "${SCRIPT_DIR}/healthcheck.sh" "$APP_URL" >/dev/null 2>&1; then
            log "重启成功，应用已通过健康检查"
            return 0
        fi
        log "  等待中... ($((i * 5))s)"
        i=$((i + 1))
    done
    return 1
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

# 重启 Docker Compose 服务
restart_docker() {
    if ! command -v docker >/dev/null 2>&1; then
        log "未安装 Docker，跳过容器重启"
        return 1
    fi

    if ! is_docker_running; then
        log "未发现运行中的族谱容器"
        return 1
    fi

    cd "$PROJECT_DIR"

    # 记录当前运行版本
    local current_image
    current_image=$(docker inspect --format='{{.Config.Image}}' family-tree 2>/dev/null || echo "unknown")
    log "当前镜像: ${current_image}"

    log "重启应用容器..."
    docker compose restart family-tree

    if [ "$FORCE" -eq 1 ]; then
        log "强制重启完成（跳过健康检查）"
        return 0
    fi

    if wait_health 60; then
        return 0
    fi

    log "错误: 应用在 60 秒内未通过健康检查"
    log "查看日志: docker compose logs --tail=50 family-tree"
    log "回滚: ${SCRIPT_DIR}/rollback.sh <上一版本号>"
    return 1
}

# 查找本地监听应用端口的进程
find_local_pid() {
    lsof -ti tcp:"$APP_PORT" -sTCP:LISTEN 2>/dev/null | head -n 1
}

# 优雅停止本地进程，超时后强制终止；参数为 PID
stop_local_pid() {
    local pid="$1"
    if [ "$FORCE" -eq 1 ]; then
        kill -9 "$pid"
        sleep 1
        return 0
    fi

    log "发送 SIGTERM，等待优雅退出（最多 ${TIMEOUT}s）..."
    kill "$pid"
    local i
    for i in $(seq 1 "$TIMEOUT"); do
        if ! kill -0 "$pid" 2>/dev/null; then
            log "旧进程已退出（耗时 ${i}s）"
            return 0
        fi
        sleep 1
    done

    log "警告: 进程在 ${TIMEOUT}s 内未退出，强制终止"
    kill -9 "$pid"
    sleep 1
    if kill -0 "$pid" 2>/dev/null; then
        log "错误: 无法终止进程 ${pid}，请手动处理"
        return 1
    fi
    return 0
}

# 重启本地 Java 进程：记录原启动命令与工作目录后停止，再以相同命令后台拉起
restart_local() {
    local pid
    pid=$(find_local_pid || true)
    if [ -z "$pid" ]; then
        log "错误: 未发现监听 ${APP_PORT} 端口的本地进程，无法重启"
        return 1
    fi

    # 记录启动命令与原工作目录，用于重新拉起
    local launch_cmd
    launch_cmd=$(ps -o command= -p "$pid" | head -n 1)
    local work_dir
    work_dir=$(lsof -a -p "$pid" -d cwd -Fn 2>/dev/null | grep '^n' | cut -c2- | head -n 1)
    log "发现本地进程 PID=${pid}"
    log "启动命令: ${launch_cmd}"

    if ! stop_local_pid "$pid"; then
        return 1
    fi

    # 在原工作目录下后台重启，日志追加到 logs/restart.log
    if [ -z "$work_dir" ] || [ ! -d "$work_dir" ]; then
        work_dir="$PROJECT_DIR"
    fi
    mkdir -p "${work_dir}/logs"
    log "在 ${work_dir} 下重新启动应用..."
    nohup bash -c "cd '$work_dir' && exec $launch_cmd" >> "${work_dir}/logs/restart.log" 2>&1 &
    log "新进程已在后台启动（PID=$!）"

    if [ "$FORCE" -eq 1 ]; then
        log "强制重启完成（跳过健康检查）"
        return 0
    fi

    if wait_health 90; then
        return 0
    fi

    log "错误: 应用在 90 秒内未通过健康检查"
    log "查看日志: tail -n 50 ${work_dir}/logs/restart.log"
    return 1
}

case "$MODE" in
    --docker)
        restart_docker
        ;;
    --local)
        restart_local
        ;;
    auto)
        if restart_docker; then
            exit 0
        fi
        restart_local
        ;;
    *)
        log "错误: 未知参数 ${MODE}"
        echo "用法: $0 [--docker|--local|--force]"
        exit 1
        ;;
esac
