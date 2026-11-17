"""回归电池公共骨架：会话、请求封装与结果记录。

只面向本地 scratch 环境（独立库 + 独立端口），脚本会在环境上建删测试账号、家族与节点，
禁止指向生产。地址与账号用环境变量覆盖，见同目录 README。

@author Family-Tree
@date 2026-11-17
"""
import http.cookiejar
import json
import os
import time
import urllib.error
import urllib.request

BASE = os.environ.get("FT_REG_BASE", "http://127.0.0.1:8093").rstrip("/")
OWNER_USER = os.environ.get("FT_REG_OWNER_USER", "admin")
OWNER_PWD = os.environ.get("FT_REG_OWNER_PWD", "123456")
MEMBER_USER = os.environ.get("FT_REG_MEMBER_USER", "蒙子羽")
MEMBER_PWD = os.environ.get("FT_REG_MEMBER_PWD", "123456")
# 电池内新建测试账号的统一口令（仅 scratch 库）
TEST_PWD = os.environ.get("FT_REG_TEST_PWD", "Reg@2026reg")

# 限流重试：写接口连续命中 429 时等待窗口重置后重试
RATE_LIMIT_CODE = 429
RATE_LIMIT_SLEEP = 63
RATE_LIMIT_TRIES = 4

# 已知未修缺陷：用例带上 known 后，失败记为 KNOWN 不计入红字，
# 一旦意外通过则报 FIXED 并计入失败，提醒把用例改回正例断言
KNOWN_DEFECTS = {
    "D-01": "版本回滚只返回数据，不落库",
    "D-08": "未知路径返回 HTTP 200 + code:500",
}

PASS, FAIL, KNOWN, FIXED = "PASS", "FAIL", "KNOWN", "FIXED"


class Session:
    """带会话 Cookie 与 CSRF 令牌的最小 HTTP 客户端。"""

    def __init__(self, label):
        self.label = label
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self.csrf = None
        self.user = None

    def raw(self, method, path, data=None, ctype=None, csrf=False):
        headers = {"Accept": "application/json"}
        if ctype:
            headers["Content-Type"] = ctype
        if csrf and self.csrf:
            headers["X-CSRF-TOKEN"] = self.csrf
        req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
        try:
            with self.opener.open(req, timeout=90) as resp:
                return resp.status, resp.read(), dict(resp.headers)
        except urllib.error.HTTPError as exc:
            return exc.code, exc.read(), dict(exc.headers)
        except Exception as exc:  # noqa: BLE001
            return 0, "%s: %s" % (type(exc).__name__, exc), {}

    def call(self, method, path, body=None, csrf=False):
        data = json.dumps(body).encode("utf-8") if body is not None else None
        for _ in range(RATE_LIMIT_TRIES):
            st, raw, _ = self.raw(method, path, data, "application/json" if data else None, csrf)
            res = _json_envelope(raw)
            if res.get("code") != RATE_LIMIT_CODE:
                return st, res
            self.ensure_csrf()
            time.sleep(RATE_LIMIT_SLEEP)
        return st, res

    def multipart(self, path, filename, content, part_type="application/octet-stream",
                  field="file", extra=None):
        """提交 multipart 表单；part_type 即浏览器对该文件声明的 Content-Type。"""
        boundary = "----ftregboundary7mapo"
        chunks = []
        for key, value in (extra or {}).items():
            chunks.append(('--%s\r\nContent-Disposition: form-data; name="%s"\r\n\r\n%s\r\n'
                           % (boundary, key, value)).encode("utf-8"))
        chunks.append(('--%s\r\nContent-Disposition: form-data; name="%s"; filename="%s"\r\n'
                       "Content-Type: %s\r\n\r\n" % (boundary, field, filename, part_type)).encode("utf-8"))
        chunks.append(content)
        chunks.append(("\r\n--%s--\r\n" % boundary).encode("utf-8"))
        for _ in range(RATE_LIMIT_TRIES):
            st, raw, _ = self.raw("POST", path, b"".join(chunks),
                                  "multipart/form-data; boundary=%s" % boundary, csrf=True)
            res = _json_envelope(raw)
            if res.get("code") != RATE_LIMIT_CODE:
                return st, res
            self.ensure_csrf()
            time.sleep(RATE_LIMIT_SLEEP)
        return st, res

    def ensure_csrf(self):
        if self.user is None:
            return
        _, res = self.call("GET", "/api/auth/me")
        token = (res.get("data") or {}).get("csrfToken")
        if token:
            self.csrf = token

    def login(self, username, password):
        _, res = self.call("POST", "/api/auth/login", {"username": username, "password": password})
        if res.get("code") == 200:
            self.csrf = res["data"].get("csrfToken")
            self.user = res["data"]
        return res

    def register(self, username, password=TEST_PWD, **extra):
        """注册测试账号；注册响应即带会话与令牌，省一次登录。"""
        body = {"username": username, "password": password}
        body.update({k: v for k, v in extra.items() if v is not None})
        _, res = self.call("POST", "/api/auth/register", body)
        if res.get("code") == 200:
            self.csrf = (res.get("data") or {}).get("csrfToken")
            self.user = (res.get("data") or {}) or {}
            if not self.csrf:
                self.login(username, password)
        return res

    @property
    def family_id(self):
        return (self.user or {}).get("familyId")

    @property
    def user_id(self):
        return (self.user or {}).get("userId")


def _json_envelope(raw):
    if isinstance(raw, dict):
        return raw
    if isinstance(raw, str):
        # 传输层异常（连接被拒/超时）走这条路，raw 已是可读文本
        return {"code": None, "message": raw[:200], "data": None}
    try:
        return json.loads(raw.decode("utf-8", "replace"))
    except Exception:  # noqa: BLE001
        return {"code": None, "message": raw[:200].decode("utf-8", "replace"), "data": None}


def png_bytes():
    """1x1 透明 PNG，用作相册正例文件体。"""
    import struct
    import zlib

    def chunk(typ, payload):
        body = typ + payload
        return struct.pack(">I", len(payload)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(b"\x00\xff\x00\x00\x00\xff\xff\xff\xff"))
            + chunk(b"IEND", b""))


class Battery:
    """结果登记表：记录用例、已知缺陷反查与最终报表。"""

    def __init__(self):
        self.rows = []
        self.ctx = {}
        self.sessions = {}

    def add(self, ok, section, who, api, note, detail="", known=None, status=None):
        if status is None:
            if ok:
                status = FIXED if known else PASS
                if status == FIXED:
                    ok = False
                    detail = "缺陷 %s 疑似已修复：%s，请把用例改回正例断言" % (known, KNOWN_DEFECTS.get(known, ""))
            else:
                status = KNOWN if known else FAIL
        self.rows.append({"status": status, "section": section, "who": who, "api": api,
                          "note": note, "detail": detail, "known": known})
        print("%-5s [%-4s] [%-9s] %-48s %s%s" % (status, section, who, api[:48], note,
                                                 ("  :: " + detail) if (detail and status == FAIL) else ""))

    def raw_call(self, section, who, method, path, ok, note, detail="", known=None):
        self.add(ok, section, who, "%s %s" % (method, path), note, detail, known)

    def check(self, section, who, method, path, expect, body=None, note="",
              assert_fn=None, csrf=True, files=None, known=None):
        """发起一次调用并按 Result.code 判定；assert_fn(data, res) 可进一步断言。

        返回响应体（失败返回 None），便于链式取上下文。
        """
        sess = self.sessions[who]
        if csrf and method in ("POST", "PUT", "DELETE", "PATCH"):
            sess.ensure_csrf()
        if files is not None:
            st, res = sess.multipart(path, files[0], files[1],
                                     part_type=(files[3] if len(files) > 3 else "application/octet-stream"),
                                     extra=(files[2] if len(files) > 2 else None))
        else:
            st, res = sess.call(method, path, body, csrf)
        code = res.get("code")
        expected = expect if isinstance(expect, (list, tuple)) else [expect]
        ok = code in expected
        detail = ""
        if ok and assert_fn is not None:
            try:
                ok = bool(assert_fn(res.get("data"), res))
            except Exception as exc:  # noqa: BLE001
                ok, detail = False, "assert_ex:%s" % exc
        if not ok and not detail:
            detail = "expected=%s got=%s http=%s msg=%s" % (
                expected, code, st, (res.get("message") or "")[:70])
        self.add(ok, section, who, "%s %s" % (method, path), note, detail, known)
        return res if ok else None

    def session(self, label):
        if label not in self.sessions:
            self.sessions[label] = Session(label)
        return self.sessions[label]

    def val(self, key, value):
        self.ctx[key] = value
        return value

    def get(self, key, default=None):
        return self.ctx.get(key, default)

    def summary(self, title):
        counts = {PASS: 0, FAIL: 0, KNOWN: 0, FIXED: 0}
        for r in self.rows:
            counts[r["status"]] += 1
        print("\n# %s：共 %d 项，PASS %d / FAIL %d / KNOWN %d / FIXED %d"
              % (title, len(self.rows), counts[PASS], counts[FAIL], counts[KNOWN], counts[FIXED]))
        for r in self.rows:
            if r["status"] in (FAIL, FIXED):
                print("  %s [%s] %s %s :: %s | %s" % (r["status"], r["section"], r["who"],
                                                      r["api"], r["note"], r["detail"]))
        for r in self.rows:
            if r["status"] == KNOWN:
                print("  KNOWN [%s] %s %s :: %s（%s）" % (r["section"], r["who"], r["api"],
                                                         r["note"], KNOWN_DEFECTS.get(r["known"], "")))
        return counts[FAIL]


def run_main(main_fn, title):
    """统一入口：脚本自身异常也要落地为退出码非 0，避免电池静默半跑。"""
    battery = Battery()
    try:
        main_fn(battery)
    except Exception as exc:  # noqa: BLE001
        import traceback
        traceback.print_exc()
        battery.add(False, "ABORT", "-", "script", "电池脚本自身异常",
                    "%s: %s" % (type(exc).__name__, exc))
    failures = battery.summary(title)
    raise SystemExit(1 if failures else 0)
