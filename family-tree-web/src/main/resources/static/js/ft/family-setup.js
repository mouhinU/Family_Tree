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

    function showMessage(text, type) {
        messageEl.textContent = text;
        messageEl.className = type;
    }

    /**
     * 预填初始节点信息（昵称、出生日期），供创建者按需修改。
     *
     * <p>/api/auth/me 每次调用都会重新生成 CSRF Token，因此必须把返回值写回 sessionStorage，
     * 否则后续 POST 会因安全校验失败被拒。预填失败不阻塞创建家族。</p>
     */
    async function prefillProfile() {
        try {
            var res = await fetch('/api/auth/me');
            if (!res.ok) {
                return;
            }
            var body = await res.json();
            if (body.code !== 200 || !body.data) {
                return;
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
            }
        } catch (err) {
            // 未登录或接口异常时保持表单为空
        }
    }

    document.getElementById('btn-create').addEventListener('click', async function () {
        var name = document.getElementById('create-name').value.trim();
        if (!name) {
            showMessage('请输入家族名称', 'error');
            return;
        }
        await profileReady;
        var payload = {name: name};
        var nodeName = document.getElementById('init-name').value.trim();
        if (nodeName) {
            payload.initialNodeName = nodeName;
            var gender = document.getElementById('init-gender').value;
            if (gender) {
                payload.initialNodeGender = Number(gender);
            }
            var birthDate = document.getElementById('init-birth').value;
            if (birthDate) {
                payload.initialNodeBirthDate = birthDate;
            }
        }
        try {
            var res = await fetch('/api/family', {
                method: 'POST',
                headers: jsonHeaders(),
                body: JSON.stringify(payload)
            });
            var data = await res.json();
            if (data.code === 200) {
                showMessage(nodeName ? '家族与初始节点创建成功，正在跳转...' : '家族创建成功，正在跳转...', 'success');
                setTimeout(function () {
                    window.location.href = '/index.html';
                }, 800);
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
        await profileReady;
        try {
            var res = await fetch('/api/family/join', {
                method: 'POST',
                headers: jsonHeaders(),
                body: JSON.stringify({inviteCode: code})
            });
            var data = await res.json();
            if (data.code === 200) {
                showMessage('加入成功，正在跳转...', 'success');
                setTimeout(function () {
                    window.location.href = '/index.html';
                }, 800);
            } else {
                showMessage(data.message || '加入失败', 'error');
            }
        } catch (err) {
            showMessage('网络错误，请重试', 'error');
        }
    });

    profileReady = prefillProfile();
})();
