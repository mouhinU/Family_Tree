/**
 * 族谱前端 - API 模块
 * 统一请求封装（401 跳登录）与族谱/辈分数据加载。
 *
 * @author Family-Tree
 * @date 2026-08-02
 */
(function () {
    'use strict';

    const FT = window.FT;

    /** CSRF Token（登录时从服务端获取） */
    var csrfToken = null;

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
        return body;
    }

    async function api(url, options) {
        var headers = {'Content-Type': 'application/json'};
        if (csrfToken) {
            headers['X-CSRF-TOKEN'] = csrfToken;
        }
        var res;
        try {
            res = await fetch(url, Object.assign({headers: headers}, options));
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
        return res.json();
    }

    async function loadTree() {
        const res = await api('/api/tree/full');
        if (res.code === 200) {
            FT.state.treeData = res.data || [];
            await loadGenerationNames();
            FT.buildGenerationWatermark();
            FT.renderTree();
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
    FT.uploadFile = uploadFile;
    FT.loadTree = loadTree;
    FT.loadGenerationNames = loadGenerationNames;
})();
