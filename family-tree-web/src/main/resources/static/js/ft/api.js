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

    async function api(url, options) {
        const res = await fetch(url, Object.assign({
            headers: {'Content-Type': 'application/json'}
        }, options));
        if (res.status === 401) {
            window.location.href = '/login.html';
            throw new Error('unauthorized');
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

    // 加载辈分名（世代名称）映射
    async function loadGenerationNames() {
        FT.state.generationNames = {};
        const res = await api('/api/generation');
        if (res.code === 200 && res.data) {
            res.data.forEach(function (g) {
                FT.state.generationNames[g.generation] = g.name;
            });
        }
    }

    FT.api = api;
    FT.loadTree = loadTree;
    FT.loadGenerationNames = loadGenerationNames;
})();
