#!/bin/sh
# ft-release-check.sh — 发版过程中的状态复查（只读）
# 用法：sh /tmp/ft-release-check.sh      （放在实例上跑，避免 workbench exec 的单行引号地狱）
Q() {
  docker exec family-tree-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 -N -e "$1" 2>/dev/null
}
PW=$(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' family-tree-mysql | sed -n 's/^MYSQL_ROOT_PASSWORD=//p')
export MYSQL_ROOT_PASSWORD="$PW"

echo "=== 镜像与容器"
docker images | grep -E "flyway|family-tree" | awk '{print $1":"$2, $3}'
docker ps -a --format '{{.Names}}\t{{.Status}}\t{{.Image}}' | grep family-tree

echo "=== Flyway 历史（V7 / V40）"
Q "SELECT version,checksum,success,description FROM family_tree.flyway_schema_history WHERE version IN (7,40);"

echo "=== 业务基线（表数 / family / account / max(id) / 最新家族名）"
Q "SELECT (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='family_tree'), (SELECT COUNT(*) FROM family_tree.family), (SELECT COUNT(*) FROM family_tree.account), (SELECT MAX(id) FROM family_tree.family), (SELECT name FROM family_tree.family ORDER BY id DESC LIMIT 1);"

echo "=== 丧偶列是否存在（V7 的效果）"
Q "SELECT COLUMN_NAME,COLUMN_TYPE,COLUMN_DEFAULT FROM information_schema.columns WHERE table_schema='family_tree' AND table_name='family_relation' AND column_name='is_widowed';"

echo "=== 对外与内部健康"
echo -n "app_health="; curl -s -o /dev/null -w '%{http_code} ' --max-time 8 http://127.0.0.1:8090/actuator/health; echo
echo -n "nginx_80="; curl -s -o /dev/null -w '%{http_code} ' --max-time 8 http://127.0.0.1/; echo
echo -n "nginx_443_ip="; curl -sk -o /dev/null -w '%{http_code} ' --max-time 8 https://127.0.0.1/; echo

echo "=== 应用日志尾部（若有容器）"
docker logs --tail 15 family-tree-app 2>&1 | tail -15
