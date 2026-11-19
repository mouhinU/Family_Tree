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
            empty.textContent = '您还没有归属任何家族，可在「创建新家族」或「加入已有家族」中开始';
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
        if (await profileReady === 'expired') {
            return;
        }
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
        if (await profileReady === 'expired') {
            return;
        }
        try {
            var data = await writeJson('/api/family/switch/' + familyId, {
                method: 'PUT',
                headers: jsonHeaders()
            });
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
     * 带会话自愈的写请求：CSRF 被拒（403）时先经 /api/auth/me 刷新令牌再重试一次。
     *
     * <p>会话为容器内存存储，发版重启或闲置超时后会话即失效，此时旧令牌会被 CSRF 过滤器
     * 以 403 拒绝；这里区分「令牌过期」与「会话过期」，后者提示并返回登录页，避免把
     * 登录态问题误报成 CSRF 校验失败。</p>
     *
     * @param url 请求地址
     * @param options fetch 配置（headers 会在重试时替换为刷新后的令牌）
     * @return 响应体 Result 对象
     */
    async function writeJson(url, options) {
        var res = await fetch(url, options);
        var data = await res.json();
        if (res.status === 403 || data.code === 403) {
            var state = await loadProfile();
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
     * 读取账号资料，刷新 CSRF Token 并确定当前家族。
     *
     * <p>/api/auth/me 每次调用都会重新生成 CSRF Token，因此必须把返回值写回 sessionStorage，
     * 否则后续 PUT/POST 会因安全校验失败被拒。其 familyId 即当前家族，用于列表标记「当前」。
     * 会话过期时提示并返回登录页；其它读取失败不阻塞页面其他操作。</p>
     *
     * @return 'ok' 读取成功；'expired' 会话过期（已安排跳转登录页）；'error' 其它失败
     */
    async function loadProfile() {
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
            currentFamilyId = body.data.familyId || null;
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
            // 网络异常时保持表单为空，不阻塞创建与加入
            return 'error';
        }
    }

    document.getElementById('btn-create').addEventListener('click', onCreateFamily);
    document.getElementById('btn-join').addEventListener('click', onJoinFamily);

    // 出生日期接入阳历/阴历录入组件，真值输入框 id 保持 init-birth
    var birthField = document.getElementById('init-birth-field');
    if (birthField && window.FT && FT.birthCalendar) {
        birthField.innerHTML = FT.birthCalendar.buildField('init-birth', null);
        FT.birthCalendar.init('init-birth', {});
    }

    var profileReady = loadProfile();
    loadMyFamilies();
})();
