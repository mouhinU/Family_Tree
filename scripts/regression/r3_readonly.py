"""R3 只读接口 + R5 权限矩阵电池：登录态、家族/族谱读接口、平台管理入口与匿名拦截。

@author Family-Tree
@date 2026-11-17
"""
import ft_reg_common as reg

from ft_reg_common import BASE, MEMBER_PWD, MEMBER_USER, OWNER_PWD, OWNER_USER

b = None


def flatten(nodes):
    out = []

    def walk(n):
        out.append(n)
        for c in n.get("children") or []:
            walk(c)

    for n in nodes:
        walk(n)
    return out


def main(battery):
    global b
    b = battery
    b.sessions["OWNER"] = reg.Session("OWNER")
    b.sessions["MEMBER"] = reg.Session("MEMBER")
    b.sessions["ANON"] = reg.Session("ANON")

    # ---------- R3 认证与会话 ----------
    r = b.sessions["OWNER"].login(OWNER_USER, OWNER_PWD)
    b.raw_call("R3", "-", "POST", "/api/auth/login", r.get("code") == 200, "族长登录",
               "got=%s %s" % (r.get("code"), str(r.get("message"))[:60]))
    mr = b.sessions["MEMBER"].login(MEMBER_USER, MEMBER_PWD)
    b.raw_call("R3", "-", "POST", "/api/auth/login", mr.get("code") == 200, "成员登录",
               "got=%s %s" % (mr.get("code"), str(mr.get("message"))[:60]))
    bad = b.sessions["ANON"].call("POST", "/api/auth/login",
                                  {"username": OWNER_USER, "password": "wrong-pass"})[1]
    b.raw_call("R3", "-", "POST", "/api/auth/login", bad.get("code") != 200, "错误口令被拒",
               "got=%s" % bad.get("code"))
    b.check("R3", "OWNER", "GET", "/api/auth/me", 200, note="登录后 me 可读")
    b.check("R3", "ANON", "GET", "/api/auth/me", 401, note="未登录 me 拒绝")

    # ---------- R4 族谱主链路读接口 ----------
    family = b.check("R4", "OWNER", "GET", "/api/family", 200, note="家族详情")
    fid = (family or {}).get("data", {}).get("id")
    b.val("familyId", fid)
    b.check("R4", "OWNER", "GET", "/api/family/members?page=1&size=50", 200, note="成员分页(管理员)")
    b.check("R4", "MEMBER", "GET", "/api/family/members?page=1&size=5", 200, note="成员分页(非管理员)")
    b.check("R4", "OWNER", "GET", "/api/family/my-list", 200, note="我的家族列表")

    tree = b.check("R4", "OWNER", "GET", "/api/tree/full", 200, note="整树")
    flat = flatten((tree or {}).get("data") or [])
    # TreeNodeVO 用 deathDate 表达已故，没有单独的 deceased 字段
    nid_any = next((n["id"] for n in flat if n.get("id")), None)
    nid_alive = next((n["id"] for n in flat if n.get("id") and not n.get("deathDate")), None)
    nid_dead = next((n["id"] for n in flat if n.get("deathDate")), None)
    b.val("nodeAny", nid_any)
    b.val("nodeAlive", nid_alive)
    b.val("nodeDead", nid_dead)
    print("# base=%s family=%s tree_nodes=%d any=%s alive=%s dead=%s"
          % (BASE, fid, len(flat), nid_any, nid_alive, nid_dead))

    b.check("R4", "OWNER", "GET", "/api/tree/detail-scope", 200, note="可见范围(管理员)")
    b.check("R4", "MEMBER", "GET", "/api/tree/detail-scope", 200, note="可见范围(非管理员)")
    b.check("R4", "OWNER", "GET", "/api/node/list?page=1&size=10", 200, note="节点列表分页")
    b.check("R4", "OWNER", "GET", "/api/node/search?keyword=%E8%92%99", 200, note="节点搜索")
    b.check("R4", "OWNER", "GET", "/api/relation/all", 200, note="全部关系")
    b.check("R4", "OWNER", "GET", "/api/generation", 200, note="辈分列表")
    b.check("R4", "OWNER", "GET", "/api/generation/layout", 200, note="辈分排布")
    if nid_any:
        b.check("R4", "OWNER", "GET", "/api/node/%s" % nid_any, 200, note="节点基础")
        b.check("R4", "OWNER", "GET", "/api/node/%s/detail" % nid_any, 200, note="节点详情")
        b.check("R4", "OWNER", "GET", "/api/relation/node/%s" % nid_any, 200, note="节点关系列表")
        b.check("R4", "OWNER", "GET", "/api/biography/%s" % nid_any, 200, note="人物小传")
        b.check("R4", "OWNER", "GET", "/api/version/node/%s/history?page=1&size=5" % nid_any, 200,
                note="节点版本历史")

    # 关系路径需要起止两个节点：起点取自己绑定的「我的位置」，终点取同一棵树内的任一他人节点
    my_node = (b.check("R3", "OWNER", "GET", "/api/auth/me", 200, note="me 返回绑定节点",
                       assert_fn=lambda d, r: d.get("nodeId") is not None) or {}).get("data", {})
    b.val("myNodeOwner", my_node.get("nodeId"))
    target_node = None
    for root_node in (tree or {}).get("data") or []:
        ids = [n["id"] for n in flatten([root_node]) if n.get("id")]
        if my_node.get("nodeId") in ids:
            # 跨树节点之间本就无可达路径，断言 found=true 只能取同一棵树里的节点
            target_node = next((i for i in ids if i != my_node["nodeId"]), None)
            break
    if my_node.get("nodeId") and target_node:
        b.check("R4", "OWNER",
                "GET", "/api/relation-path?fromNodeId=%s&toNodeId=%s" % (my_node["nodeId"], target_node),
                200, note="关系路径(我的位置->同树目标)",
                assert_fn=lambda d, r: (d or {}).get("found") is True)
    if nid_dead:
        b.check("R4", "OWNER", "GET", "/api/memorial/node/%s" % nid_dead, 200, note="悼念页签")
        b.check("R4", "OWNER", "GET", "/api/offering/node/%s" % nid_dead, 200, note="供奉统计")

    # ---------- R9 既有功能读接口 ----------
    b.check("R9", "OWNER", "GET", "/api/timeline", 200, note="时间线")
    b.check("R9", "OWNER", "GET", "/api/message?page=1&size=10", 200, note="家族留言")
    b.check("R9", "OWNER", "GET", "/api/forum", 200, note="家族圈")
    b.check("R9", "OWNER", "GET", "/api/notification/list?page=1&size=10", 200, note="通知列表")
    b.check("R9", "OWNER", "GET", "/api/notification/unread-count", 200, note="未读数")
    b.check("R9", "OWNER", "GET", "/api/anniversary", 200, note="纪念日")
    b.check("R9", "OWNER", "GET", "/api/birthday", 200, note="生日提醒")
    b.check("R9", "OWNER", "GET", "/api/death-anniversary", 200, note="忌日提醒")
    b.check("R9", "OWNER", "GET", "/api/event", 200, note="活动列表")
    b.check("R9", "OWNER", "GET", "/api/photo", 200, note="相册列表")
    b.check("R9", "OWNER", "GET", "/api/private-message/conversations", 200, note="会话列表")
    b.check("R9", "OWNER", "GET", "/api/private-message/unread-count", 200, note="私聊未读数")
    contacts = b.check("R9", "OWNER", "GET", "/api/private-message/contacts", 200, note="私信联系人")
    # 联系人以 family_member 为准：同族另一名账号必须在列表内（D-03 口径）
    b.raw_call("R9", "OWNER", "GET", "/api/private-message/contacts",
               bool(contacts) and any(c.get("id") == b.sessions["MEMBER"].user_id
                                      for c in (contacts.get("data") or [])),
               "联系人含同族成员账号",
               "data=%s" % str((contacts or {}).get("data"))[:80])

    # ---------- D-04：邀请链接与库内真码同源 ----------
    link = b.check("R4", "OWNER", "GET", "/api/invite/link", 200, note="邀请链接可读")
    db_code = (family or {}).get("data", {}).get("inviteCode")
    link_code = ((link or {}).get("data") or {}).get("inviteCode")
    link_url = ((link or {}).get("data") or {}).get("inviteUrl") or ""
    b.raw_call("R4", "OWNER", "GET", "/api/invite/link", bool(db_code) and db_code == link_code,
               "链接码与库内邀请码同源", "db=%s link=%s" % (db_code, link_code))
    b.raw_call("R4", "OWNER", "GET", "/api/invite/link", "?invite=%s" % (db_code or "") in link_url,
               "邀请链接带真实码参数", "url=%s" % link_url[:90])
    qr = b.sessions["OWNER"].raw("GET", "/api/invite/qrcode")
    b.raw_call("R4", "OWNER", "GET", "/api/invite/qrcode", qr[0] == 200 and qr[1][:8] == b"\x89PNG\r\n\x1a\n",
               "二维码返回 PNG 图片", "http=%s len=%d" % (qr[0], len(qr[1])))

    # ---------- R5 权限矩阵 ----------
    b.check("R5", "MEMBER", "GET", "/api/admin/overview", [400, 403], note="非白名单访问平台管理")
    b.check("R5", "MEMBER", "GET", "/api/admin/families?page=1&size=5", [400, 403], note="非白名单家族列表")
    b.check("R5", "MEMBER", "GET", "/api/admin/inspection", [400, 403], note="非白名单巡检")
    b.check("R5", "MEMBER", "GET", "/api/node/list?page=1&size=5", 200, note="成员读节点列表")
    b.check("R5", "MEMBER", "GET", "/api/operation-log?page=1&size=5", [200, 400, 403],
            note="成员读操作日志(按角色)")
    b.check("R5", "ANON", "GET", "/api/tree/full", [401, 403], note="匿名读整树")
    b.check("R5", "ANON", "GET", "/api/family", [401, 403], note="匿名读家族")
    b.check("R5", "OWNER", "GET", "/api/admin/overview", 200, note="白名单平台概况")
    b.check("R5", "OWNER", "GET", "/api/admin/families?page=1&size=5", 200, note="白名单家族列表")
    b.check("R5", "OWNER", "GET", "/api/admin/users?page=1&size=5", 200, note="白名单用户列表")
    b.check("R5", "OWNER", "GET", "/api/admin/logs?page=1&size=5", 200, note="平台日志检索")
    b.check("R5", "OWNER", "GET", "/api/admin/log-types", 200, note="日志类型字典")
    b.check("R5", "OWNER", "GET", "/api/admin/inspection", 200, note="数据巡检")
    b.check("R5", "OWNER", "GET", "/api/admin/platform-settings", 200, note="平台配置")
    if fid:
        b.check("R5", "OWNER", "GET", "/api/admin/family/%s" % fid, 200, note="家族概况详情")
        b.check("R5", "OWNER", "GET", "/api/admin/family/%s/settings" % fid, 200, note="家族配置生效链")

    # ---------- R8 家族日志 / 快照 ----------
    b.check("R8", "OWNER", "GET", "/api/operation-log?page=1&size=10", 200, note="家族操作日志")
    b.check("R8", "OWNER", "GET", "/api/version/snapshot/list", 200, note="快照列表")

    # ---------- D-08 未知路径语义 ----------
    st, res = b.sessions["OWNER"].call("GET", "/api/definitely-not-an-endpoint")
    b.raw_call("R8", "OWNER", "GET", "/api/definitely-not-an-endpoint", res.get("code") == 404,
               "未知 API 路径应 404", "http=%s code=%s" % (st, res.get("code")), known="D-08")
    st, res = b.sessions["OWNER"].call("GET", "/api/node/not-a-number")
    b.raw_call("R8", "OWNER", "GET", "/api/node/{id}", res.get("code") == 400,
               "路径参数类型错误应 400", "http=%s code=%s" % (st, res.get("code")), known="D-08")

    b.sessions["OWNER"].call("POST", "/api/auth/logout", csrf=True)


reg.run_main(main, "R3/R4/R5 只读与权限电池")
