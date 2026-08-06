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

    async function api(url, options) {
        var headers = {'Content-Type': 'application/json'};
        if (csrfToken) {
            headers['X-CSRF-TOKEN'] = csrfToken;
        }
        const res = await fetch(url, Object.assign({headers: headers}, options));
        if (res.status === 401) {
            window.location.href = '/login.html';
            throw new Error('unauthorized');
        }
        if (res.status === 403) {
            // CSRF 校验失败，跳转登录页重新获取 token
            window.location.href = '/login.html';
            throw new Error('csrf.invalid');
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
    FT.loadTree = loadTree;
    FT.loadGenerationNames = loadGenerationNames;
})();
