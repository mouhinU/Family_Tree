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

echo "=== Flyway 历史（V7 / 顶部版本）"
# 顶部版本必须按 installed_rank 取：version 是 VARCHAR，字符串序 '9' > '40'
Q "SELECT version,checksum,success,description FROM family_tree.flyway_schema_history WHERE version IN ('7') OR installed_rank=(SELECT MAX(installed_rank) FROM family_tree.flyway_schema_history);"

echo "=== 业务基线（表数 / family / account / max(id) / 最新家族名）"
Q "SELECT (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='family_tree'), (SELECT COUNT(*) FROM family_tree.family), (SELECT COUNT(*) FROM family_tree.account), (SELECT MAX(id) FROM family_tree.family), (SELECT name FROM family_tree.family ORDER BY id DESC LIMIT 1);"

echo "=== 丧偶列是否存在（V7 的效果）"
Q "SELECT COLUMN_NAME,COLUMN_TYPE,COLUMN_DEFAULT FROM information_schema.columns WHERE table_schema='family_tree' AND table_name='family_relation' AND column_name='is_widowed';"

echo "=== 对外与内部健康"
# 应用端口从 nginx 反代现取（k3s 蓝绿在任交替，写死 8090 在 compose 退役后恒 000）。
# awk 取第 2 列：uniq -c 的行是「计数 端口」，用 tr -dc '0-9' 会粘成 68091 这种假端口。
AP=$(grep -oE '127\.0\.0\.1:809[0-9]' /etc/nginx/conf.d/family-tree.conf 2>/dev/null | sed 's/.*://' | sort | uniq -c | sort -rn | head -1 | awk '{print $2}')
echo "nginx 反代在任端口=${AP:-未取到}"
[ -n "$AP" ] && { echo -n "app_health_${AP}="; curl -s -o /dev/null -w '%{http_code} ' --max-time 8 "http://127.0.0.1:$AP/actuator/health"; echo; }
echo -n "app_health_8090="; curl -s -o /dev/null -w '%{http_code} ' --max-time 8 http://127.0.0.1:8090/actuator/health; echo "（compose 退役后 000 属预期）"
echo -n "nginx_80="; curl -s -o /dev/null -w '%{http_code} ' --max-time 8 http://127.0.0.1/; echo
echo -n "nginx_443_ip="; curl -sk -o /dev/null -w '%{http_code} ' --max-time 8 https://127.0.0.1/; echo

echo "=== 应用日志尾部（compose 在任取 docker logs，否则取 k3s 在任槽）"
if [ "$(docker inspect -f '{{.State.Running}}' family-tree-app 2>/dev/null)" = "true" ]; then
    docker logs --tail 15 family-tree-app 2>&1 | tail -15
    echo "host 日志（同一 hostPath，k3s pod 也写这儿）：$(ls -t /opt/family-tree/logs 2>/dev/null | head -1)"
else
    k3s kubectl -n prod logs deploy/family-tree-app-blue --tail=15 2>/dev/null \
      || k3s kubectl -n prod logs deploy/family-tree-app-green --tail=15 2>/dev/null \
      || echo "两个槽都取不到日志（都在 replicas=0？先看 deploy 行数）"
    ls -t /opt/family-tree/logs 2>/dev/null | head -2
fi
