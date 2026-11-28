#!/bin/sh
# ft-boot-recover — ECS 开机后确保 compose 数据面（mysql/redis）与 k3s 应用槽就位（幂等，可反复执行）
#
# 为什么需要：2026-11-28 19:04 重启实测「自动恢复」整条链路是坏的。docker 从持久状态恢复时，
# 会按重启前记录的网络 ID 重建网桥 br-<ID>；而 firewalld 的 permanent 配置里还留着同名绑定
# （容器网络早已重建、ID 变了），于是被判 ZONE_CONFLICT、网络建不出来，restart=unless-stopped
# 的两个数据面容器直接被跳过：
#   failed to create endpoint family-tree-redis on network family-tree_default:
#   network 98737a40d76d... does not exist
# 表现 = 全站 502 + 蓝槽 pod CrashLoopBackOff，必须人工救。
#
# 本脚本做三件事：
#   ① 补齐数据盘 bind 挂载（/data 掉线时拒绝启动数据面，避免 mysql 对着空目录初始化出一套新库）；
#   ② 清掉指向「已不存在的 docker 网络」的陈旧 permanent 绑定，并删掉无端口挂载的孤儿网桥（根因）；
#   ③ 按 compose up → docker start → 重连网络再 start → compose force-recreate 四级兜底拉起数据面，
#      确认 mysql healthy / redis PONG 后，再等当前在任的应用槽就绪（卡 BackOff 时对它 rollout restart）。
#
# 作用范围：只碰 family-tree 这一套（数据盘 bind、网络 family-tree_default、容器 family-tree-mysql / family-tree-redis、
# ns=prod 的 family-tree-app-blue / -green 里 replicas>0 的那个）。绝不启动 compose 里的 app 服务（那是退役的回滚容器），
# 也不动休眠槽 replicas=0（回滚锚）、不动 nginx、不动任何防火墙端口/服务策略。
#
# @author Family-Tree
# @date 2026-11-28
set -u

APP_DIR=/opt/family-tree
NET=family-tree_default
ZONE=trusted
MYSQL=family-tree-mysql
REDIS=family-tree-redis
LOG=$APP_DIR/logs/ft-boot-recover.log
NS=prod
# 2026-11-28 起数据面与日志/照片/备份都落在数据盘（vdb -> /data），这些宿主目录靠 fstab 的 bind 行就位
DATADISK=/data/family-tree
BINDS="logs data backup backups"
MYSQL_DATADIR=$APP_DIR/data/mysql
# 应用槽会随蓝绿切换换位在任方，两个端口都要探
SLOTS="8091:family-tree-app-blue 8092:family-tree-app-green"

log() {
    printf '%s %s\n' "$(date '+%F %T')" "$*" >>"$LOG"
    echo "$*"
}

compose() {
    docker compose --project-directory "$APP_DIR" -f "$APP_DIR/docker-compose.yml" "$@"
}

running() { docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "$1"; }

wait_docker() {
    i=0
    while [ $i -lt 60 ]; do
        docker info >/dev/null 2>&1 && return 0
        i=$((i + 1))
        sleep 2
    done
    return 1
}

# ① 根因处置：permanent 配置里残留的 br-* 绑定会让下次开机重建同名网桥时撞上 ZONE_CONFLICT
prune_stale_zone_bindings() {
    command -v firewall-cmd >/dev/null 2>&1 || { log "firewall-cmd 不存在，跳过绑定清理"; return 0; }
    firewall-cmd --state >/dev/null 2>&1 || { log "firewalld 未运行，跳过绑定清理"; return 0; }

    ids=$(docker network ls --no-trunc --format '{{.ID}}' 2>/dev/null)
    for iface in $(firewall-cmd --permanent --zone="$ZONE" --list-interfaces 2>/dev/null); do
        case "$iface" in br-*) ;; *) continue ;; esac
        nid=${iface#br-}
        case "$ids" in
            *"$nid"*)
                continue
                ;;
        esac

        if firewall-cmd --permanent --zone="$ZONE" --remove-interface="$iface" >/dev/null 2>&1; then
            log "已清除陈旧 permanent 绑定 $iface（对应 docker 网络已不存在）"
        else
            log "清除 permanent 绑定 $iface 失败，请手工 firewall-cmd --permanent --zone=$ZONE --remove-interface=$iface"
        fi
        firewall-cmd --zone="$ZONE" --remove-interface="$iface" >/dev/null 2>&1 || true

        # 网桥残留但没有任何端口挂载 → 删掉，避免下次开机抢同名接口
        if [ -e "/sys/class/net/$iface" ] && [ -z "$(ls -A "/sys/class/net/$iface/brif" 2>/dev/null)" ]; then
            if ip link set "$iface" down >/dev/null 2>&1 && ip link delete "$iface" >/dev/null 2>&1; then
                log "已删除孤儿网桥 $iface"
            else
                log "网桥 $iface 未能删除（可能有端口挂载或权限不足），留待人工"
            fi
        fi
    done
}

# ② 数据面拉起：四级兜底，任一级成功即止
ensure_up() {
    svc=$1
    cname=$2
    if running "$cname"; then
        log "$cname 已在运行"
        return 0
    fi

    log "$cname 未运行，开始四级兜底拉起"
    compose up -d "$svc" >>"$LOG" 2>&1 || docker start "$cname" >>"$LOG" 2>&1 || {
        docker network disconnect -f "$NET" "$cname" >/dev/null 2>&1
        docker network connect "$NET" "$cname" >/dev/null 2>&1
        docker start "$cname" >>"$LOG" 2>&1 || compose up -d --force-recreate "$svc" >>"$LOG" 2>&1
    }

    if running "$cname"; then
        log "$cname 已拉起"
        return 0
    fi
    log "$cname 四级兜底全部失败，需人工介入"
    return 1
}

wait_mysql() {
    i=0
    st=none
    while [ $i -lt 60 ]; do
        st=$(docker inspect -f '{{.State.Health.Status}}' "$MYSQL" 2>/dev/null || echo none)
        [ "$st" = healthy ] && { log "mysql 已 healthy"; return 0; }
        i=$((i + 1))
        sleep 5
    done
    log "mysql 300s 内未达 healthy（当前 $st）"
    return 1
}

wait_redis() {
    i=0
    while [ $i -lt 12 ]; do
        [ "$(docker exec "$REDIS" redis-cli ping 2>/dev/null)" = PONG ] && { log "redis 返回 PONG"; return 0; }
        i=$((i + 1))
        sleep 5
    done
    log "redis 60s 内未响应 PING"
    return 1
}

health_of() { curl -s -o /dev/null -w '%{http_code}' --max-time 8 "http://127.0.0.1:$1/actuator/health"; }

slot_want() { k3s kubectl -n "$NS" get "deployment/$1" -o 'jsonpath={.spec.replicas}' 2>/dev/null || echo 0; }

wait_app() {
    i=0
    while [ $i -lt 30 ]; do
        for slot in $SLOTS; do
            port=${slot%%:*}
            [ "$(health_of "$port")" = 200 ] && { log "应用槽 $port health=200"; return 0; }
        done
        i=$((i + 1))
        sleep 10
    done
    return 1
}

# 应用槽：数据面就绪后 kubelet 的 BackOff 通常会自行收敛；卡住才对「replicas>0 的在任槽」rollout restart。
# 在任方随蓝绿切流换位（2026-11-28 起是绿槽 8092），所以不能写死蓝槽；休眠槽 replicas=0 是回滚锚，绝不拉起。
ensure_app() {
    wait_app && return 0
    restarted=0
    for slot in $SLOTS; do
        dep=${slot#*:}
        want=$(slot_want "$dep")
        case "$want" in '' | *[!0-9]*) want=0 ;; esac
        [ "$want" -gt 0 ] || continue
        log "应用槽 300s 未就绪，对 $dep（replicas=$want）执行 rollout restart"
        k3s kubectl -n "$NS" rollout restart "deployment/$dep" >>"$LOG" 2>&1
        restarted=1
    done
    if [ "$restarted" -eq 0 ]; then
        log "两个应用槽 replicas 均为 0，无在任槽可催；请人工确认 nginx upstream 与 k3s kubectl -n $NS get deploy"
        return 1
    fi
    wait_app && return 0
    log "应用槽仍未就绪，需人工介入（k3s kubectl -n $NS get pods）"
    return 1
}

# 数据盘 bind：/data 或其上四个 bind 掉线时先补挂（fstab 有 nofail，数据盘故障不会挡开机，得在这里判定）
ensure_datadisk_mounts() {
    grep -q ' /data ext4 ' /proc/mounts || { log "数据盘 /data 未挂载（fstab 的 /dev/vdb1 行没生效）"; return 1; }
    ok=1
    for d in $BINDS; do
        grep -q " $APP_DIR/$d " /proc/mounts && continue
        mkdir -p "$DATADISK/$d" "$APP_DIR/$d"
        if mount --bind "$DATADISK/$d" "$APP_DIR/$d" >>"$LOG" 2>&1; then
            log "已补挂 bind $APP_DIR/$d <- $DATADISK/$d"
        else
            log "补挂 bind $APP_DIR/$d 失败"
            ok=0
        fi
    done
    return $((1 - ok))
}

# 空 datadir 是这台机器上最贵的事故：mysqld 会安静初始化出一套全新空库并正常启动（见 README 已知限制）
guard_mysql_datadir() {
    [ -f "$MYSQL_DATADIR/ibdata1" ] || { log "致命：$MYSQL_DATADIR/ibdata1 不存在，数据盘上的 datadir 没就位，拒绝启动 mysql"; return 1; }
}

mkdir -p "$APP_DIR/logs"
log "=== ft-boot-recover 开始 ==="
command -v docker >/dev/null 2>&1 || { log "docker 不在 PATH，放弃"; exit 1; }
wait_docker || { log "docker daemon 未就绪，放弃"; exit 1; }

prune_stale_zone_bindings
ensure_datadisk_mounts || log "数据盘 bind 未全部就位，按现有挂载继续"
guard_mysql_datadir || { log "=== ft-boot-recover 中止，返回码 1 ==="; exit 1; }

rc=0
ensure_up mysql "$MYSQL" || rc=1
ensure_up redis "$REDIS" || rc=1
if [ $rc -ne 0 ]; then
    log "数据面未全部就位，跳过应用槽检查"
    exit 1
fi

wait_mysql || rc=1
wait_redis || rc=1
if [ $rc -ne 0 ]; then
    log "数据面未就绪，跳过应用槽检查"
    exit 1
fi

ensure_app || rc=1
log "=== ft-boot-recover 结束，返回码 $rc ==="
exit $rc
