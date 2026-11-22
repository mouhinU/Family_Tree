#!/usr/bin/env bash
# 族谱管理系统 — 阿里云 ECS 分阶段部署脚本
#
# 目标目录布局：
#   /opt/family-tree/     本目录（compose / .env / nginx 配置 / 本脚本 / backup.sh）
#   /opt/family-tree/migrations/mysql/  仅 repair 阶段需要：与新镜像同一 commit 的 40 个迁移文件
#   /root/ft-migrate/     迁移输入（MIG_DIR）：docker save 的镜像 tar.gz + 生产 dump *.sql
#
# 用法: ./ecs-setup.sh <stage>
#   env     生成 /opt/family-tree/.env（口令随机生成，不打印到日志）
#   images  docker load 应用镜像 + 拉取 mysql/redis（幂等，已存在则跳过）
#   data    启动 mysql + redis 并等待 healthy
#   import  导入 MIG_DIR/*.sql（库内已有表则中止，不覆盖）
#   app     启动应用容器并等待 /actuator/health
#   nginx   安装并配置 nginx 反代
#   backup  安装每日 03:00 的数据库备份 cron
#   tobind  一次性：把旧命名卷里的数据搬到宿主 ./data（compose 已改 bind 挂载）
#   repair  一次性：改过已应用的迁移文件内容后，重写 flyway_schema_history 的 checksum
#   status  打印容器、内存、监听、健康、宿主数据目录与最近备份
#
# 顺序：env → images → data → import → app → nginx → backup（老环境先跑 tobind 再起 data）
# 升级发版只需改 .env 的 FT_IMAGE 后重跑 app 阶段；若这一版改过既有迁移文件，先跑 repair。
set -euo pipefail

APP_DIR=${APP_DIR:-/opt/family-tree}
MIG_DIR=${MIG_DIR:-/root/ft-migrate}
COMPOSE="docker compose -f ${APP_DIR}/docker-compose.yml --env-file ${APP_DIR}/.env"
# repair 阶段用：迁移文件目录，以及与之配套的 flyway CLI 镜像。
# 版本必须跟 Boot 4.0.8 管理的 flyway（11.14.1）一致 —— checksum 由 Flyway 自己算，
# 换个大版本若算法有变，repair 写完应用侧仍会报 mismatch。
MIGRATIONS_DIR=${MIGRATIONS_DIR:-${APP_DIR}/migrations/mysql}
FLYWAY_IMAGE=${FLYWAY_IMAGE:-flyway/flyway:11.14-alpine}
# compose 里写死的 mysql 容器名；允许覆盖是为了能在本机拿一个同名沙箱容器跑通 repair，
# 而不必占用/改动真实栈的容器
MYSQL_CTR=${MYSQL_CTR:-family-tree-mysql}
# V7 去掉 MariaDB 的 IF NOT EXISTS 之后应有的 checksum（Flyway 11.14.1 对 mysql/V7__add_widowed_flag.sql
# 现算的值，实测 465406669）。对不上就说明 MIGRATIONS_DIR 里的文件跟镜像里打包的不是同一份。
# 以后再改 V7 就同步改这里，或置空跳过校验：EXPECT_V7_CHECKSUM= ./ecs-setup.sh repair
# 要手工核算：Flyway 的 checksum = 对「去掉空行后的每一行（保留行内首尾空格，含注释行）」
# 直接首尾拼接（行间不加分隔符）取 CRC32，再按有符号 int32 解释。
EXPECT_V7_CHECKSUM=${EXPECT_V7_CHECKSUM-465406669}

log() { echo "[$(date '+%H:%M:%S')] $*"; }
die() { echo "错误: $*" >&2; exit 1; }

rand_pw() {
    local base
    base=$(openssl rand -base64 32 | tr -dc 'A-Za-z0-9' | head -c 16)
    # 追加固定字符组，确保满足 MySQL validate_password 对大小写/数字/符号的要求
    echo "${base}#Qz7"
}

stage_env() {
    [ -f "${APP_DIR}/.env" ] && die ".env 已存在，不覆盖（如需重置请手工删除）"
    mkdir -p "${APP_DIR}/data/photos" "${APP_DIR}/logs"
    chmod 700 "${APP_DIR}"

    local root_pw app_pw
    root_pw=$(rand_pw)
    app_pw=$(rand_pw)

    umask 077
    cat > "${APP_DIR}/.env" <<EOF
# 必须与 tar.gz 里的镜像 tag 完全一致（docker load 后 docker images 可查），否则 images 阶段会重复 load、app 阶段起不来
FT_IMAGE=${FT_IMAGE:-family-tree:v71-local}
APP_PORT=8090
MYSQL_DATABASE=family_tree
DB_USERNAME=family_tree_app
MYSQL_ROOT_PASSWORD=${root_pw}
DB_PASSWORD=${app_pw}
MYSQL_PORT=3308
REDIS_PORT=6381
PLATFORM_ADMIN_USERNAMES=admin
FAMILY_TREE_PHOTO_DIR=/app/data/photos
APP_MEM_LIMIT=768m
MYSQL_MEM_LIMIT=512m
REDIS_MEM_LIMIT=96m
EOF
    chmod 600 "${APP_DIR}/.env"
    log ".env 已生成（2 条随机口令，未打印到日志）"
    log "取用方式: grep DB_PASSWORD ${APP_DIR}/.env —— 请把口令同步进你的密码管理器，"
    log "这是全网唯一副本，丢了 root 口令只能进容器改 mysql 用户表。"
}

stage_images() {
    local tarball
    tarball=$(ls -1 "${MIG_DIR}"/*.tar.gz 2>/dev/null | head -1) || true
    [ -n "${tarball}" ] || die "未找到 ${MIG_DIR}/*.tar.gz（docker save 产物）"

    # 先验字节完整性再 load：workbench upload 经 OSS 中转，静默截断过一次的话这里能兜住
    # 自己比对摘要而不是用 sha256sum -c：校验文件里可能记的是绝对路径，-c 会因为找不到该路径而失败
    if [ -f "${tarball}.sha256" ]; then
        log "校验 sha256 ..."
        local expected actual
        expected=$(cut -d' ' -f1 "${tarball}.sha256" | tr 'A-F' 'a-f')
        actual=$(sha256sum "${tarball}" | cut -d' ' -f1)
        [ "${expected}" = "${actual}" ] || die "sha256 不匹配：期望 ${expected}，实际 ${actual}（tar 包损坏或没上传同一个文件）"
        log "sha256 一致：${actual}"
    else
        log "警告：未找到 ${tarball}.sha256，跳过完整性校验"
    fi

    local img
    img=$(grep -m1 '^FT_IMAGE=' "${APP_DIR}/.env" | cut -d= -f2)
    if docker image inspect "${img}" >/dev/null 2>&1; then
        log "镜像 ${img} 已存在，跳过 load"
    else
        log "docker load ${tarball} ..."
        gunzip -c "${tarball}" | docker load
    fi

    for img in mysql:8.0 redis:8.8.0; do
        if docker image inspect "${img}" >/dev/null 2>&1; then
            log "${img} 已存在"
        else
            log "拉取 ${img} ..."
            docker pull "${img}"
        fi
    done
    docker images --format "{{.Repository}}:{{.Tag}} {{.Size}}" | grep -E "family-tree|mysql|redis" || true
}

stage_data() {
    [ -f "${APP_DIR}/.env" ] || die "先跑 env 阶段"
    # 数据目录属主必须是 uid 999：mysql/redis 官方镜像都以各自用户（uid 999）运行，
    # 宿主 bind 挂载后目录归 root 的话，容器起来直接 Permission denied
    mkdir -p "${APP_DIR}/data/mysql" "${APP_DIR}/data/redis" "${APP_DIR}/data/photos" "${APP_DIR}/logs"
    chown -R 999:999 "${APP_DIR}/data/mysql" "${APP_DIR}/data/redis"
    chmod 750 "${APP_DIR}/data/mysql" "${APP_DIR}/data/redis"
    ${COMPOSE} up -d mysql redis
    log "等待 mysql healthy ..."
    ${COMPOSE} ps
    local i
    for i in $(seq 1 60); do
        if docker inspect -f '{{.State.Health.Status}}' family-tree-mysql 2>/dev/null | grep -q healthy; then
            log "mysql healthy"
            break
        fi
        [ "$i" = 60 ] && die "mysql 60 次探测（300s）内未 healthy"
        sleep 5
    done
    docker inspect -f '{{.Name}} health={{.State.Health.Status}} mem_limit={{.HostConfig.Memory}}' family-tree-mysql family-tree-redis
}

stage_import() {
    local dump
    dump=$(ls -1t "${MIG_DIR}"/*.sql 2>/dev/null | head -1) || true
    [ -n "${dump}" ] || die "未找到 ${MIG_DIR}/*.sql"
    log "使用 dump: ${dump} ($(du -h "${dump}" | cut -f1))"

    local existing
    existing=$(docker exec family-tree-mysql sh -c \
        'exec mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=\"family_tree\";"' 2>/dev/null) || die "无法连接 mysql"
    if [ "${existing}" != "0" ]; then
        die "family_tree 库已有 ${existing} 张表，疑似已导入过；本脚本不覆盖，请手工确认"
    fi
    log "目标库为空，开始导入 ..."
    docker exec -i family-tree-mysql sh -c \
        'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" family_tree' < "${dump}"

    local after before
    before=$(grep -c "^CREATE TABLE" "${dump}")
    after=$(docker exec family-tree-mysql sh -c \
        'exec mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=\"family_tree\";"')
    [ "${before}" = "${after}" ] || die "表数不符：dump 里 ${before} 张，库里 ${after} 张"
    log "导入完成，表数 = ${after}（与 dump 一致）"
    docker exec family-tree-mysql sh -c \
        'exec mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SELECT COUNT(*) FROM family_tree.family;"' | sed "s/^/family 行数 = /"
}

stage_app() {
    [ -f "${APP_DIR}/.env" ] || die "先跑 env 阶段"
    # V7 修好之后空库不再报错停下，而是安静建出一套空库并正常启动 —— 挂错目录/漏跑 tobind 就没人拦了，这里补一句
    local tbls
    tbls=$(docker exec family-tree-mysql sh -c \
        'exec mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=\"family_tree\";"' 2>/dev/null || echo 未知)
    [ "${tbls}" = "0" ] && {
        log "警告：family_tree 库 0 张表。全新环境没问题（Flyway 会建一套空库）；"
        log "     但如果你是从命名卷切过来，说明 tobind 没跑或挂错了目录 —— 先停在这里，别把空库当生产库用。"
    }
    ${COMPOSE} up -d family-tree
    log "等待应用健康（最多 240s）..."
    local i code
    for i in $(seq 1 48); do
        code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://127.0.0.1:8090/actuator/health || echo 000)
        if [ "${code}" = "200" ]; then
            log "应用 200 OK"
            curl -s --max-time 5 http://127.0.0.1:8090/actuator/health; echo
            log "启动耗时参考：v71 实测约 32s（Started WebApplication）"
            return 0
        fi
        sleep 5
    done
    log "健康检查超时，最近 60 行日志："
    docker logs --tail 60 family-tree-app
    die "应用未在 240s 内就绪"
}

stage_nginx() {
    command -v nginx >/dev/null 2>&1 || dnf install -y nginx
    cp "${APP_DIR}/nginx-family-tree.conf" /etc/nginx/conf.d/family-tree.conf
    # 让本配置成为 80 端口唯一 default server：摘掉主配置里 stock server 块的标记。
    # conf.d/*.conf 在 http{} 末尾才 include，顺序靠后，不摘掉的话请求会命中 nginx 默认页。
    if grep -q "default_server" /etc/nginx/nginx.conf; then
        cp -a /etc/nginx/nginx.conf "/etc/nginx/nginx.conf.bak.$(date +%Y%m%d_%H%M%S)"
        sed -i 's/ default_server;//g' /etc/nginx/nginx.conf
        log "已摘掉主配置中的 default_server（原文件已备份）"
    fi
    # SELinux enforcing 时放行 nginx 反代到本机 8090（Alinux3 默认 Disabled，这里只是兜底）
    if command -v getenforce >/dev/null 2>&1 && [ "$(getenforce)" = "Enforcing" ]; then
        setsebool -P httpd_can_network_connect 1 || true
    fi
    nginx -t
    systemctl enable --now nginx
    systemctl reload nginx
    log "nginx 已就绪（残留 conflicting server name \"_\" warning 无害）"
    curl -s -o /dev/null -w "本机 80 -> %{http_code}\n" --max-time 10 http://127.0.0.1/
}

stage_backup() {
    [ -f "${APP_DIR}/backup.sh" ] || die "未找到 ${APP_DIR}/backup.sh（请把本目录 5 个文件一起上传）"
    chmod 755 "${APP_DIR}/backup.sh"
    local line="0 3 * * * ${APP_DIR}/backup.sh >> /var/log/family-tree-backup.log 2>&1"
    # 先剔除旧的同类条目再追加，重复执行不会产生第二行
    ( crontab -l 2>/dev/null | grep -vF "${APP_DIR}/backup.sh"; echo "${line}" ) | crontab -
    log "cron 已安装：${line}"
    log "提醒：备份落在同一块系统盘，等同步到 OSS 或配控制台自动快照才算有异地副本"
    log "首轮请手工跑一次 ${APP_DIR}/backup.sh 确认能出文件"
}

tobind_one() {
    # $1 容器名  $2 卷在容器内的挂载点  $3 宿主目标目录  $4 搬运后必须存在的文件  $5 兜底卷名后缀
    local cname="$1" mount="$2" dst="$3" marker="$4" suffix="$5"
    local vol="" candidates n
    # 卷名先问容器自己要：一台机器上可能同时挂着多个 <project>_${suffix}，
    # 只按 *_${suffix} 后缀猜会搬错卷，把别的项目的数据当成本栈的生产数据
    if docker inspect "${cname}" >/dev/null 2>&1; then
        candidates=$(docker inspect -f "{{ range .Mounts }}{{ if eq .Destination \"${mount}\" }}{{ .Type }}|{{ .Name }} {{ end }}{{ end }}" "${cname}")
        case " ${candidates} " in
            *" volume|"*) vol=${candidates#*volume|}; vol=${vol%% *} ;;
            *) log "跳过 ${dst}：${cname} 的 ${mount} 不是命名卷（${candidates:-无挂载记录}），无需搬迁"; return 0 ;;
        esac
    else
        candidates=$(docker volume ls -q | grep -E "_${suffix}\$" || true)
        n=$(printf '%s\n' "${candidates}" | sed '/^[[:space:]]*$/d' | wc -l | tr -d ' ')
        [ "${n}" -le 1 ] || die "容器 ${cname} 已不在，且存在 ${n} 个 *_${suffix} 卷（${candidates//$'\n'/ }），无法判定哪个属于本栈 —— 请人工 cp -a，别交给我猜"
        vol=""
        for cand in ${candidates}; do vol="${cand}"; done   # 取最后一行；多行已在上面 die 掉
    fi
    [ -n "${vol}" ] || { log "跳过 ${dst}：没有 *_${suffix} 命名卷（全新环境本来就用 bind 挂载）"; return 0; }

    mkdir -p "${dst}"
    if [ -n "$(ls -A "${dst}" 2>/dev/null)" ]; then
        die "${dst} 非空，不敢覆盖。确认一下是已搬过（那就直接跑 data 阶段），还是要人工处理"
    fi
    log "搬运 ${vol} → ${dst} ..."
    # 借 mysql:8.0 镜像当 cp 载体（本机已有，且自带 sh 与正确 uid 999 语义）
    docker run --rm -v "${vol}:/from:ro" -v "${dst}:/to" mysql:8.0 sh -c \
        'cp -a /from/. /to/ && chown -R 999:999 /to'
    [ -e "${dst}/${marker}" ] || die "${dst} 里找不到 ${marker}，搬运结果不对，先别起容器"
    log "完成 ${dst}：$(du -sh "${dst}" | cut -f1)，校验标记 ${marker} 存在"
}

stage_tobind() {
    # 一次性搬迁：命名卷 → 宿主目录。compose 现在用 bind 挂载，老环境升上来必须先把数据搬出来，
    # 否则容器会挂着一个空目录，Flyway 当全新库从 V1 重跑（且当前 V7 在 MySQL 8 上必失败）。
    [ -f "${APP_DIR}/.env" ] || die "先跑 env 阶段"
    command -v docker >/dev/null || die "需要 docker"

    log "停止 app 与数据层（mysql 走正常 shutdown，避免 InnoDB 处于崩溃恢复态）"
    ${COMPOSE} stop family-tree mysql redis 2>/dev/null || ${COMPOSE} stop

    # 标记文件用 ibdata1 / appendonlydir：这两个在才说明数据文件真过去了，而不是空目录
    tobind_one family-tree-mysql /var/lib/mysql "${APP_DIR}/data/mysql" ibdata1 mysql-data
    tobind_one family-tree-redis /data "${APP_DIR}/data/redis" appendonlydir redis-data

    log "接下来：./ecs-setup.sh data && ./ecs-setup.sh app，跑通并确认数据在，再删旧卷腾空间："
    log "  docker volume rm <project>_mysql-data <project>_redis-data   # 先 docker volume ls 看清名字"
}

stage_repair() {
    # 只在「改动了已经应用过的迁移文件内容」之后跑：checksum 变了会让应用启动即失败（Flyway validate），
    # repair 只重写 flyway_schema_history 里的 checksum/description 并清掉失败行，不碰任何业务数据。
    [ -f "${APP_DIR}/.env" ] || die "先跑 env 阶段"
    [ -d "${MIGRATIONS_DIR}" ] || die "缺少 ${MIGRATIONS_DIR}：把与新镜像同一 commit 的 db/migration/mysql 整个传上来（不是同一份文件，repair 算出的 checksum 与镜像里的对不上）"
    docker inspect -f '{{.State.Running}}' "${MYSQL_CTR}" 2>/dev/null | grep -q true \
        || die "${MYSQL_CTR} 没在跑，先跑 data 阶段"
    local root_pw
    root_pw=$(grep -m1 '^MYSQL_ROOT_PASSWORD=' "${APP_DIR}/.env" | cut -d= -f2-)
    [ -n "${root_pw}" ] || die "取不到 MYSQL_ROOT_PASSWORD，检查 ${APP_DIR}/.env"

    docker image inspect "${FLYWAY_IMAGE}" >/dev/null 2>&1 || {
        log "拉取 ${FLYWAY_IMAGE}"
        docker pull "${FLYWAY_IMAGE}" >/dev/null || die "拉取失败：这台机器 dockerhub 直连不通，先按 README 离线准备该镜像"
    }

    local before after envf rc
    before=$(docker exec "${MYSQL_CTR}" sh -c \
        'exec mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SELECT checksum FROM family_tree.flyway_schema_history WHERE version=\"7\";"' 2>/dev/null || true)
    log "repair 前 V7 checksum = ${before:-无 V7 记录}"

    # 口令走 --env-file（0600，用完即删），不进宿主命令行、也不落进 docker inspect 的 argv
    envf=$(mktemp "${APP_DIR}/.repair.XXXXXX")
    chmod 600 "${envf}"
    {
        printf 'FLYWAY_URL=jdbc:mysql://127.0.0.1:3306/family_tree?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai\n'
        printf 'FLYWAY_USER=root\n'
        printf 'FLYWAY_PASSWORD=%s\n' "${root_pw}"
        printf 'FLYWAY_LOCATIONS=filesystem:/sql\n'
    } > "${envf}"
    # 借 mysql 容器的网络命名空间连 127.0.0.1:3306：不用猜 compose 的网络名，也不多开一个端口
    docker run --rm --network "container:${MYSQL_CTR}" --env-file "${envf}" \
        -v "${MIGRATIONS_DIR}:/sql:ro" "${FLYWAY_IMAGE}" repair
    rc=$?
    rm -f "${envf}"
    [ "${rc}" = "0" ] || die "repair 失败（exit ${rc}）"

    after=$(docker exec "${MYSQL_CTR}" sh -c \
        'exec mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SELECT checksum FROM family_tree.flyway_schema_history WHERE version=\"7\";"' 2>/dev/null || true)
    log "repair 后 V7 checksum = ${after:-无 V7 记录}，历史表共 $(docker exec "${MYSQL_CTR}" sh -c \
        'exec mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SELECT COUNT(*) FROM family_tree.flyway_schema_history;"' 2>/dev/null) 行"
    if [ -n "${EXPECT_V7_CHECKSUM}" ] && [ "${after}" != "${EXPECT_V7_CHECKSUM}" ]; then
        die "V7 checksum 是 ${after}，期望 ${EXPECT_V7_CHECKSUM} —— ${MIGRATIONS_DIR} 里的文件跟镜像里打包的不是同一份。重新上传对应 commit 的 db/migration/mysql 再跑；确认无误要跳过校验就 EXPECT_V7_CHECKSUM= ./ecs-setup.sh repair"
    fi
    log "接下来 ./ecs-setup.sh app 起新镜像；Flyway 校验通过就说明 checksum 对上了"
}

stage_status() {
    echo "===== 容器 ====="
    ${COMPOSE} ps 2>/dev/null || docker ps
    echo "===== 内存 ====="
    free -m
    echo "===== 监听 ====="
    ss -tlnp | grep -E ":80 |:8090|:3308|:6381" || echo "无"
    echo "===== 健康 ====="
    curl -s -o /dev/null -w "8090/actuator/health -> %{http_code}\n" --max-time 5 http://127.0.0.1:8090/actuator/health || true
    curl -s -o /dev/null -w "80/ -> %{http_code}\n" --max-time 5 http://127.0.0.1/ || true
    echo "===== 容器内存占用 ====="
    docker stats --no-stream --format "{{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}" || true
    echo "===== 宿主数据目录 ====="
    du -sh "${APP_DIR}"/data/* "${APP_DIR}/logs" 2>/dev/null || echo "无 data 目录"
    df -h / | tail -1
    echo "===== 最近备份 ====="
    ls -lt "${APP_DIR}/backups" 2>/dev/null | head -4 || echo "尚无备份（跑 backup 阶段安装 cron，或手工执行 backup.sh）"
}

SELF=$(cd "$(dirname "$0")" && pwd)/$(basename "$0")
cd /
case "${1:-}" in
    env) stage_env ;;
    images) stage_images ;;
    data) stage_data ;;
    import) stage_import ;;
    app) stage_app ;;
    nginx) stage_nginx ;;
    backup) stage_backup ;;
    tobind) stage_tobind ;;
    repair) stage_repair ;;
    status) stage_status ;;
    *) sed -n '4,22p' "${SELF}"; exit 1 ;;
esac
