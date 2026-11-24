#!/bin/sh
# ft-sentinel — 发版/重启后的应用→数据面连通性验收
#
# 检查三件事，任一失败即退出码 1：
#   1) 应用能不能建到 mysql / redis 的 TCP（firewalld 把网桥踢出 trusted 时症状是「ICMP 通、
#      TCP 被 reject」，HTTP 接口看着正常但登录会卡 ~30-60s 后 500）
#   2) /actuator/health 是否 200（DOWN 时 nginx 反代会挂 502/504）
#   3) 登录接口是否在 25s 内返回并给出 HTTP 码（假凭据的 401/400/业务码 500 都算正常）
#
# **形态自适应**（2026-11-24 k3s 接管后必须自适应，否则在健康的生产上误报 FAIL）：
#   compose 形态（app 容器 Running）：app 在独立 bridge 网络，必须 nsenter 进 app 的 netns 量；
#     宿主上另起临时容器属于另一个网络，ARP 都不通，量出来是假阳性。地址按容器名现查。
#   k3s 形态（compose app 已退役）：pod 是 hostNetwork，它的 netns 就是宿主，所以直接从宿主量
#     127.0.0.1 的发布端口（mysql 3308 / redis 6381）；应用端口取 nginx 反代实际指向的 8091/8092，
#     不写死槽位（蓝绿在任会随发版交替）。
#
# 历史坑（本次修掉的两个假通过）：
#   - 容器已停时 `docker inspect -f {{.State.Pid}}` 返回 **0**，`nsenter -t 0 -n` 等于在宿主跑探针，
#     compose 网络被拦也照样「OK」→ 现在按 State.Running 判形态，且 compose 模式要求 Pid 非 0。
#   - 连接被拒时 curl 立刻返回且 http_code 为空/000，旧脚本只量耗时，于是把「根本没连上」判成
#     「0s 返回，OK」→ 现在必须同时拿到非 000 的 HTTP 码。
set -u

FAIL=0
APP_CTR=${APP_CTR:-family-tree-app}
MYSQL_CTR=${MYSQL_CTR:-family-tree-mysql}
REDIS_CTR=${REDIS_CTR:-family-tree-redis}
NGINX_CONF=${NGINX_CONF:-/etc/nginx/conf.d/family-tree.conf}
MYSQL_HOST_PORT=${MYSQL_HOST_PORT:-3308}
REDIS_HOST_PORT=${REDIS_HOST_PORT:-6381}
COMPOSE_BASE=${COMPOSE_BASE:-http://127.0.0.1:8090}
NS=''

ip_of() { docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' "$1" 2>/dev/null | cut -d' ' -f1; }

probe_tcp() { # $1=标签 $2=host:port
    # mysqld/redis 先发明文协议包，curl -v 会把它打到输出里；不滤掉 NUL，bash 会报
    # "ignored null byte in input"（结果不受影响，但发版日志里像出错）
    if [ -n "$NS" ]; then
        out=$("$NS" curl -v --max-time 5 "http://$2/" 2>&1 | tr -d '\000')
    else
        out=$(curl -v --max-time 5 "http://$2/" 2>&1 | tr -d '\000')
    fi
    case "$out" in
        *"Connected to"*) echo "OK   $1: 能建 TCP -> $2（mysqld/redis 先发协议，HTTP 000 属正常）" ;;
        *"No route to host"*|*"Permission denied"*) echo "FAIL $1: TCP 被拒 -> $2（多半是不在 firewalld trusted 的网桥/宿主端口）"; FAIL=1 ;;
        *) echo "FAIL $1: 5s 内未建立连接 -> $2"; FAIL=1 ;;
    esac
}

http_code() { # $1=path $2=max-time $3..=curl 额外参数
    path=$1; mt=$2; shift 2
    if [ -n "$NS" ]; then
        "$NS" curl -s -o /dev/null -w '%{http_code}' --max-time "$mt" "$@" "$BASE$path" 2>/dev/null
    else
        curl -s -o /dev/null -w '%{http_code}' --max-time "$mt" "$@" "$BASE$path" 2>/dev/null
    fi
}

if [ "$(docker inspect -f '{{.State.Running}}' "$APP_CTR" 2>/dev/null)" = "true" ]; then
    MODE=compose
    APP_PID=$(docker inspect -f '{{.State.Pid}}' "$APP_CTR" 2>/dev/null | tr -d '[:space:]')
    if [ -z "$APP_PID" ] || [ "$APP_PID" = "0" ]; then
        echo "FAIL $APP_CTR 显示 Running 但 PID=$APP_PID（取不到 netns，别用宿主结果当结论）"
        echo "sentinel rc=1"; exit 1
    fi
    NS="nsenter -t $APP_PID -n"
    BASE=$COMPOSE_BASE
    MYSQL_TARGET="$(ip_of "$MYSQL_CTR"):3306"
    REDIS_TARGET="$(ip_of "$REDIS_CTR"):6379"
else
    MODE=k3s
    # awk 取第 2 列=端口；用 tr -dc '0-9' 会把 uniq -c 的计数粘进来变成 68091
    APP_PORT=$(grep -oE '127\.0\.0\.1:809[0-9]' "$NGINX_CONF" 2>/dev/null | sed 's/.*://' | sort | uniq -c | sort -rn | head -1 | awk '{print $2}')
    if [ -z "$APP_PORT" ]; then
        echo "FAIL 从 $NGINX_CONF 里读不到 127.0.0.1:809x 反代目标（nginx 配置被改过？先手工确认在任槽位）"
        echo "sentinel rc=1"; exit 1
    fi
    BASE="http://127.0.0.1:$APP_PORT"
    MYSQL_TARGET="127.0.0.1:$MYSQL_HOST_PORT"
    REDIS_TARGET="127.0.0.1:$REDIS_HOST_PORT"
fi
echo "mode=$MODE base=$BASE"

case "$MYSQL_TARGET" in
    :*) echo "FAIL 未取到 $MYSQL_CTR 的地址（容器没起？名字不对？）"; FAIL=1 ;;
    *) probe_tcp mysql "$MYSQL_TARGET" ;;
esac
case "$REDIS_TARGET" in
    :*) echo "WARN 未取到 $REDIS_CTR 的地址（纯 H2 形态可忽略，否则算失败）"; FAIL=1 ;;
    *) probe_tcp redis "$REDIS_TARGET" ;;
esac

health=$(http_code /actuator/health 5)
[ "$health" = "200" ] && echo "OK   health: $BASE/actuator/health = 200" \
    || { echo "FAIL health: 期望 200，实际 ${health:-无响应}"; FAIL=1; }

t_start=$(date +%s)
login=$(http_code /api/auth/login 25 -H 'Content-Type: application/json' \
    -d '{"username":"__sentinel__","password":"__sentinel__"}')
t_elapsed=$(( $(date +%s) - t_start ))
if [ "$t_elapsed" -lt 25 ] && [ -n "$login" ] && [ "$login" != "000" ]; then
    echo "OK   login: ${t_elapsed}s 返回 HTTP $login（假凭据，401/400/业务码均正常）"
else
    echo "FAIL login: ${t_elapsed}s、HTTP ${login:-无} —— 慢返回多半是 redis/mysql 被拦，无响应多半是应用端口没起"
    FAIL=1
fi

echo "sentinel rc=$FAIL"
exit $FAIL
