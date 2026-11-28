/**
 * 族谱前端 - API 模块
 * 统一请求封装（401 跳登录）、主动登出跳转与族谱/辈分数据加载。
 *
 * @author Family-Tree
 * @date 2026-08-02
 */
(function () {
    'use strict';

    const FT = window.FT;

    /** CSRF Token（登录时从服务端获取） */
    var csrfToken = null;

    /** 主动登出的落地页 */
    var LOGOUT_TARGET = '/home';

    /** 登出请求的最长等待：超过则直接导航，避免 Redis 异常时用户卡在退出按钮上 */
    var LOGOUT_NAV_TIMEOUT = 1500;

    /**
     * 初始化 CSRF Token（登录成功后调用）
     */
    FT.initCsrfToken = function (token) {
        csrfToken = token;
    };

    /**
     * 获取当前 CSRF Token（multipart 上传等自定义请求使用）
     */
    FT.getCsrfToken = function () {
        return csrfToken;
    };

    /**
     * multipart 文件上传（FormData 不能携带 Content-Type 头，故不复用 api）
     */
    async function uploadFile(url, formData) {
        var headers = {};
        if (csrfToken) {
            headers['X-CSRF-TOKEN'] = csrfToken;
        }
        var res;
        try {
            res = await fetch(url, {method: 'POST', headers: headers, body: formData});
        } catch (networkErr) {
            FT.toast('网络连接失败，请检查网络后重试', 'error');
            throw new Error('network.error');
        }
        if (res.status === 401) {
            FT.toast('登录已过期，请重新登录', 'warning');
            window.location.href = '/login.html';
            throw new Error('unauthorized');
        }
        var errMsg = '上传失败 (' + res.status + ')';
        var body = null;
        try {
            body = await res.json();
        } catch (parseErr) {
            // 非 JSON 响应，使用默认错误信息
        }
        if (!res.ok) {
            if (body && body.message) { errMsg = body.message; }
            FT.toast(errMsg, 'error');
            throw new Error(errMsg);
        }
        // 与 api() 一致：HTTP 200 但业务 code 非 200 时统一弹出错误
        if (body && typeof body.code === 'number' && body.code !== 200) {
            FT.toast(body.message || '上传失败', 'error');
        }
        return body;
    }

    async function api(url, options) {
        var headers = {'Content-Type': 'application/json'};
        if (csrfToken) {
            headers['X-CSRF-TOKEN'] = csrfToken;
        }
        var res;
        var opts = options || {};
        var mergedHeaders = Object.assign({}, headers, opts.headers || {});
        try {
            res = await fetch(url, Object.assign({}, opts, {headers: mergedHeaders}));
        } catch (networkErr) {
            FT.toast('网络连接失败，请检查网络后重试', 'error');
            throw new Error('network.error');
        }
        if (res.status === 401) {
            FT.toast('登录已过期，请重新登录', 'warning');
            window.location.href = '/login.html';
            throw new Error('unauthorized');
        }
        if (res.status === 403) {
            FT.toast('安全校验失败，页面将刷新', 'warning');
            window.location.href = '/login.html';
            throw new Error('csrf.invalid');
        }
        if (res.status === 429) {
            var retryAfter = res.headers.get('Retry-After') || '60';
            FT.toast('操作过于频繁，请 ' + retryAfter + ' 秒后重试', 'warning');
            throw new Error('rate_limited');
        }
        if (!res.ok) {
            var errMsg = '请求失败 (' + res.status + ')';
            try {
                var errBody = await res.json();
                if (errBody && errBody.message) {
                    errMsg = errBody.message;
                }
            } catch (parseErr) {
                // 非 JSON 响应，使用默认错误信息
            }
            FT.toast(errMsg, 'error');
            throw new Error(errMsg);
        }
        var body = await res.json();
        // 集中处理业务错误：后端 GlobalExceptionHandler 以 HTTP 200 + Result.code 返回失败，
        // 调用方若不判断 res.code 会静默失败。此处统一弹出错误，返回值仍交给调用方按需判断。
        if (body && typeof body.code === 'number' && body.code !== 200) {
            FT.toast(body.message || '操作失败', 'error');
        }
        return body;
    }

    /**
     * 主动登出并跳到 Home。
     * 不复用 api()：401/403 分支会把用户送去登录页并弹「登录已过期」，
     * 与登出这个主动动作竞争同一次导航。
     * 导航要等登出请求落定：落地页若与 POST /logout 并发，服务端会在响应提交处抛
     * IllegalStateException("Session was invalidated")（RedisSessionRepository.save），
     * 整页变成 500；超时兜底保证 Redis 异常时仍能离开当前页。
     */
    function logout() {
        var headers = {};
        if (csrfToken) {
            headers['X-CSRF-TOKEN'] = csrfToken;
        }
        csrfToken = null;
        // 服务端会话随 logout 失效，留在 sessionStorage 的旧令牌在下次启动时会被误当作可恢复态
        sessionStorage.removeItem('csrfToken');
        var navigated = false;
        function goHome() {
            if (navigated) {
                return;
            }
            navigated = true;
            clearTimeout(timer);
            window.location.href = LOGOUT_TARGET;
        }
        var timer = setTimeout(goHome, LOGOUT_NAV_TIMEOUT);
        // keepalive 必须带：超时兜底分支会立刻卸载页面，普通 fetch 会被中止而丢不掉服务端会话
        fetch('/api/auth/logout', {method: 'POST', headers: headers, keepalive: true})
            .catch(function () {
                // 会话已失效或断网时不报错，导航照常
            })
            .then(goHome);
    }

    async function loadTree() {
        const res = await api('/api/tree/full');
        if (res.code === 200) {
            FT.state.treeData = res.data || [];
            await Promise.all([loadDetailScope(), loadGenerationNames()]);
            FT.buildGenerationWatermark();
            FT.renderTree();
        }
    }

    // 拉取当前用户可详情的节点白名单：null=不受限（管理员/拉取失败时放行，后端仍独立校验）
    async function loadDetailScope() {
        FT.state.detailVisibleIds = null;
        const res = await api('/api/tree/detail-scope');
        if (res.code === 200 && res.data) {
            FT.state.detailVisibleIds = res.data.unrestricted
                ? null
                : new Set(res.data.nodeIds || []);
        }
    }

    // 加载辈分名（世代名称）映射与行列布局
    async function loadGenerationNames() {
        FT.state.generationNames = {};
        const res = await api('/api/generation');
        if (res.code === 200 && res.data) {
            res.data.forEach(function (g) {
                FT.state.generationNames[g.generation] = g.name;
            });
        }
        // 加载行列布局
        const layoutRes = await api('/api/generation/layout');
        if (layoutRes.code === 200 && layoutRes.data) {
            FT.state.generationCols = layoutRes.data.cols || 5;
            FT.state.generationRows = layoutRes.data.rows || 5;
        }
    }

    FT.api = api;
    FT.logout = logout;
    FT.uploadFile = uploadFile;
    FT.loadTree = loadTree;
    FT.loadDetailScope = loadDetailScope;
    FT.loadGenerationNames = loadGenerationNames;
})();
