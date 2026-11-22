#!/bin/sh
# ft-v7-checksum-fix.sh — 用一条 SQL 做 ecs-setup.sh repair 对 V7 那一行所做的事
#
# 为什么手写：repair 阶段要 flyway/flyway:11.14-alpine，实例拉这个仓库反复超时（2026-11-22），
# 而应用已停机。Flyway 的 repair 在此场景的实际效果就是把 V7 的 checksum 从旧值改成新值
# （没有 success=0 的失败行要清，description/type 都没变），所以带 WHERE 旧值守卫的 UPDATE 等价。
# 新值 465406669 已用 CRC32 口径从 v73 镜像里的 jar 反查过（unzip -p app.jar BOOT-INF/classes/db/migration/mysql/V7__add_widowed_flag.sql）。
set -u
NEW=${NEW_CHECKSUM:-465406669}
OLD=${OLD_CHECKSUM:--781586466}

PW=$(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' family-tree-mysql | sed -n 's/^MYSQL_ROOT_PASSWORD=//p')
M() { docker exec family-tree-mysql mysql -uroot -p"$PW" --default-character-set=utf8mb4 -N -e "$1" 2>/dev/null; }

echo "改前："; M "SELECT version,checksum,success,description FROM family_tree.flyway_schema_history WHERE version='7';"

n=$(M "SELECT COUNT(*) FROM family_tree.flyway_schema_history WHERE version='7' AND checksum=$OLD;")
if [ "$n" != "1" ]; then
    echo "中止：version=7 且 checksum=$OLD 的行数是 $n（期望 1）。要么已经改过，要么库里状态与预期不符 —— 别硬改。"
    exit 1
fi

M "UPDATE family_tree.flyway_schema_history SET checksum=$NEW WHERE version='7' AND checksum=$OLD;"
M "COMMIT;" 2>/dev/null

echo "改后："; M "SELECT version,checksum,success,description FROM family_tree.flyway_schema_history WHERE version='7';"
echo "失败行（success=0）应为 0 行："; M "SELECT version,success FROM family_tree.flyway_schema_history WHERE success=0;"
echo "历史表总行数与最高版本："; M "SELECT COUNT(*), MAX(CAST(version AS UNSIGNED)) FROM family_tree.flyway_schema_history;"
