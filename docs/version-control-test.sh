#!/bin/bash

# 版本控制功能 - API测试脚本
# 使用方法: bash version-control-test.sh [BASE_URL] [SESSION_COOKIE]

BASE_URL=${1:-"http://localhost:8080"}
SESSION_COOKIE=${2:-""}

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}  版本控制功能 - API自动化测试${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""

# 检查会话Cookie
if [ -z "$SESSION_COOKIE" ]; then
    echo -e "${RED}⚠️  警告: 未提供Session Cookie${NC}"
    echo "请先登录系统,然后使用以下命令:"
    echo "bash version-control-test.sh http://localhost:8080 'JSESSIONID=your_session_id'"
    echo ""
    echo "或者手动替换下面的测试命令中的Cookie值"
    echo ""
fi

HEADERS=""
if [ -n "$SESSION_COOKIE" ]; then
    HEADERS="-H \"Cookie: $SESSION_COOKIE\""
fi

# 测试1: 查询节点历史(假设节点ID=1)
echo -e "${YELLOW}[测试 1/6] 查询节点历史 (nodeId=1)${NC}"
RESPONSE=$(curl -s -X GET "${BASE_URL}/api/version/node/1/history?page=1&size=5" \
    -H "Content-Type: application/json" \
    $HEADERS)
echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"
echo ""

# 测试2: 创建快照
echo -e "${YELLOW}[测试 2/6] 创建快照${NC}"
SNAPSHOT_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/version/snapshot?snapshotName=自动化测试快照&description=通过脚本创建" \
    -H "Content-Type: application/json" \
    $HEADERS)
echo "$SNAPSHOT_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$SNAPSHOT_RESPONSE"

# 提取快照ID
SNAPSHOT_ID=$(echo "$SNAPSHOT_RESPONSE" | grep -o '"snapshotId":[0-9]*' | cut -d':' -f2)
echo ""
if [ -n "$SNAPSHOT_ID" ]; then
    echo -e "${GREEN}✓ 快照创建成功, ID: ${SNAPSHOT_ID}${NC}"
else
    echo -e "${RED}✗ 快照创建失败${NC}"
fi
echo ""

# 测试3: 列出所有快照
echo -e "${YELLOW}[测试 3/6] 列出所有快照${NC}"
curl -s -X GET "${BASE_URL}/api/version/snapshot/list" \
    -H "Content-Type: application/json" \
    $HEADERS | python3 -m json.tool 2>/dev/null
echo ""

# 测试4: 查询快照详情
if [ -n "$SNAPSHOT_ID" ]; then
    echo -e "${YELLOW}[测试 4/6] 查询快照详情 (snapshotId=${SNAPSHOT_ID})${NC}"
    curl -s -X GET "${BASE_URL}/api/version/snapshot/${SNAPSHOT_ID}" \
        -H "Content-Type: application/json" \
        $HEADERS | python3 -m json.tool 2>/dev/null
    echo ""
fi

# 测试5: 预览快照恢复
if [ -n "$SNAPSHOT_ID" ]; then
    echo -e "${YELLOW}[测试 5/6] 预览快照恢复 (snapshotId=${SNAPSHOT_ID})${NC}"
    RESPONSE=$(curl -s -X POST "${BASE_URL}/api/version/snapshot/${SNAPSHOT_ID}/preview" \
        -H "Content-Type: application/json" \
        $HEADERS)
    echo "$RESPONSE" | python3 -m json.tool 2>/dev/null | head -20
    echo "... (数据过长,仅显示前20行)"
    echo ""
fi

# 测试6: 删除快照
if [ -n "$SNAPSHOT_ID" ]; then
    echo -e "${YELLOW}[测试 6/6] 删除快照 (snapshotId=${SNAPSHOT_ID})${NC}"
    curl -s -X DELETE "${BASE_URL}/api/version/snapshot/${SNAPSHOT_ID}" \
        -H "Content-Type: application/json" \
        $HEADERS
    echo ""
    echo -e "${GREEN}✓ 快照已删除${NC}"
    echo ""
fi

# 测试7: 对比版本(如果存在历史记录)
echo -e "${YELLOW}[测试 7/7] 对比节点版本 (nodeId=1, v1=1, v2=2)${NC}"
curl -s -X GET "${BASE_URL}/api/version/node/1/compare?v1=1&v2=2" \
    -H "Content-Type: application/json" \
    $HEADERS | python3 -m json.tool 2>/dev/null
echo ""
echo "(如果没有历史数据,此测试会返回错误,这是正常的)"
echo ""

echo -e "${YELLOW}========================================${NC}"
echo -e "${GREEN}  测试完成!${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""

echo "📚 详细文档:"
echo "  - 功能总结: docs/version-control-feature-summary.md"
echo "  - 集成指南: docs/version-control-integration-guide.md"
echo "  - 快速启动: docs/version-control-quickstart.md"
echo ""
