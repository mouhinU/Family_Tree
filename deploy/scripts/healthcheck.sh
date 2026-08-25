#!/usr/bin/env bash
# ==========================================
# 族谱管理系统 — 健康检查脚本
#
# 用法：./healthcheck.sh [应用地址]
# 默认检查 http://localhost:8090
#
# 退出码：
#   0 = 健康
#   1 = 不健康或不可达
# ==========================================
set -euo pipefail

APP_URL="${1:-http://localhost:8090}"
HEALTH_ENDPOINT="${APP_URL}/actuator/health"

echo "[$(date '+%Y-%m-%d %H:%M:%S')] 健康检查: ${HEALTH_ENDPOINT}"

# 请求健康端点
HTTP_CODE=$(curl -s -o /tmp/health_response.json -w "%{http_code}" --connect-timeout 5 --max-time 10 "$HEALTH_ENDPOINT" 2>/dev/null) || {
    echo "FAIL: 无法连接到 ${APP_URL}"
    exit 1
}

RESPONSE=$(cat /tmp/health_response.json 2>/dev/null)
rm -f /tmp/health_response.json

if [ "$HTTP_CODE" = "200" ]; then
    STATUS=$(echo "$RESPONSE" | python3 -c "import json,sys; print(json.load(sys.stdin).get('status','UNKNOWN'))" 2>/dev/null || echo "UNKNOWN")
    if [ "$STATUS" = "UP" ]; then
        echo "OK: 应用健康 (HTTP ${HTTP_CODE}, status=${STATUS})"
        exit 0
    else
        echo "WARN: 应用响应 200 但状态异常 (status=${STATUS})"
        echo "$RESPONSE"
        exit 1
    fi
else
    echo "FAIL: 健康检查失败 (HTTP ${HTTP_CODE})"
    echo "$RESPONSE"
    exit 1
fi
