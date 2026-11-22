#!/bin/sh
# ft-firewall-guard — 把「跑着 family-tree 容器的 docker 网桥」绑进 firewalld trusted 区
#
# 为什么需要：firewalld 重启/重载后，网桥不再属于 trusted 区，转发流量落进 default zone（public），
# 命中 filter_FORWARD_IN_ZONES → filter_FWDI_public → filter_FWDI_public_deny（reject）
# → app 容器连 mysql/redis 的 TCP 全部被拒（ICMP 不受影响，所以 ping 得通、连不上）。
# 2026-11-22 12:10 宝塔面板重启 firewalld 触发过一次，表现为「网站能打开、接口卡 30s」。
#
# 为什么不做 --permanent：网桥名随网络重建变化（br-<网络ID>），permanent 里写死的名字下次不一定是它。
# 本脚本只做运行时绑定（每次幂等），由 systemd timer 定期兜底。
#
# 作用范围：只挑「名字以 family-tree 开头」或「挂着 family-tree* 容器」的网络，
# 其它容器网络的网桥（如宝塔的 baota_net）一律不动。也不改主机侧 INPUT/端口/服务策略。
set -u

log() { echo "$(date '+%F %T') $*"; }

command -v docker >/dev/null 2>&1 || { log "docker 不在 PATH，跳过"; exit 0; }
command -v firewall-cmd >/dev/null 2>&1 || { log "firewalld 未安装，无需处理"; exit 0; }
firewall-cmd --state >/dev/null 2>&1 || { log "firewalld 未运行，容器间不受影响"; exit 0; }

# 每张网络一行：网络ID 网络名 该网络上的容器名（分号分隔）
inv=$(docker network inspect -f '{{.Name}} {{range .Containers}}{{.Name}};{{end}}' $(docker network ls -q) 2>/dev/null)
ids=$(docker network ls --format '{{.ID}} {{.Name}}' 2>/dev/null)
[ -n "$ids" ] || { log "docker network ls 无输出，跳过"; exit 0; }

rc=0
targets=0
# 用换行分字段，逐个网络判定（IFS 只在循环内临时改，不影响外部）
while IFS=' ' read -r id name; do
    [ -n "$id" ] || continue
    if [ "$name" = host ] || [ "$name" = none ]; then
        continue
    fi

    # 该网络上是否有 family-tree 容器
    has_ft=0
    case "$name" in family-tree*) has_ft=1 ;; esac
    line=$(printf '%s\n' "$inv" | awk -v n="$name" '$1==n {print; exit}')
    case "$line" in *family-tree*) has_ft=1 ;; esac
    [ "$has_ft" -eq 1 ] || continue

    # 网络对应的网桥接口：默认 bridge 网络是 docker0，用户自定义网络是 br-<12位ID>
    if [ "$name" = bridge ]; then
        iface=docker0
    else
        iface=br-$id
    fi
    [ -e "/sys/class/net/$iface" ] || { log "$name: 找不到网桥 $iface（可能是非 bridge 驱动），跳过"; continue; }

    targets=$((targets + 1))
    # 注意：--query-interface 只回 yes/no（且这台 firewalld 上对 trusted 绑定的接口也回 no），
    # 要看归属 zone 得用 --get-zone-of-interface。
    cur=$(firewall-cmd --get-zone-of-interface="$iface" 2>/dev/null || true)
    case "$cur" in
        trusted)
            log "$name: $iface 已在 trusted，无需改动"
            ;;
        *)
            if firewall-cmd --change-interface="$iface" --zone=trusted >/dev/null 2>&1; then
                log "$name: 已绑定 $iface -> trusted（原先为 ${cur:-无 zone}）"
            else
                log "$name: 绑定 $iface 失败，请手工 firewall-cmd --change-interface=$iface --zone=trusted"
                rc=1
            fi
            ;;
    esac
done <<EOF
$ids
EOF

log "完成：检查了 $targets 张 family-tree 相关网桥，返回码 $rc"
exit $rc
