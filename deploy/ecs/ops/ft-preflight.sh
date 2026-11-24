#!/bin/sh
# 只读预检：不写任何东西，只打印状态证据（供发版前后各跑一次做对照）
#
# 2026-11-24 k3s 接管后重写，四处会误导发版判定的坑都修掉了：
#   1) 不再 `set -e`：curl 连不上返回 7 会让脚本从中途静默退出，后半截（镜像/cron）根本不打印，
#      看起来像「没有历史镜像」而不是「没跑到」。现在每条自带兜底，全程跑完。
#   2) Flyway 顶部版本从 `max(version)` 改成按 `installed_rank desc`：version 是 VARCHAR，
#      字符串序 '9' > '40'，旧写法永远报 schema_version=9，而 SOP 的判定口径是「历史表顶 40」。
#   3) 所有取中文的查询补 `--default-character-set=utf8mb4`：缺了它 mysql 客户端按连接默认字符集
#      输出，中文全变 `????`，会被 §7 的「乱码=字符集回归」判成生产数据坏了（实际数据没问题）。
#   4) 应用侧健康不再写死 8090：compose app 退役后 8090 恒为 000。改成读 nginx 反代实际指向的
#      127.0.0.1:809x（蓝绿在任交替），并在两种形态下分别取应用 env 来源。
set -u

NGINX_CONF=${NGINX_CONF:-/etc/nginx/conf.d/family-tree.conf}
mysql_q() { # 只读查询，带 utf8mb4，密码在容器内展开
    docker exec family-tree-mysql sh -c \
        "mysql -N --default-character-set=utf8mb4 -uroot -p\"\$MYSQL_ROOT_PASSWORD\" -e \"$1\"" 2>/dev/null
}

echo "=== 0. 应用形态 ==="
if [ "$(docker inspect -f '{{.State.Running}}' family-tree-app 2>/dev/null)" = "true" ]; then
    echo "compose app 在任（容器 family-tree-app Running）"
else
    echo "compose app 未在任 → 按 k3s 蓝绿形态检查"
fi
# 取出现次数最多的槽位作为在任端口。注意不能用 `tr -dc '0-9'`：uniq -c 的行是「计数 端口」，
# 去掉空格会变成 68091 这种假端口，健康检查就会永远 000 被误判成应用挂了。
APP_PORT=$(grep -oE '127\.0\.0\.1:809[0-9]' "$NGINX_CONF" 2>/dev/null | sed 's/.*://' | sort | uniq -c | sort -rn | head -1 | awk '{print $2}')
echo "nginx 反代在任应用端口=${APP_PORT:-未取到}（$(grep -cE '127\.0\.0\.1:809[0-9]' "$NGINX_CONF" 2>/dev/null) 处 proxy 引用）"

echo "=== 1. 容器 ==="
docker ps --format '{{.Names}}|{{.Image}}|{{.Status}}' || echo "docker ps 失败"
echo "--- k3s（未装则跳过）---"
k3s kubectl -n prod get deploy -o jsonpath='{range .items[*]}{.metadata.name}={.spec.replicas} {.spec.template.spec.containers[0].image}{"\n"}{end}' 2>/dev/null \
    || echo "k3s 不可用（compose 形态可忽略）"
k3s kubectl -n prod get pods -o wide --no-headers 2>/dev/null | awk '{print "pod:", $1, $2, $3, $5}' || true

echo "=== 2. app 关键 env ==="
if [ "$(docker inspect -f '{{.State.Running}}' family-tree-app 2>/dev/null)" = "true" ]; then
    docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' family-tree-app 2>/dev/null \
        | grep -E '^(SPRING_PROFILES_ACTIVE|SPRING_DATA_REDIS_HOST|SPRING_DATA_REDIS_PORT|DB_URL|H2_DATA_PATH|FAMILY_TREE_PHOTO_DIR|SERVER_PORT|JAVA_OPTS)=' \
        | sed -E 's#(jdbc:mysql://[^?]*).*#\1?…#' || echo "取不到 compose 容器 env"
else
    echo "(compose app 已停) k3s secret ft-app-env 的数据面指向："
    k3s kubectl -n prod get secret ft-app-env -o jsonpath='{.data}' 2>/dev/null \
        | tr ',' '\n' | tr -d '"{}' | grep -iE '^(DB_URL|SPRING_DATA_REDIS_HOST|SPRING_DATA_REDIS_PORT|SERVER_PORT|SPRING_PROFILES_ACTIVE)' \
        | while IFS=':' read -r k v; do
            printf '%s=%s\n' "$k" "$(printf '%s' "$v" | base64 -d 2>/dev/null | sed -E 's#(jdbc:mysql://[^?]*).*#\1?…#')"
          done || echo "取不到 k3s secret"
fi

echo "=== 3. compose 里的存储形态（named volume 还是 bind） ==="
grep -nE 'volumes:|- \./data|mysql-data|redis-data|family-tree-data' /opt/family-tree/docker-compose.yml 2>/dev/null | head -20

echo "=== 4. Flyway 与表/行数 ==="
mysql_q "select concat('schema_top=', version, '  success=', success, '  description=', description) from family_tree.flyway_schema_history order by installed_rank desc limit 1;"
mysql_q "select concat('tables=', count(*)) from information_schema.tables where table_schema='family_tree';"
mysql_q "select concat('family=', (select count(*) from family_tree.family), ' account=', (select count(*) from family_tree.account), ' has_phone=', (select count(*) from family_tree.account where phone is not null and phone<>''));"

echo "=== 5. 中文可读性抽查（不导数据，必须 utf8mb4） ==="
mysql_q "select id, name from family_tree.family order by id limit 3;"

echo "=== 6. 备份（cron 自动备份 backups/ 与发版前手工快照 backup/） ==="
ls -lt /opt/family-tree/backups 2>/dev/null | head -4 || echo "无 backups 目录"
ls -lt /opt/family-tree/backup 2>/dev/null | head -5 || echo "无 backup 目录（发版前手工快照落这儿）"

echo "=== 7. 磁盘 / 内存 ==="
df -h / | tail -1
free -m | head -2

echo "=== 8. 应用与对外健康 ==="
if [ -n "$APP_PORT" ]; then
    echo "health_${APP_PORT}=$(curl -s -o /dev/null -w '%{http_code}' --max-time 6 "http://127.0.0.1:$APP_PORT/actuator/health" 2>/dev/null)"
fi
echo "health_8090=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://127.0.0.1:8090/actuator/health 2>/dev/null)（compose 退役后 000 属预期）"
echo "nginx_80=$(curl -s -o /dev/null -w '%{http_code}' --max-time 6 http://127.0.0.1/ 2>/dev/null)"
echo "login_v=$(curl -s --max-time 6 http://127.0.0.1/login.html 2>/dev/null | grep -oE 'login.js\?v=[0-9a-z]+' | head -1)"
echo "auth_config=$(curl -s --max-time 6 http://127.0.0.1/api/auth/config 2>/dev/null)"

echo "=== 9. 镜像清单 ==="
docker images --format '{{.Repository}}:{{.Tag}}|{{.Size}}|{{.CreatedSince}}' 2>/dev/null | grep family-tree || echo "docker 侧无 family-tree 镜像"
echo "--- containerd（k3s 实际拉取用的库，imagePullPolicy=Never，导入缺失 pod 会 ImageNeverPull）---"
k3s ctr -n k8s.io images list 2>/dev/null | awk '/family-tree/ {print $1, $3}' || echo "k3s ctr 不可用"

echo "=== 10. cron ==="
crontab -l 2>/dev/null | grep -v '^#' | head -5 || echo "无 crontab"
