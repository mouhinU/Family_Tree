"""R4 写主链路 + R8 快照 + R9 既有功能写路径电池。

节点全生命周期、关系、版本、辈分、留言/家族圈/通知、私聊、活动/纪念日/悼念供奉、相册、导出与 GEDCOM。
只允许打本地 scratch 环境。

@author Family-Tree
@date 2026-11-17
"""
import json

import ft_reg_common as reg

from ft_reg_common import MEMBER_PWD, MEMBER_USER, OWNER_PWD, OWNER_USER, png_bytes

GED_SAMPLE = ("0 HEAD\n1 SOUR FTREG\n1 GEDC\n2 VERS 5.5.1\n2 LANG Chinese\n"
              "0 @I1@ INDI\n1 NAME 回归 /GED测试/\n1 SEX M\n1 BIRT\n2 DATE 5 MAR 1990\n0 TRLR\n")


def records_of(data):
    return data if isinstance(data, list) else (data or {}).get("records", [])


def main(b):
    owner = b.session("OWNER")
    member = b.session("MEMBER")
    owner.login(OWNER_USER, OWNER_PWD)
    member.login(MEMBER_USER, MEMBER_PWD)
    fid = owner.family_id
    b.val("familyId", fid)
    b.val("memberUserId", member.user_id)

    # ---------- R4 节点全生命周期 ----------
    tree = b.check("R4", "OWNER", "GET", "/api/tree/full", 200, note="整树")
    flat = []

    def walk(n):
        flat.append(n)
        for c in n.get("children") or []:
            walk(c)

    for n in (tree or {}).get("data") or []:
        walk(n)
    if not flat:
        b.raw_call("R4", "OWNER", "GET", "/api/tree/full", False,
                   "scratch 库主家族必须有节点，否则写主链路无从下手", "nodes=0")
        return
    root = next((n for n in flat if n.get("parentId") in (None, 0)), flat[0])
    listing = b.check("R4", "OWNER", "GET", "/api/node/list?page=1&size=500", 200, note="节点全量列表")
    all_nodes = records_of((listing or {}).get("data"))
    dead_rows = [n for n in all_nodes if n.get("deathDate")]
    b.val("rootId", root["id"])
    kid = next((x for x in flat if x.get("parentId") == root["id"]), None)
    b.val("kidId", kid.get("id") if kid else None)
    # 我的位置要还原成跑电池之前的绑定，不能强行写死成根节点
    me = (b.check("R4", "OWNER", "GET", "/api/auth/me", 200, note="读取当前绑定") or {}).get("data") or {}
    my_origin_node = b.val("myOriginNode", me.get("nodeId"))
    print("# root=%s(%s) nodes=%d deceased=%d myNode=%s"
          % (root["id"], root.get("name"), len(all_nodes), len(dead_rows), my_origin_node))

    node = b.check("R4", "OWNER", "POST", "/api/node", 200,
                   {"name": "回归测试甲", "gender": 1, "birthDate": "1990-03-05",
                    "parentNodeId": b.get("rootId"), "birthOrder": 1},
                   note="新增在世节点(挂根下, 阳历1990-03-05)")
    nid = ((node or {}).get("data") or {}).get("nodeId") or ((node or {}).get("data") or {}).get("id")
    b.val("newNodeId", nid)
    b.check("R4", "OWNER", "GET", "/api/node/%s/detail" % nid, 200, note="新节点详情按阳历派生农历",
            assert_fn=lambda d, r: bool(d.get("lunarBirthDate")))
    b.check("R4", "OWNER", "PUT", "/api/node", 200,
            {"id": nid, "name": "回归测试乙", "gender": 1, "birthDate": "1990-03-05", "generation": 1},
            note="节点改名")
    b.check("R4", "OWNER", "GET", "/api/node/%s" % nid, 200, note="改名后可读",
            assert_fn=lambda d, r: d.get("name") == "回归测试乙")
    b.check("R4", "OWNER", "PUT", "/api/node/color", 200, {"nodeIds": [nid], "colorLabel": "paternal"},
            note="节点标色 父系")
    b.check("R4", "OWNER", "POST", "/api/node", 200,
            {"name": "回归测试配偶", "gender": 2, "birthDate": "1991-07-07", "spouseNodeId": nid},
            note="新增配偶节点并建立夫妻关系")
    b.check("R4", "OWNER", "GET", "/api/node/%s/detail" % nid, 200, note="详情出现配偶行",
            assert_fn=lambda d, r: len(d.get("spouses") or []) >= 1)
    rels = b.check("R4", "OWNER", "GET", "/api/relation/node/%s" % nid, 200, note="节点关系列表")
    spouse_rel = next((x for x in (rels or {}).get("data") or [] if x.get("relationType") == 2), None)
    b.val("spouseRelId", spouse_rel.get("id") if spouse_rel else None)
    b.check("R4", "MEMBER", "POST", "/api/node", [400, 403],
            {"name": "非管理员建节点", "gender": 1}, note="非管理员建节点被拒")

    # ---------- R4 版本：历史/对比可读，回滚落地为已知缺陷 D-01 ----------
    hist = b.check("R4", "OWNER", "GET", "/api/version/node/%s/history?page=1&size=10" % nid, 200,
                   note="节点版本历史>=2",
                   assert_fn=lambda d, r: (d.get("total") or 0) >= 2 if isinstance(d, dict) else len(d or []) >= 2)
    versions = sorted({x.get("versionNumber") for x in ((hist or {}).get("data") or {}).get("list") or []
                       if x.get("versionNumber")})
    v_first = versions[0] if versions else 1
    v_last = versions[-1] if len(versions) > 1 else v_first + 1
    b.check("R4", "OWNER", "GET", "/api/version/node/%s/compare?v1=%s&v2=%s" % (nid, v_first, v_last),
            200, note="版本对比 v%s/v%s" % (v_first, v_last))
    b.check("R4", "OWNER", "POST", "/api/version/node/%s/rollback?versionId=%s" % (nid, v_first), 200,
            note="回滚到 v%s 接口返回成功" % v_first)
    b.check("R4", "OWNER", "GET", "/api/node/%s" % nid, 200, note="回滚生效：姓名回到 回归测试甲",
            assert_fn=lambda d, r: d.get("name") == "回归测试甲", known="D-01")
    b.check("R4", "OWNER", "PUT", "/api/node", 200,
            {"id": nid, "name": "回归测试乙", "gender": 1, "birthDate": "1990-03-05", "generation": 1},
            note="回滚探针后还原改名")

    if b.get("spouseRelId"):
        b.check("R4", "OWNER", "DELETE", "/api/relation/%s" % b.get("spouseRelId"), 200, note="删除夫妻关系")
    spouses = b.check("R4", "OWNER", "GET",
                      "/api/node/search?keyword=%E5%9B%9E%E5%BD%92%E6%B5%8B%E8%AF%95%E9%85%8D%E5%81%B6",
                      200, note="搜索配偶节点")
    for x in (spouses or {}).get("data") or []:
        owner.call("DELETE", "/api/node/%s" % x["id"], csrf=True)
    b.check("R4", "OWNER", "DELETE", "/api/node/%s" % nid, 200, note="删除测试节点（连带回收自动账号）")
    b.check("R4", "OWNER", "GET", "/api/node/search?keyword=%E5%9B%9E%E5%BD%92%E6%B5%8B%E8%AF%95%E7%94%B2",
            200, note="删除后搜索无残留", assert_fn=lambda d, r: len(d or []) == 0)
    # 自动账号口令是随机生成的，用登录尝试证明不了什么；直接查平台账号列表确认已回收
    users = b.check("R4", "OWNER", "GET",
                   "/api/admin/users?page=1&size=10&keyword=%E5%9B%9E%E5%BD%92%E6%B5%8B%E8%AF%95%E7%94%B2",
                   200, note="按姓名检索账号")
    b.raw_call("R4", "OWNER", "GET", "/api/admin/users?keyword=",
               users is not None and len(records_of((users or {}).get("data"))) == 0,
               "节点删除后同名自动账号已回收", "rows=%s" % str((users or {}).get("data"))[:80])

    # ---------- R4 关系路径 / 我的位置 ----------
    b.check("R4", "OWNER", "GET",
            "/api/relation-path?fromNodeId=%s&toNodeId=%s" % (b.get("rootId"), b.get("rootId")),
            200, note="关系路径(同一人 found=false)", assert_fn=lambda d, r: (d or {}).get("found") is False)
    if b.get("kidId"):
        b.check("R4", "OWNER", "GET",
                "/api/relation-path?fromNodeId=%s&toNodeId=%s" % (b.get("rootId"), b.get("kidId")),
                200, note="关系路径(祖->孙)",
                assert_fn=lambda d, r: (d or {}).get("found") is True and (d or {}).get("pathLength", 0) >= 1)
    b.check("R4", "OWNER", "PUT", "/api/auth/my-node", 200, {"nodeId": b.get("rootId")},
            note="标记我的位置=根节点")
    b.check("R4", "OWNER", "GET", "/api/auth/me", 200, note="me 回显新绑定",
            assert_fn=lambda d, r: d.get("nodeId") == b.get("rootId"))
    restore_to = b.get("myOriginNode")
    if restore_to:
        b.check("R4", "OWNER", "PUT", "/api/auth/my-node", 200, {"nodeId": restore_to},
                note="还原我的位置=电池前的绑定")

    # ---------- R8 快照 ----------
    snap = b.check("R8", "OWNER", "POST",
                   "/api/version/snapshot?snapshotName=%E5%9B%9E%E5%BD%92%E5%BF%AB%E7%85%A7&description=v50",
                   200, note="创建快照")
    sid = ((snap or {}).get("data") or {}).get("id") or ((snap or {}).get("data") or {}).get("snapshotId")
    b.check("R8", "OWNER", "GET", "/api/version/snapshot/list", 200, note="快照列表含新快照",
            assert_fn=lambda d, r: any((x.get("id") == sid or x.get("snapshotId") == sid)
                                       for x in (d or [])) if sid else False)
    if sid:
        b.check("R8", "OWNER", "GET", "/api/version/snapshot/%s" % sid, 200, note="快照详情")
        b.check("R8", "OWNER", "POST", "/api/version/snapshot/%s/preview" % sid, 200, note="快照恢复预览")
        b.check("R8", "OWNER", "DELETE", "/api/version/snapshot/%s" % sid, 200, note="删除快照")

    # ---------- R9 辈分：覆盖式逐行 upsert ----------
    gens = b.check("R9", "OWNER", "GET", "/api/generation", 200, note="辈分列表")
    original = (gens or {}).get("data") or []
    lay0 = (b.check("R9", "OWNER", "GET", "/api/generation/layout", 200, note="排布原值") or {}).get("data") or {}
    b.check("R9", "OWNER", "PUT", "/api/generation/layout", 200, {"cols": 5, "rows": 10},
            note="排布保存 5x10")
    b.check("R9", "OWNER", "GET", "/api/generation/layout", 200, note="排布读回一致",
            assert_fn=lambda d, r: d.get("cols") == 5 and d.get("rows") == 10)
    if lay0.get("cols") and lay0.get("rows"):
        b.check("R9", "OWNER", "PUT", "/api/generation/layout", 200,
                {"cols": lay0["cols"], "rows": lay0["rows"]}, note="排布还原 %sx%s"
                % (lay0["cols"], lay0["rows"]))
    b.check("R9", "MEMBER", "PUT", "/api/generation", [400, 403],
            [{"generation": 99, "name": "回归辈X"}], note="非管理员保存辈分被拒（只读收口）")
    b.check("R9", "OWNER", "PUT", "/api/generation", [400],
            [{"generation": 0, "name": "非法世代"}], note="世代越界被拒")
    b.check("R9", "OWNER", "PUT", "/api/generation", 200, [{"generation": 99, "name": "回归辈X"}] + original,
            note="管理员追加辈分行")
    b.check("R9", "OWNER", "GET", "/api/generation", 200, note="辈分含新行",
            assert_fn=lambda d, r: any(x.get("name") == "回归辈X" for x in (d or [])))
    b.check("R9", "OWNER", "PUT", "/api/generation", 200, original, note="辈分还原（省略行不删）")
    b.check("R9", "OWNER", "GET", "/api/generation", 200, note="省略提交的行仍在（逐行 upsert 语义）",
            assert_fn=lambda d, r: any(x.get("name") == "回归辈X" for x in (d or [])))
    b.check("R9", "OWNER", "PUT", "/api/generation", 200, [{"generation": 99, "name": ""}],
            note="空名提交=删除该行（前端全量矩阵靠它清行）")
    b.check("R9", "OWNER", "GET", "/api/generation", 200, note="空名行已删除",
            assert_fn=lambda d, r: not any(x.get("generation") == 99 for x in (d or [])))

    # ---------- R9 留言 / 家族圈 / 通知 ----------
    b.check("R9", "OWNER", "POST", "/api/message", 200,
            {"content": "回归测试留言", "category": "GENERAL"}, note="发布留言（POST 不返回 id，需列表回查）")
    mlst = owner.call("GET", "/api/message?page=1&size=100")[1]
    mid = next((x.get("id") for x in records_of((mlst or {}).get("data"))
                if x.get("content") == "回归测试留言"), None)
    b.check("R9", "MEMBER", "POST", "/api/message", 200,
            {"content": "回归测试回复", "parentId": mid, "category": "GENERAL"}, note="成员回复留言")
    b.check("R9", "MEMBER", "GET", "/api/message/%s/replies" % mid, 200, note="回复列表",
            assert_fn=lambda d, r: len(records_of(d)) >= 1)
    b.check("R9", "MEMBER", "POST", "/api/message/%s/like" % mid, 200, note="点赞")
    b.check("R9", "MEMBER", "DELETE", "/api/message/%s/like" % mid, 200, note="取消点赞")
    b.check("R9", "MEMBER", "DELETE", "/api/message/%s" % mid, [400, 403], note="他人留言不可删除")
    b.check("R9", "OWNER", "DELETE", "/api/message/%s" % mid, 200, note="作者删除留言")

    topic = b.check("R9", "OWNER", "POST", "/api/forum", 200, {"title": "回归测试帖", "content": "内容"},
                    note="发帖")
    tid = ((topic or {}).get("data") or {}).get("id")
    b.check("R9", "MEMBER", "POST", "/api/forum/%s/reply" % tid, 200, {"content": "回归测试回复"}, note="回帖")
    b.check("R9", "OWNER", "GET", "/api/forum/%s" % tid, 200, note="帖子详情含回复",
            assert_fn=lambda d, r: len(d.get("replies") or []) >= 1)
    b.check("R9", "OWNER", "DELETE", "/api/forum/%s" % tid, 200, note="删帖")

    b.check("R9", "OWNER", "POST", "/api/notification/read-all", 200, note="全部标记已读")
    b.check("R9", "OWNER", "GET", "/api/notification/unread-count", 200, note="已读后未读数=0",
            assert_fn=lambda d, r: (d.get("count") if isinstance(d, dict) else d) in (0, None))

    # ---------- R9 私聊：同族以 family_member 为准（D-03 修复口径） ----------
    b.check("R9", "MEMBER", "POST", "/api/private-message", 200,
            {"receiverId": owner.user_id, "content": "回归私聊"}, note="成员->族长私聊")
    b.check("R9", "OWNER", "GET", "/api/private-message/conversations", 200, note="会话列表含新会话",
            assert_fn=lambda d, r: any(c.get("peerUserId") == b.get("memberUserId") for c in (d or [])))
    b.check("R9", "OWNER", "GET", "/api/private-message/conversation/%s" % b.get("memberUserId"),
            200, note="单会话消息可读")
    b.check("R9", "OWNER", "GET", "/api/private-message/unread-count", 200, note="私聊未读数")
    outsider = b.session("outsider")
    outsider.register("regpmout1")
    outsider.call("POST", "/api/family", {"name": "回归私聊外族"}, csrf=True)
    b.check("R9", "outsider", "POST", "/api/private-message", 400,
            {"receiverId": owner.user_id, "content": "跨家族私聊"}, note="跨家族私聊被拒")

    # ---------- R9 活动 / 纪念日 ----------
    ev = b.check("R9", "OWNER", "POST", "/api/event", 200,
                 {"title": "回归测试活动", "description": "d", "eventTime": "2026-12-31 10:00:00",
                  "location": "线上", "totalCost": 100}, note="创建活动")
    evid = ((ev or {}).get("data") or {}).get("id")
    b.check("R9", "MEMBER", "POST", "/api/event/%s/signup" % evid, 200, {}, note="成员报名")
    b.check("R9", "OWNER", "POST", "/api/event/%s/close" % evid, 200, note="关闭报名")
    b.check("R9", "OWNER", "POST", "/api/event/%s/open" % evid, 200, note="重新开放")
    b.check("R9", "OWNER", "DELETE", "/api/event/%s" % evid, 200, note="删除活动")

    ann = b.check("R9", "OWNER", "POST", "/api/anniversary", 200,
                  {"nodeId": b.get("rootId"), "title": "回归纪念日", "category": "other",
                   "anniversaryDate": "2026-12-25", "remark": "r"}, note="新增纪念日")
    aid = ((ann or {}).get("data") or {}).get("id")
    b.check("R9", "OWNER", "PUT", "/api/anniversary/%s" % aid, 200,
            {"nodeId": b.get("rootId"), "title": "回归纪念日改", "category": "other",
             "anniversaryDate": "2026-12-25", "remark": "r"}, note="修改纪念日")
    b.check("R9", "OWNER", "DELETE", "/api/anniversary/%s" % aid, 200, note="删除纪念日")

    # ---------- R9 悼念 / 上香：祭品只能敬给已故长辈 ----------
    deceased = [n for n in all_nodes if n.get("deathDate") and n.get("generation")]
    if deceased:
        deepest = max(all_nodes, key=lambda n: n.get("generation") or 0)
        b.check("R9", "OWNER", "PUT", "/api/auth/my-node", 200, {"nodeId": deepest["id"]},
                note="以最深世代为我的位置（用于长辈校验）")
        elders = [n for n in deceased if (n.get("generation") or 0) < (deepest.get("generation") or 0)]
        target = elders[0] if elders else deceased[0]
        b.check("R9", "OWNER", "POST", "/api/memorial/node/%s" % target["id"], 200,
                {"content": "回归测试悼念"}, note="悼念留言")
        mm = b.check("R9", "OWNER", "GET", "/api/memorial/node/%s" % target["id"], 200, note="悼念列表",
                     assert_fn=lambda d, r: any("回归测试悼念" in str(x.get("content")) for x in (d or [])))
        for x in ((mm or {}).get("data") or []):
            if "回归测试悼念" in str(x.get("content")):
                owner.ensure_csrf()
                owner.call("DELETE", "/api/memorial/%s" % x["id"], csrf=True)
        b.check("R9", "OWNER", "POST", "/api/offering", 200,
                {"nodeId": target["id"], "offeringType": 1},
                note="以 %s(gen=%s) 为我的位置，向已故长辈 %s(gen=%s) 上香"
                     % (deepest.get("name"), deepest.get("generation"),
                        target.get("name"), target.get("generation")))
        b.check("R9", "OWNER", "GET", "/api/offering/node/%s" % target["id"], 200, note="供奉统计可读")
        b.check("R9", "OWNER", "POST", "/api/offering", 400,
                {"nodeId": deepest["id"], "offeringType": 1}, note="给在世/平辈上香被拒")
        b.check("R9", "OWNER", "PUT", "/api/auth/my-node", 200,
                {"nodeId": b.get("myOriginNode") or b.get("rootId")}, note="还原我的位置")
    else:
        b.raw_call("R9", "OWNER", "POST", "/api/offering", False, "无已故节点，供奉用例无法执行",
                   "scratch 库缺少 deathDate 节点")

    b.check("R9", "OWNER", "PUT", "/api/biography/%s" % b.get("rootId"), 200,
            {"biography": "回归测试小传"}, note="写小传")
    b.check("R9", "OWNER", "GET", "/api/biography/%s" % b.get("rootId"), 200, note="小传读回",
            assert_fn=lambda d, r: "回归测试小传" in json.dumps(d, ensure_ascii=False))
    b.check("R9", "OWNER", "PUT", "/api/biography/%s" % b.get("rootId"), 200,
            {"biography": ""}, note="小传清空还原")

    # ---------- R9 相册：按家族目录落盘 + 越权拒绝（D-06 修复口径） ----------
    other = b.session("otherfam")
    other.register("regphoto1")
    otherfam = other.call("POST", "/api/family", {"name": "回归相册家族"}, csrf=True)[1]
    b.raw_call("R9", "regphoto1", "POST", "/api/family", otherfam.get("code") == 200,
               "另建一个家族用于跨族读图", "got=%s %s" % (otherfam.get("code"),
                                                          str(otherfam.get("message"))[:50]))

    up = b.check("R9", "OWNER", "POST", "/api/photo/upload", 200, note="上传照片(声明 image/png + 真实 PNG)",
                 files=("reg.png", png_bytes(),
                        {"title": "回归测试照片", "description": "d"}, "image/png"))
    photo = (up or {}).get("data") or {}
    pid = photo.get("id")
    photo_url = photo.get("photoUrl") or ""
    b.val("photoId", pid)
    b.raw_call("R9", "OWNER", "POST", "/api/photo/upload",
               photo_url.startswith("/api/photo/%s/" % fid),
               "photo_url 带本家族目录前缀", "url=%s" % photo_url)
    b.check("R9", "OWNER", "GET", "/api/photo", 200, note="相册列表含新照片",
            assert_fn=lambda d, r: any((x.get("id") == pid) for x in (d or [])) if pid else False)
    if photo_url:
        read_back = b.sessions["OWNER"].raw("GET", photo_url)
        b.raw_call("R9", "OWNER", "GET", photo_url, read_back[0] == 200 and read_back[1][:4] == b"\x89PNG",
                   "本家族回读照片字节一致", "http=%s head=%r" % (read_back[0], read_back[1][:8]))
        cross = b.sessions["otherfam"].raw("GET", photo_url)
        b.raw_call("R9", "regphoto1", "GET", photo_url, cross[0] == 403,
                   "跨家族读图被拒 403", "http=%s" % cross[0])
    b.check("R9", "OWNER", "POST", "/api/photo/upload", 400,
            note="声明 PNG 实为文本被拒（魔数校验）",
            files=("fake.png", b"not an image at all", {"title": "伪装"}, "image/png"))
    b.check("R9", "OWNER", "POST", "/api/photo/upload", 400,
            note="Content-Type 非图片被拒",
            files=("reg.txt", b"hello", {"title": "文本"}, "text/plain"))
    if pid:
        tg = b.check("R9", "OWNER", "POST", "/api/photo/%s/tags" % pid, 200,
                     {"nodeId": b.get("rootId")}, note="照片打节点标签")
        tag_id = ((tg or {}).get("data") or {}).get("id")
        if tag_id:
            b.check("R9", "OWNER", "DELETE", "/api/photo/%s/tags/%s" % (pid, tag_id), 200, note="删除标签")
        b.check("R9", "OWNER", "DELETE", "/api/photo/%s" % pid, 200, note="删除照片")
        gone = b.sessions["OWNER"].raw("GET", photo_url) if photo_url else (0, b"", {})
        b.raw_call("R9", "OWNER", "GET", photo_url, gone[0] == 404,
                   "删除后文件不再可读", "http=%s" % gone[0])

    # ---------- R9 导出 / GEDCOM ----------
    st, raw, _ = owner.raw("GET", "/api/data/export")
    b.raw_call("R9", "OWNER", "GET", "/api/data/export", st == 200 and bool(raw),
               "JSON 导出", "http=%s len=%d" % (st, len(raw)))
    st, raw, _ = owner.raw("GET", "/api/data/export/gedcom")
    b.raw_call("R9", "OWNER", "GET", "/api/data/export/gedcom",
               st == 200 and raw.decode("utf-8", "replace").startswith("0 HEAD"),
               "GEDCOM 导出以 0 HEAD 开头", "http=%s head=%r" % (st, raw[:12]))
    b.check("R9", "MEMBER", "POST", "/api/data/import/gedcom/append", [400, 403],
            note="非管理员导入被拒", files=("reg.ged", GED_SAMPLE.encode(), None, "application/octet-stream"))
    imp = b.check("R9", "OWNER", "POST", "/api/data/import/gedcom/append", 200, note="GEDCOM 追加导入",
                  files=("reg.ged", GED_SAMPLE.encode(), None, "application/octet-stream"))
    if imp:
        d = imp.get("data") or {}
        print("# gedcom append imported=%s skipped=%s" % (d.get("importedNodeCount"), d.get("skippedCount")))
        found = b.check("R9", "OWNER", "GET", "/api/node/search?keyword=%E5%9B%9E%E5%BD%92",
                        200, note="导入节点可搜索")
        for x in (found or {}).get("data") or []:
            if "GED测试" in str(x.get("name")):
                owner.call("DELETE", "/api/node/%s" % x["id"], csrf=True)
    b.check("R9", "MEMBER", "GET", "/api/data/export/gedcom", [400, 403, 200], note="成员导出（受角色约束）")

    # ---------- R9 AI / 时间线回填 ----------
    b.check("R9", "OWNER", "POST", "/api/ai/query", [200, 400, 503], {"question": "家族有多少人"},
            note="AI 查询（无 key 时应给出可控错误）")
    b.check("R9", "OWNER", "POST", "/api/timeline/backfill", 200, note="时间线回填")


reg.run_main(main, "R4/R8/R9 写路径电池")
