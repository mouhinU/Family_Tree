#!/bin/sh
# ft-sentinel — 发版/重启后的容器间连通性验收
#
# 检查三件事，任一失败即退出码 1：
#   1) app 容器能不能连上 mysql / redis 的 TCP 端口（firewalld 把网桥踢出 trusted 时，
#      症状是「ICMP 通、TCP 被 reject」，只有从 app 的网络命名空间里量才有效——
#      宿主上另起的临时容器属于另一个网络，ARP 都不通，量出来是假阳性）
#   2) /actuator/health 是否 200（DOWN 时 nginx 反代会挂 502/504）
#   3) 登录接口是否在 25s 内返回（会话与登录锁定都在 redis 上；redis 不通时是卡 ~30-60s 后 500）
#
# mysql/redis 的地址按容器名现查，不写死 IP；docker 网桥随发版重建，IP 会变。
set -u

FAIL=0
APP_CTR=${APP_CTR:-family-tree-app}
MYSQL_CTR=${MYSQL_CTR:-family-tree-mysql}
REDIS_CTR=${REDIS_CTR:-family-tree-redis}
BASE_URL=${BASE_URL:-http://127.0.0.1:8090}

ip_of() { docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' "$1" 2>/dev/null | cut -d' ' -f1; }

APP_PID=$(docker inspect -f '{{.State.Pid}}' "$APP_CTR" 2>/dev/null)
MYSQL_IP=$(ip_of "$MYSQL_CTR")
REDIS_IP=$(ip_of "$REDIS_CTR")

[ -n "$APP_PID" ] || { echo "FAIL 找不到 $APP_CTR 的 PID（容器没起？名字不对？）"; exit 1; }
[ -n "$MYSQL_IP" ] || echo "WARN 未找到 $MYSQL_CTR 的 IP"
[ -n "$REDIS_IP" ] || echo "WARN 未找到 $REDIS_CTR 的 IP（纯 H2 形态可忽略）"

if [ -n "$MYSQL_IP" ]; then
    out=$(nsenter -t "$APP_PID" -n curl -v --max-time 5 "http://$MYSQL_IP:3306/" 2>&1)
    case "$out" in
        *"Connected to"*) echo "OK   mysql: app 容器内能建 TCP（banner 握手导致 HTTP 000 属正常）" ;;
        *"No route to host"*|*"Permission denied"*) echo "FAIL mysql: app 容器内 TCP 被拒（多半是网桥不在 firewalld trusted）-> $MYSQL_IP:3306" ; FAIL=1 ;;
        *) echo "FAIL mysql: app 容器内 5s 内未建立连接 -> $MYSQL_IP:3306" ; FAIL=1 ;;
    esac
fi

if [ -n "$REDIS_IP" ]; then
    out=$(nsenter -t "$APP_PID" -n curl -v --max-time 5 "http://$REDIS_IP:6379/" 2>&1)
    case "$out" in
        *"Connected to"*) echo "OK   redis: app 容器内能建 TCP（HTTP 000 属正常）" ;;
        *"No route to host"*|*"Permission denied"*) echo "FAIL redis: app 容器内 TCP 被拒 -> $REDIS_IP:6379" ; FAIL=1 ;;
        *) echo "FAIL redis: app 容器内 5s 内未建立连接 -> $REDIS_IP:6379" ; FAIL=1 ;;
    esac
fi

health=$(nsenter -t "$APP_PID" -n curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$BASE_URL/actuator/health" 2>/dev/null)
[ "$health" = "200" ] && echo "OK   health: $BASE_URL/actuator/health = 200" \
    || { echo "FAIL health: 期望 200，实际 ${health:-无响应}"; FAIL=1; }

t_start=$(date +%s)
login=$(nsenter -t "$APP_PID" -n curl -s -o /dev/null -w '%{http_code}' --max-time 25 \
    -H 'Content-Type: application/json' \
    -d '{"username":"__sentinel__","password":"__sentinel__"}' \
    "$BASE_URL/api/auth/login" 2>/dev/null)
t_elapsed=$(( $(date +%s) - t_start ))
# 凭据是假的，401/400/业务码 500 都算正常；不正常的是「长时间不返回」或连不上
[ "$t_elapsed" -lt 25 ] && echo "OK   login: ${t_elapsed}s 返回（HTTP ${login:-无}，假凭据）" \
    || { echo "FAIL login: ${t_elapsed}s 未返回，疑似 redis/mysql 转发被拦"; FAIL=1; }

echo "sentinel rc=$FAIL"
exit $FAIL
