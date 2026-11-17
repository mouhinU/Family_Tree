"""R6 三级配置生效链 + R7 家族治理 + R10 注册建族与一人一家族电池。

平台管理、开关、邀请码加入、成员角色、账号口令生命周期。只允许打本地 scratch 环境。

@author Family-Tree
@date 2026-11-17
"""
import ft_reg_common as reg

from ft_reg_common import MEMBER_PWD, MEMBER_USER, OWNER_PWD, OWNER_USER, TEST_PWD

# 平台日志检索分页大小
LOG_PAGE_SIZE = 10
# 登录失败锁定阈值（LoginSecurityConsts.MAX_FAILED_ATTEMPTS）
LOGIN_FAIL_LOCK_ATTEMPTS = 5
# 首登改密后的新口令（仅 scratch 测试账号）
CHANGED_PWD = "Changed@2026"


def family_settings(b, who, family_id):
    """读取某家族的三级生效链视图，返回 配置键 → 配置行。"""
    _, res = b.sessions[who].call("GET", "/api/admin/family/%s/settings" % family_id)
    return {x.get("key"): x for x in res.get("data") or []}


def platform_settings(b):
    """读取平台层配置视图，返回 配置键 → 配置行。"""
    _, res = b.sessions["OWNER"].call("GET", "/api/admin/platform-settings")
    return {x.get("key"): x for x in res.get("data") or []}


def main(b):
    owner = b.session("OWNER")
    member = b.session("MEMBER")
    owner.login(OWNER_USER, OWNER_PWD)
    member.login(MEMBER_USER, MEMBER_PWD)
    fid = owner.family_id

    # ---------- R10 注册 → 创建家族（v50 初始节点特性） ----------
    user_a = b.session("regv50a")
    user_a.register("regv50a")
    me = user_a.call("GET", "/api/auth/me")[1]
    b.raw_call("R10", "regv50a", "GET", "/api/auth/me",
               me.get("code") == 200 and me.get("data").get("hasFamily") is False,
               "新账号未加入家族", "got=%s data=%s" % (me.get("code"), str(me.get("data"))[:60]))
    fam_a = b.check("R10", "regv50a", "POST", "/api/family", 200,
                    {"name": "回归家族A", "initialNodeName": "回归始祖甲", "initialNodeGender": 1,
                     "initialNodeBirthDate": "1988-02-03"}, note="创建家族+初始节点(选填全填)")
    fid_a = b.val("familyA", ((fam_a or {}).get("data") or {}).get("familyId"))
    b.check("R10", "regv50a", "GET", "/api/tree/full", 200, note="族谱含初始节点",
            assert_fn=lambda d, r: any(n.get("name") == "回归始祖甲" for n in (d or [])))
    b.check("R10", "regv50a", "GET", "/api/node/list?page=1&size=10", 200, note="节点列表 1 条",
            assert_fn=lambda d, r: len(d if isinstance(d, list) else (d or {}).get("records", [])) == 1)
    b.check("R10", "regv50a", "GET", "/api/auth/me", 200, note="me 返回家族与绑定",
            assert_fn=lambda d, r: d.get("hasFamily") is True and d.get("nodeId") is not None)
    b.check("R10", "regv50a", "GET", "/api/family/members?page=1&size=10", 200, note="成员=1 且角色 OWNER",
            assert_fn=lambda d, r: (d or {}).get("total") == 1 and d["records"][0].get("role") == "OWNER")

    user_b = b.session("regv50b")
    user_b.register("regv50b")
    fam_b = b.check("R10", "regv50b", "POST", "/api/family", 200, {"name": "回归家族B"},
                    note="创建家族(不填初始节点)")
    fid_b = b.val("familyB", ((fam_b or {}).get("data") or {}).get("familyId"))
    b.check("R10", "regv50b", "GET", "/api/tree/full", 200, note="未填初始节点时族谱为空",
            assert_fn=lambda d, r: len(d or []) == 0)

    user_c = b.session("regv50c")
    user_c.register("regv50c")
    b.check("R10", "regv50c", "POST", "/api/family", 400,
            {"name": "回归家族C", "initialNodeName": "X" * 51}, note="初始节点姓名超长(>50)被 @Valid 拒")
    b.check("R10", "regv50c", "POST", "/api/family", 400,
            {"name": "回归家族C", "initialNodeName": "非法日期", "initialNodeBirthDate": "1988-13-99"},
            note="初始节点日期非法被拒（同事务回滚，未落家族）")
    b.check("R10", "regv50c", "POST", "/api/family", 200,
            {"name": "回归家族C", "initialNodeName": "   "}, note="初始节点空白姓名=不建节点")
    b.check("R10", "regv50c", "GET", "/api/tree/full", 200, note="空白姓名族谱仍为空",
            assert_fn=lambda d, r: len(d or []) == 0)

    # trim 用例必须用尚未建族的账号：一人只能有一个家族，第二次建族会被拒
    user_d = b.session("regv50d")
    user_d.register("regv50d")
    b.check("R10", "regv50d", "POST", "/api/family", 200,
            {"name": "回归家族D", "initialNodeName": "  trim 测试  "}, note="初始节点姓名 trim")
    b.check("R10", "regv50d", "GET", "/api/tree/full", 200, note="trim 后姓名无首尾空格",
            assert_fn=lambda d, r: (d or [{}])[0].get("name") == "trim 测试")

    # ---------- R10/R7 一人一家族：重复建族、跨族切换均被拒 ----------
    b.check("R10", "regv50d", "POST", "/api/family", 400, {"name": "回归家族D2"},
            note="已属家族时再建族被拒")
    b.check("R10", "regv50d", "PUT", "/api/family/switch/%s" % fid, 400,
            note="切换到不属的家族被拒")
    b.check("R10", "regv50d", "GET", "/api/family/my-list", 200, note="我的家族列表=1",
            assert_fn=lambda d, r: len(d or []) == 1)

    # ---------- R7 邀请码加入（真码端到端） ----------
    link_a = b.check("R7", "regv50a", "GET", "/api/invite/link", 200, note="家族A 邀请链接")
    code_a = b.val("familyAInviteCode", ((link_a or {}).get("data") or {}).get("inviteCode"))
    fam_a_row = (user_a.call("GET", "/api/family")[1].get("data") or {})
    b.raw_call("R7", "regv50a", "GET", "/api/invite/link",
               bool(code_a) and code_a == fam_a_row.get("inviteCode"),
               "链接码与 family.invite_code 同源（D-04）",
               "link=%s db=%s" % (code_a, fam_a_row.get("inviteCode")))
    b.raw_call("R7", "regv50a", "GET", "/api/invite/link",
               "?invite=%s" % code_a in str(((link_a or {}).get("data") or {}).get("inviteUrl")),
               "邀请链接为含真码的可访问地址（D-04）",
               "url=%s" % ((link_a or {}).get("data") or {}).get("inviteUrl"))

    invitee = b.session("regv50e")
    invitee.register("regv50e")
    b.check("R7", "regv50e", "POST", "/api/family/join", 400, {"inviteCode": "NO-SUCH-CODE"},
            note="无效邀请码被拒")
    b.check("R7", "regv50e", "POST", "/api/family/join", 200, {"inviteCode": code_a},
            note="凭家族A 真码加入")
    b.check("R7", "regv50e", "GET", "/api/family", 200, note="加入后读到家族A",
            assert_fn=lambda d, r: d.get("id") == fid_a)
    b.check("R7", "regv50a", "GET", "/api/family/members?page=1&size=50", 200, note="家族A 成员=2",
            assert_fn=lambda d, r: (d or {}).get("total") == 2)
    b.check("R7", "regv50e", "POST", "/api/family/join", 400, {"inviteCode": code_a},
            note="已在族内时重复加入被拒")

    guest = b.session("regv50f")
    gr = guest.register("regv50f", inviteCode=code_a)
    b.raw_call("R7", "ANON", "POST", "/api/auth/register", gr.get("code") == 200,
               "带邀请码注册成功", "got=%s %s" % (gr.get("code"), str(gr.get("message"))[:60]))
    guest_me = guest.call("GET", "/api/auth/me")[1]
    b.raw_call("R7", "regv50f", "GET", "/api/auth/me",
               guest_me.get("code") == 200 and (guest_me.get("data") or {}).get("familyId") == fid_a,
               "邀请注册直接进入家族A", "familyId=%s expect=%s"
               % ((guest_me.get("data") or {}).get("familyId"), fid_a))
    b.raw_call("R7", "regv50f", "GET", "/api/auth/me",
               guest_me.get("code") == 200 and not (guest_me.get("data") or {}).get("mustChangePassword"),
               "自助设置口令不触发首登强制改密", "mustChange=%s"
               % (guest_me.get("data") or {}).get("mustChangePassword"))

    # 成员角色治理（家族A 内，非族长不可操作）
    b.check("R7", "regv50e", "DELETE", "/api/family/member/%s" % guest.user_id, 400,
            note="非族长移除成员被拒")
    b.check("R7", "regv50e", "PUT", "/api/family/member/role", 400,
            {"userId": invitee.user_id, "role": "ADMIN"}, note="非族长设置角色被拒")
    b.check("R7", "regv50a", "PUT", "/api/family/member/role", 200,
            {"userId": invitee.user_id, "role": "ADMIN"}, note="族长把成员升为 ADMIN")
    b.check("R7", "regv50a", "GET", "/api/family/members?page=1&size=50", 200, note="角色已生效",
            assert_fn=lambda d, r: any(x.get("userId") == invitee.user_id and x.get("role") == "ADMIN"
                                       for x in d.get("records", [])))
    b.check("R7", "regv50a", "PUT", "/api/family/member/role", 200,
            {"userId": invitee.user_id, "role": "MEMBER"}, note="取消 ADMIN")
    b.check("R7", "regv50a", "DELETE", "/api/family/member/%s" % guest.user_id, 200, note="族长移除成员")
    b.check("R7", "regv50a", "GET", "/api/family/members?page=1&size=50", 200, note="成员回到 2",
            assert_fn=lambda d, r: (d or {}).get("total") == 2)

    # 邀请码刷新：新码即库内真码，旧码随即失效
    refreshed = b.check("R7", "regv50a", "PUT", "/api/family/invite-code", 200, note="族长刷新邀请码")
    new_code = ((refreshed or {}).get("data") or {}).get("inviteCode")
    db_code = (user_a.call("GET", "/api/auth/me")[1].get("data") or {}).get("inviteCode")
    b.raw_call("R7", "regv50a", "GET", "/api/auth/me", bool(new_code) and new_code == db_code,
               "刷新后的码即库内真码（D-04）", "put=%s db=%s" % (new_code, db_code))
    late = b.session("regv50g")
    late.register("regv50g")
    b.check("R7", "regv50g", "POST", "/api/family/join", 400, {"inviteCode": code_a},
            note="刷新后旧码失效")
    b.val("familyAInviteCode", new_code)
    b.check("R7", "regv50g", "POST", "/api/family/join", 200, {"inviteCode": new_code},
            note="新码可加入")

    # 主家族人数已达 MAX_FAMILY_MEMBERS 时按设计拒绝加入（可配置化是缺陷 D-07）
    fam_main = (owner.call("GET", "/api/auth/me")[1].get("data") or {})
    main_code = fam_main.get("inviteCode")
    capped = b.session("regcap1")
    capped.register("regcap1")
    cap_res = capped.call("POST", "/api/family/join", {"inviteCode": main_code}, csrf=True)[1]
    b.raw_call("R7", "regcap1", "POST", "/api/family/join",
               cap_res.get("code") in (200, 400)
               and (cap_res.get("code") == 200 or "上限" in str(cap_res.get("message"))),
               "加入主家族：要么成功要么受成员上限拦截",
               "got=%s %s" % (cap_res.get("code"), str(cap_res.get("message"))[:60]))

    # ---------- R7 家族信息治理（堂号/祖籍，仅家族管理员） ----------
    fam_before = (user_a.call("GET", "/api/family")[1].get("data") or {})
    b.check("R7", "regv50e", "PUT", "/api/family/info", 400, {"hallName": "越权堂号"},
            note="普通成员改堂号被拒")
    b.check("R7", "regv50a", "PUT", "/api/family/info", 200,
            {"hallName": "回归堂号", "ancestralHome": "回归故里"}, note="族长改堂号/祖籍")
    b.check("R7", "regv50a", "GET", "/api/family", 200, note="读回一致",
            assert_fn=lambda d, r: d.get("hallName") == "回归堂号" and d.get("ancestralHome") == "回归故里")
    b.check("R7", "regv50a", "PUT", "/api/family/info", 200,
            {"hallName": fam_before.get("hallName") or "", "ancestralHome": ""}, note="还原堂号/祖籍")

    # ---------- R6 三级生效链（家族覆盖 → 平台覆盖 → 代码默认），全部在电池家族上跑 ----------
    plat0 = platform_settings(b)
    fam0 = family_settings(b, "OWNER", fid_a)
    b.raw_call("R6", "OWNER", "GET", "/api/admin/family/{fid}/settings", "MAX_NODE_COUNT" in fam0,
               "家族生效链可读", "keys=%s" % list(fam0)[:6])
    b.raw_call("R6", "OWNER", "GET", "/api/admin/family/{fid}/settings",
               fam0["MAX_NODE_COUNT"].get("overridden") is False
               and fam0["MAX_NODE_COUNT"].get("value") == plat0["MAX_NODE_COUNT"].get("value"),
               "未覆盖时家族生效值=平台生效值", "fam=%s plat=%s"
               % (fam0.get("MAX_NODE_COUNT"), plat0.get("MAX_NODE_COUNT")))

    b.check("R6", "OWNER", "PUT", "/api/admin/family/%s/settings" % fid_a, 200,
            {"settings": {"MAX_NODE_COUNT": "1234"}}, note="家族覆盖=1234")
    fam1 = family_settings(b, "OWNER", fid_a)
    b.raw_call("R6", "OWNER", "GET", "/api/admin/family/{fid}/settings",
               fam1["MAX_NODE_COUNT"].get("value") == "1234"
               and fam1["MAX_NODE_COUNT"].get("overridden") is True,
               "家族覆盖值与覆盖标记生效", "row=%s" % fam1.get("MAX_NODE_COUNT"))
    b.check("R6", "OWNER", "PUT", "/api/admin/platform-settings", 200,
            {"settings": {"MAX_NODE_COUNT": "30000"}}, note="平台覆盖=30000")
    fam2 = family_settings(b, "OWNER", fid_a)
    b.raw_call("R6", "OWNER", "GET", "/api/admin/family/{fid}/settings",
               fam2["MAX_NODE_COUNT"].get("value") == "1234"
               and fam2["MAX_NODE_COUNT"].get("defaultValue") == "30000",
               "家族覆盖优先于平台覆盖，默认值列跟随平台", "row=%s" % fam2.get("MAX_NODE_COUNT"))
    plat2 = platform_settings(b)
    b.raw_call("R6", "OWNER", "GET", "/api/admin/platform-settings",
               plat2["MAX_NODE_COUNT"].get("value") == "30000"
               and plat2["MAX_NODE_COUNT"].get("overridden") is True,
               "平台覆盖行可读", "row=%s" % plat2.get("MAX_NODE_COUNT"))
    if fid_b:
        fam_b2 = family_settings(b, "OWNER", fid_b)
        b.raw_call("R6", "OWNER", "GET", "/api/admin/family/{fid}/settings",
                   fam_b2["MAX_NODE_COUNT"].get("value") == "30000"
                   and fam_b2["MAX_NODE_COUNT"].get("overridden") is False,
                   "无家族覆盖时继承平台覆盖", "row=%s" % fam_b2.get("MAX_NODE_COUNT"))
    b.check("R6", "MEMBER", "PUT", "/api/admin/family/%s/settings" % fid_a, 400,
            {"settings": {"MAX_NODE_COUNT": "10"}}, note="非白名单不能写家族配置")
    b.check("R6", "OWNER", "PUT", "/api/admin/family/%s/settings" % fid_a, 400,
            {"settings": {"MAX_NODE_COUNT": "9"}}, note="低于 minValue 被拒")
    b.check("R6", "OWNER", "PUT", "/api/admin/family/%s/settings" % fid_a, 400,
            {"settings": {"NOT_A_KEY": "1"}}, note="未登记键被拒")

    # 空值在两级都应被拒绝：改回默认提交的是默认值本身，而不是清空
    b.check("R6", "OWNER", "PUT", "/api/admin/family/%s/settings" % fid_a, 400,
            {"settings": {"MAX_NODE_COUNT": ""}}, note="家族层空值被拒（须提交默认值）")
    b.check("R6", "OWNER", "PUT", "/api/admin/platform-settings", 400,
            {"settings": {"MAX_NODE_COUNT": ""}}, note="平台层空值被拒（须提交默认值）")

    # D-05：家族层提交默认值应删除覆盖行、回到继承平台值
    # D-05：家族层提交默认值会返回成功，但覆盖行并未删除，生效值仍停在家族覆盖上
    # 因此写接口按正例断言，读回链保持 known="D-05"，修复后去掉 known 即为正例守护
    fam_default = family_settings(b, "OWNER", fid_a)["MAX_NODE_COUNT"].get("defaultValue")
    b.check("R6", "OWNER", "PUT", "/api/admin/family/%s/settings" % fid_a, 200,
            {"settings": {"MAX_NODE_COUNT": fam_default}},
            note="家族层提交生效默认值=%s" % fam_default)
    fam_reset = family_settings(b, "OWNER", fid_a)
    b.raw_call("R6", "OWNER", "GET", "/api/admin/family/{fid}/settings",
               fam_reset["MAX_NODE_COUNT"].get("overridden") is False
               and fam_reset["MAX_NODE_COUNT"].get("value") == "30000",
               "家族覆盖删除后继承平台覆盖", "row=%s" % fam_reset.get("MAX_NODE_COUNT"), known="D-05")

    # 平台层提交默认值即删除覆盖行（现已实现，作为正例守护）
    plat_default = platform_settings(b)["MAX_NODE_COUNT"].get("defaultValue")
    b.check("R6", "OWNER", "PUT", "/api/admin/platform-settings", 200,
            {"settings": {"MAX_NODE_COUNT": plat_default}},
            note="平台层提交代码默认值=%s 删覆盖行" % plat_default)
    plat3 = platform_settings(b)
    b.raw_call("R6", "OWNER", "GET", "/api/admin/platform-settings",
               plat3["MAX_NODE_COUNT"].get("overridden") is False
               and plat3["MAX_NODE_COUNT"].get("value") == plat_default,
               "平台覆盖已回到代码默认", "row=%s" % plat3.get("MAX_NODE_COUNT"))

    # ---------- R6 平台开关 ----------
    reg_default = platform_settings(b)["REGISTER_ENABLED"].get("defaultValue")
    b.check("R6", "OWNER", "PUT", "/api/admin/platform-settings", 200,
            {"settings": {"REGISTER_ENABLED": "false"}}, note="平台关闭注册")
    res = b.session("regclosed1").call("POST", "/api/auth/register",
                                       {"username": "regclosed1", "password": TEST_PWD})[1]
    b.raw_call("R6", "ANON", "POST", "/api/auth/register",
               res.get("code") == 400 and "关闭" in str(res.get("message")),
               "注册关闭时被拦截", "got=%s %s" % (res.get("code"), str(res.get("message"))[:60]))
    b.check("R6", "OWNER", "PUT", "/api/admin/platform-settings", 200,
            {"settings": {"REGISTER_ENABLED": reg_default}}, note="注册开关提交默认值=删覆盖行")
    res = b.session("regopened1").register("regopened1")
    b.raw_call("R6", "ANON", "POST", "/api/auth/register", res.get("code") == 200,
               "开关恢复后可注册", "got=%s %s" % (res.get("code"), str(res.get("message"))[:60]))
    plat4 = platform_settings(b)
    b.raw_call("R6", "OWNER", "GET", "/api/admin/platform-settings",
               plat4["REGISTER_ENABLED"].get("overridden") is False,
               "恢复默认后不再有覆盖行", "row=%s" % plat4.get("REGISTER_ENABLED"))
    b.check("R6", "regclosed1", "POST", "/api/auth/login", 400,
            {"username": "regclosed1", "password": TEST_PWD}, note="被拦截的注册未落库，登录失败")

    join_default = platform_settings(b)["INVITE_JOIN_ENABLED"].get("defaultValue")
    b.check("R6", "OWNER", "PUT", "/api/admin/platform-settings", 200,
            {"settings": {"INVITE_JOIN_ENABLED": "false"}}, note="平台关闭邀请码加入")
    blocked = b.session("reginv2")
    blocked.register("reginv2")
    res = blocked.call("POST", "/api/family/join", {"inviteCode": b.get("familyAInviteCode")}, csrf=True)[1]
    b.raw_call("R7", "reginv2", "POST", "/api/family/join",
               res.get("code") == 400 and "邀请" in str(res.get("message")),
               "邀请加入关闭时被拦截", "got=%s %s" % (res.get("code"), str(res.get("message"))[:60]))
    b.check("R6", "OWNER", "PUT", "/api/admin/platform-settings", 200,
            {"settings": {"INVITE_JOIN_ENABLED": join_default}}, note="恢复邀请码加入")
    res = blocked.call("POST", "/api/family/join", {"inviteCode": b.get("familyAInviteCode")}, csrf=True)[1]
    b.raw_call("R7", "reginv2", "POST", "/api/family/join", res.get("code") == 200,
               "恢复后可加入", "got=%s %s" % (res.get("code"), str(res.get("message"))[:60]))

    # 家族总数上限：先卡在当前总数（新建必拒），再放宽（新建放行），最后提交默认值删覆盖行
    total = (owner.call("GET", "/api/admin/families?page=1&size=1")[1].get("data") or {}).get("total") or 0
    count_default = platform_settings(b)["MAX_FAMILY_COUNT"].get("defaultValue")
    b.check("R6", "OWNER", "PUT", "/api/admin/platform-settings", 200,
            {"settings": {"MAX_FAMILY_COUNT": str(total)}}, note="家族总数上限=%d（已达）" % total)
    capped2 = b.session("regcap2")
    capped2.register("regcap2")
    res = capped2.call("POST", "/api/family", {"name": "超限家族"}, csrf=True)[1]
    b.raw_call("R6", "regcap2", "POST", "/api/family",
               res.get("code") == 400 and "上限" in str(res.get("message")),
               "超总数上限创建被拒", "got=%s %s" % (res.get("code"), str(res.get("message"))[:60]))
    b.check("R6", "OWNER", "PUT", "/api/admin/platform-settings", 200,
            {"settings": {"MAX_FAMILY_COUNT": str(total + 5)}}, note="总数上限放宽到=%d" % (total + 5))
    res = capped2.call("POST", "/api/family", {"name": "回归家族F"}, csrf=True)[1]
    b.raw_call("R6", "regcap2", "POST", "/api/family", res.get("code") == 200,
               "放宽上限后可建族", "got=%s %s" % (res.get("code"), str(res.get("message"))[:60]))
    b.check("R6", "OWNER", "PUT", "/api/admin/platform-settings", 200,
            {"settings": {"MAX_FAMILY_COUNT": count_default}}, note="提交默认值=%s 删覆盖行" % count_default)
    plat5 = platform_settings(b)
    b.raw_call("R6", "OWNER", "GET", "/api/admin/platform-settings",
               plat5["MAX_FAMILY_COUNT"].get("overridden") is False,
               "总数上限回到代码默认", "row=%s" % plat5.get("MAX_FAMILY_COUNT"))

    # ---------- R7 平台侧家族治理（对电池家族A 操作，避免污染真实家族） ----------
    b.check("R7", "MEMBER", "GET", "/api/admin/families?page=1&size=10", 400, note="非白名单读家族列表被拒")
    b.check("R7", "OWNER", "GET", "/api/admin/overview", 200, note="平台总览",
            assert_fn=lambda d, r: (d or {}).get("familyCount") is not None)
    b.check("R7", "OWNER", "GET", "/api/admin/families?page=1&size=10&keyword=%s" % new_code, 200,
            note="按邀请码检索家族",
            assert_fn=lambda d, r: any(x.get("id") == fid_a for x in d.get("records", [])))
    b.check("R7", "OWNER", "GET", "/api/admin/family/%s" % fid_a, 200, note="家族详情",
            assert_fn=lambda d, r: (d.get("family") or {}).get("inviteCode") == new_code)
    b.check("R7", "OWNER", "GET", "/api/admin/users?page=1&size=10&keyword=regv50", 200,
            note="按用户名检索用户")
    b.check("R7", "MEMBER", "PUT", "/api/admin/family/%s/status" % fid_a, 400,
            {"status": "FROZEN"}, note="非白名单不能冻结")
    b.check("R7", "OWNER", "PUT", "/api/admin/family/%s" % fid_a, 200,
            {"name": "回归改名家族"}, note="平台侧重命名家族")
    b.check("R7", "OWNER", "PUT", "/api/admin/family/%s" % fid_a, 400,
            {"name": ""}, note="空名重命名被拒")
    renamed = (user_a.call("GET", "/api/family")[1].get("data") or {})
    b.raw_call("R7", "regv50a", "GET", "/api/family", renamed.get("name") == "回归改名家族",
               "改名对成员立即可见", "name=%s" % renamed.get("name"))
    b.check("R7", "OWNER", "PUT", "/api/admin/family/%s/status" % fid_a, 200,
            {"status": "FROZEN"}, note="冻结家族")
    b.check("R7", "regv50a", "POST", "/api/node", 403, {"name": "冻结期间建节点", "gender": 1},
            note="冻结后成员写操作被拒")
    b.check("R7", "regv50a", "GET", "/api/tree/full", 200, note="冻结期间成员仍可读")
    b.check("R7", "OWNER", "PUT", "/api/admin/family/%s/status" % fid_a, 200,
            {"status": "ARCHIVED"}, note="归档家族")
    b.check("R7", "regv50a", "GET", "/api/tree/full", 403, note="归档后成员整体下线")
    b.check("R7", "OWNER", "GET", "/api/admin/family/%s" % fid_a, 200, note="平台管理员仍可查看详情")
    b.check("R7", "OWNER", "PUT", "/api/admin/family/%s/status" % fid_a, 200,
            {"status": "NORMAL"}, note="恢复正常")
    b.check("R7", "regv50a", "GET", "/api/tree/full", 200, note="恢复后成员读写正常",
            assert_fn=lambda d, r: len(d or []) >= 1)

    # 转让族长：失联恢复用，转完立即转回
    b.check("R7", "OWNER", "PUT", "/api/admin/family/%s/owner" % fid_a, 200,
            {"userId": invitee.user_id}, note="平台侧转让族长给成员")
    b.check("R7", "OWNER", "PUT", "/api/admin/family/%s/owner" % fid_a, 200,
            {"userId": user_a.user_id}, note="转回原族长")
    b.check("R7", "regv50a", "PUT", "/api/family/invite-code", 200, note="转回后原族长权限恢复")

    # ---------- R9 账号口令生命周期 ----------
    pwd_user = b.session("regpwd1")
    pwd_user.register("regpwd1")
    uid = pwd_user.user_id
    rst = b.check("R9", "OWNER", "POST", "/api/admin/user/%s/password/reset" % uid, 200,
                  note="平台重置测试账号口令")
    newpwd = ((rst or {}).get("data") or {}).get("newPassword")
    b.raw_call("R9", "OWNER", "POST", "/api/admin/user/{uid}/password/reset",
               bool(newpwd) and (rst.get("data") or {}).get("mustChangePassword") is True,
               "重置返回一次性口令并要求首登改密", "data=%s" % str((rst or {}).get("data"))[:80])
    if newpwd:
        s2 = b.session("S2")
        lr = s2.login("regpwd1", newpwd)
        b.raw_call("R9", "S2", "POST", "/api/auth/login",
                   lr.get("code") == 200 and (lr.get("data") or {}).get("mustChangePassword") is True,
                   "随机口令登录且要求首登改密", "resp=%s" % str(lr)[:80])
        if lr.get("code") == 200:
            s2.ensure_csrf()
            b.check("R9", "S2", "POST", "/api/auth/password", 200,
                    {"oldPassword": newpwd, "newPassword": CHANGED_PWD}, note="首登改密成功")
            r5 = b.session("S3").login("regpwd1", CHANGED_PWD)
            b.raw_call("R9", "S3", "POST", "/api/auth/login",
                       r5.get("code") == 200 and not (r5.get("data") or {}).get("mustChangePassword"),
                       "改密后登录不再强制", "resp=%s" % str(r5)[:80])
        for _ in range(LOGIN_FAIL_LOCK_ATTEMPTS):
            b.session("S4").call("POST", "/api/auth/login", {"username": "regpwd1", "password": "wrongpwd"})
        locked = b.session("S5").login("regpwd1", CHANGED_PWD)
        b.raw_call("R9", "S5", "POST", "/api/auth/login",
                   locked.get("code") == 400 and "登录失败次数过多" in str(locked.get("message")),
                   "连续失败达阈值后正确口令也被锁", "resp=%s" % str(locked)[:80])
        b.check("R9", "OWNER", "POST", "/api/admin/user/%s/unlock" % uid, 200, note="平台解锁账号")
        r6 = b.session("S6").login("regpwd1", CHANGED_PWD)
        b.raw_call("R9", "S6", "POST", "/api/auth/login", r6.get("code") == 200,
                   "解锁后可登录", "resp=%s" % str(r6)[:80])

    # ---------- R8 日志检索 ----------
    b.check("R8", "OWNER", "GET", "/api/admin/logs?familyId=%s&page=1&size=%d" % (fid, LOG_PAGE_SIZE),
            200, note="按家族过滤平台日志", assert_fn=lambda d, r: (d or {}).get("total") is not None)
    b.check("R8", "OWNER", "GET", "/api/admin/logs?operationType=LOGIN&page=1&size=%d" % LOG_PAGE_SIZE,
            200, note="按操作类型检索登录日志",
            assert_fn=lambda d, r: all(x.get("operationType") == "LOGIN" for x in d.get("records", [])))
    b.check("R8", "OWNER", "GET", "/api/admin/log-types", 200, note="日志类型字典非空",
            assert_fn=lambda d, r: len(d or []) > 0)
    insp = b.check("R8", "OWNER", "GET", "/api/admin/inspection", 200, note="数据巡检报告",
                   assert_fn=lambda d, r: (d or {}).get("checkCount") and (d or {}).get("items") is not None)
    if insp:
        print("# inspection checks=%s problemChecks=%s high=%s medium=%s low=%s" % (
            (insp.get("data") or {}).get("checkCount"), (insp.get("data") or {}).get("problemCheckCount"),
            (insp.get("data") or {}).get("highCount"), (insp.get("data") or {}).get("mediumCount"),
            (insp.get("data") or {}).get("lowCount")))

    # ---------- R3 登出 ----------
    b.check("R3", "OWNER", "POST", "/api/auth/logout", 200, note="登出")
    res = owner.call("GET", "/api/auth/me")[1]
    b.raw_call("R3", "OWNER", "GET", "/api/auth/me", res.get("code") == 401,
               "登出后会话失效", "got=%s" % res.get("code"))


reg.run_main(main, "R6/R7/R10 配置链与治理电池")
