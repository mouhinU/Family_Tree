/**
 * 族谱前端 - 登录后的家族过渡页
 * 列出该账号已归属的家族并可切换当前家族，同时保留创建新家族与凭邀请码加入已有家族的入口。
 *
 * @author Family-Tree
 * @date 2026-11-17
 */
(function () {
    'use strict';

    /** 出生日期输入框可接受的格式（yyyy-MM-dd） */
    var DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

    /** 家族角色编码到中文称谓 */
    var ROLE_LABELS = {OWNER: '族长', ADMIN: '管理员', MEMBER: '成员'};

    var messageEl = document.getElementById('portal-message');
    var listEl = document.getElementById('my-family-list');

    /** 当前家族ID，用于在列表中标记「当前」，未登录或无家族时为 null */
    var currentFamilyId = null;

    /** 从 sessionStorage 读取 CSRF Token 并组装 JSON 请求头 */
    function jsonHeaders() {
        var headers = {'Content-Type': 'application/json'};
        var csrfToken = sessionStorage.getItem('csrfToken');
        if (csrfToken) {
            headers['X-CSRF-TOKEN'] = csrfToken;
        }
        return headers;
    }

    function showMessage(text, type) {
        messageEl.textContent = text;
        messageEl.className = type;
    }

    function clearMessage() {
        messageEl.textContent = '';
        messageEl.className = '';
    }

    /** 建族/加入/切换成功后的跳转延时，留出时间展示成功提示 */
    var REDIRECT_DELAY = 800;

    function renderFamilies(families) {
        listEl.textContent = '';
        if (!families.length) {
            var empty = document.createElement('li');
            empty.className = 'family-empty';
            empty.textContent = '您还没有归属任何家族，可在下方创建或加入一个';
            listEl.appendChild(empty);
            return;
        }
        families.forEach(function (item) {
            var row = document.createElement('li');
            var name = document.createElement('span');
            name.textContent = item.name;
            row.appendChild(name);

            var role = document.createElement('span');
            role.className = 'family-role';
            role.textContent = '（' + (ROLE_LABELS[item.currentRole] || item.currentRole) + '）';
            row.appendChild(role);

            if (currentFamilyId && String(item.id) === String(currentFamilyId)) {
                var badge = document.createElement('span');
                badge.className = 'family-current';
                badge.textContent = '当前';
                row.appendChild(badge);
            }

            var enter = document.createElement('button');
            enter.type = 'button';
            enter.textContent = '进入';
            enter.dataset.familyId = item.id;
            enter.addEventListener('click', onEnterFamily);
            row.appendChild(enter);
            listEl.appendChild(row);
        });
    }

    /**
     * 拉取该账号归属的家族列表
     *
     * <p>依赖 profileReady 先完成：/api/auth/me 每次调用都会轮换 CSRF Token，
     * 切换家族的 PUT 必须使用刷新后的 Token，否则被安全校验拒绝。</p>
     */
    async function loadMyFamilies() {
        await profileReady;
        try {
            var res = await fetch('/api/family/my-list');
            if (!res.ok) {
                return;
            }
            var body = await res.json();
            if (body.code !== 200) {
                return;
            }
            renderFamilies(body.data || []);
        } catch (err) {
            // 网络异常时保留列表占位，不阻塞创建与加入
        }
    }

    async function onEnterFamily(event) {
        clearMessage();
        var familyId = event.currentTarget.dataset.familyId;
        await profileReady;
        try {
            var res = await fetch('/api/family/switch/' + familyId, {
                method: 'PUT',
                headers: jsonHeaders()
            });
            var data = await res.json();
            if (data.code === 200) {
                currentFamilyId = familyId;
                showMessage('已进入该家族，正在跳转...', 'success');
                setTimeout(function () {
                    window.location.href = '/index.html';
                }, REDIRECT_DELAY);
            } else {
                showMessage(data.message || '进入失败', 'error');
            }
        } catch (err) {
            showMessage('网络错误，请重试', 'error');
        }
    }

    async function onCreateFamily() {
        clearMessage();
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
                showMessage(nodeName ? '家族与初始节点创建成功，正在进入...' : '家族创建成功，正在进入...',
                    'success');
                setTimeout(function () {
                    window.location.href = '/index.html';
                }, REDIRECT_DELAY);
            } else {
                showMessage(data.message || '创建失败', 'error');
            }
        } catch (err) {
            showMessage('网络错误，请重试', 'error');
        }
    }

    async function onJoinFamily() {
        clearMessage();
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
                showMessage('加入成功，正在进入...', 'success');
                setTimeout(function () {
                    window.location.href = '/index.html';
                }, REDIRECT_DELAY);
            } else {
                showMessage(data.message || '加入失败', 'error');
            }
        } catch (err) {
            showMessage('网络错误，请重试', 'error');
        }
    }

    /**
     * 读取账号资料，刷新 CSRF Token 并确定当前家族。
     *
     * <p>/api/auth/me 每次调用都会重新生成 CSRF Token，因此必须把返回值写回 sessionStorage，
     * 否则后续 PUT/POST 会因安全校验失败被拒。其 familyId 即当前家族，用于列表标记「当前」。
     * 读取失败不阻塞页面其他操作。</p>
     */
    async function loadProfile() {
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
            currentFamilyId = body.data.familyId || null;
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

    document.getElementById('btn-create').addEventListener('click', onCreateFamily);
    document.getElementById('btn-join').addEventListener('click', onJoinFamily);

    var profileReady = loadProfile();
    loadMyFamilies();
})();
