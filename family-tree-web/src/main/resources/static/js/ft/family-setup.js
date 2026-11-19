/**
 * 族谱前端 - 家族设置模块
 * 新用户创建家族或加入已有家族；创建家族时可把本人信息初始化为第一个族谱节点。
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
(function () {
    'use strict';

    /** 出生日期输入框可接受的格式（yyyy-MM-dd） */
    var DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

    var messageEl = document.getElementById('setup-message');

    /** 从 sessionStorage 读取 CSRF Token 并组装 JSON 请求头 */
    function jsonHeaders() {
        var headers = {'Content-Type': 'application/json'};
        var csrfToken = sessionStorage.getItem('csrfToken');
        if (csrfToken) {
            headers['X-CSRF-TOKEN'] = csrfToken;
        }
        return headers;
    }

    /** 预填资料请求，提交前需等待其完成（Token 由该请求刷新） */
    var profileReady = null;

    /** 提示展示后跳转的延时，留出时间让用户看清成功/过期提示 */
    var REDIRECT_DELAY = 800;

    function showMessage(text, type) {
        messageEl.textContent = text;
        messageEl.className = type;
    }

    /**
     * 带会话自愈的写请求：CSRF 被拒（403）时先经 /api/auth/me 刷新令牌再重试一次。
     *
     * <p>会话为容器内存存储，发版重启或闲置超时后会话即失效，旧令牌会被 CSRF 过滤器以 403
     * 拒绝；这里区分「令牌过期」与「会话过期」，后者提示并返回登录页。</p>
     *
     * @param url 请求地址
     * @param options fetch 配置（headers 会在重试时替换为刷新后的令牌）
     * @return 响应体 Result 对象
     */
    async function writeJson(url, options) {
        var res = await fetch(url, options);
        var data = await res.json();
        if (res.status === 403 || data.code === 403) {
            var state = await prefillProfile();
            if (state === 'expired') {
                return {code: 401, message: '登录已过期，正在返回登录页...'};
            }
            if (state === 'ok') {
                options.headers = jsonHeaders();
                res = await fetch(url, options);
                data = await res.json();
            }
        }
        return data;
    }

    /**
     * 预填初始节点信息（昵称、出生日期），供创建者按需修改。
     *
     * <p>/api/auth/me 每次调用都会重新生成 CSRF Token，因此必须把返回值写回 sessionStorage，
     * 否则后续 POST 会因安全校验失败被拒。会话过期时提示并返回登录页；其它预填失败不阻塞创建家族。</p>
     *
     * @return 'ok' 读取成功；'expired' 会话过期（已安排跳转登录页）；'error' 其它失败
     */
    async function prefillProfile() {
        try {
            var res = await fetch('/api/auth/me');
            var body = await res.json();
            if (res.status === 401 || body.code === 401) {
                showMessage('登录已过期，正在返回登录页...', 'error');
                setTimeout(function () {
                    window.location.href = '/login.html';
                }, REDIRECT_DELAY);
                return 'expired';
            }
            if (!res.ok || body.code !== 200 || !body.data) {
                return 'error';
            }
            if (body.data.csrfToken) {
                sessionStorage.setItem('csrfToken', body.data.csrfToken);
            }
            var nameEl = document.getElementById('init-name');
            if (nameEl && !nameEl.value) {
                nameEl.value = body.data.nickname || '';
            }
            var birthEl = document.getElementById('init-birth');
            var birthDate = body.data.birthDate;
            if (birthEl && !birthEl.value && birthDate && DATE_PATTERN.test(birthDate)) {
                birthEl.value = birthDate;
                // 触发组件的历法换算与预览刷新（录入组件 id 仍为 init-birth）
                birthEl.dispatchEvent(new Event('change'));
            }
            return 'ok';
        } catch (err) {
            // 网络异常时保持表单为空
            return 'error';
        }
    }

    document.getElementById('btn-create').addEventListener('click', async function () {
        var name = document.getElementById('create-name').value.trim();
        if (!name) {
            showMessage('请输入家族名称', 'error');
            return;
        }
        if (await profileReady === 'expired') {
            return;
        }
        var payload = {name: name};
        var nodeName = document.getElementById('init-name').value.trim();
        if (nodeName) {
            payload.initialNodeName = nodeName;
            var gender = document.getElementById('init-gender').value;
            if (gender) {
                payload.initialNodeGender = Number(gender);
            }
            var bc = FT.birthCalendar.getSolar('init-birth');
            if (!bc.ok) {
                showMessage(bc.message, 'error');
                return;
            }
            if (bc.solar) {
                payload.initialNodeBirthDate = bc.solar;
                payload.initialNodeBirthCalendar = FT.birthCalendar.getCalendarCode('init-birth');
            }
        }
        try {
            var data = await writeJson('/api/family', {
                method: 'POST',
                headers: jsonHeaders(),
                body: JSON.stringify(payload)
            });
            if (data.code === 200) {
                showMessage(nodeName ? '家族与初始节点创建成功，正在跳转...' : '家族创建成功，正在跳转...', 'success');
                setTimeout(function () {
                    window.location.href = '/index.html';
                }, REDIRECT_DELAY);
            } else {
                showMessage(data.message || '创建失败', 'error');
            }
        } catch (err) {
            showMessage('网络错误，请重试', 'error');
        }
    });

    document.getElementById('btn-join').addEventListener('click', async function () {
        var code = document.getElementById('join-code').value.trim();
        if (!code) {
            showMessage('请输入邀请码', 'error');
            return;
        }
        if (await profileReady === 'expired') {
            return;
        }
        try {
            var data = await writeJson('/api/family/join', {
                method: 'POST',
                headers: jsonHeaders(),
                body: JSON.stringify({inviteCode: code})
            });
            if (data.code === 200) {
                showMessage('加入成功，正在跳转...', 'success');
                setTimeout(function () {
                    window.location.href = '/index.html';
                }, REDIRECT_DELAY);
            } else {
                showMessage(data.message || '加入失败', 'error');
            }
        } catch (err) {
            showMessage('网络错误，请重试', 'error');
        }
    });

    // 出生日期接入阳历/阴历录入组件，真值输入框 id 保持 init-birth
    var birthField = document.getElementById('init-birth-field');
    if (birthField && window.FT && FT.birthCalendar) {
        birthField.innerHTML = FT.birthCalendar.buildField('init-birth', null);
        FT.birthCalendar.init('init-birth', {});
    }

    profileReady = prefillProfile();
})();
