#!/bin/sh
# 只读预检：不写任何东西，只打印状态证据（供发版前后各跑一次做对照）
set -e
echo "=== 1. 容器 ==="
docker ps --format '{{.Names}}|{{.Image}}|{{.Status}}'
echo "=== 2. app 关键 env ==="
docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' family-tree-app | grep -E '^(SPRING_PROFILES_ACTIVE|SPRING_DATA_REDIS_HOST|H2_DATA_PATH|FAMILY_TREE_PHOTO_DIR|JAVA_OPTS)='
echo "=== 3. compose 里的存储形态（named volume 还是 bind） ==="
grep -nE 'volumes:|- \./data|mysql-data|redis-data|family-tree-data' /opt/family-tree/docker-compose.yml | head -20
echo "=== 4. Flyway 与表/行数 ==="
docker exec family-tree-mysql sh -c 'mysql -N -uroot -p"$MYSQL_ROOT_PASSWORD" -e "select concat(\"schema_version=\", max(version), \"  success=\", max(success)) from family_tree.flyway_schema_history; select concat(\"tables=\", count(*)) from information_schema.tables where table_schema=\"family_tree\"; select concat(\"family=\", count(*), \" account=\", (select count(*) from family_tree.account)) from family_tree.family;"' 2>/dev/null
echo "=== 5. 中文可读性抽查（不导数据） ==="
docker exec family-tree-mysql sh -c 'mysql -N -uroot -p"$MYSQL_ROOT_PASSWORD" -e "select id, name from family_tree.family order by id limit 3;"' 2>/dev/null
echo "=== 6. 备份 ==="
ls -lt /opt/family-tree/backups 2>/dev/null | head -5
echo "=== 7. 磁盘 / 内存 ==="
df -h / | tail -1
free -m | head -2
echo "=== 8. 应用与对外健康 ==="
curl -s -o /dev/null -w 'health_8090=%{http_code}\n' http://127.0.0.1:8090/actuator/health
curl -s -o /dev/null -w 'nginx_80=%{http_code}\n' http://127.0.0.1/
echo "=== 9. 镜像清单 ==="
docker images --format '{{.Repository}}:{{.Tag}}|{{.Size}}|{{.CreatedSince}}' | grep family-tree
echo "=== 10. cron ==="
crontab -l 2>/dev/null | grep -v '^#' | head -5
