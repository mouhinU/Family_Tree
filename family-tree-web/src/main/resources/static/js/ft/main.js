/**
 * 族谱前端 - 主入口模块
 * 工具栏事件绑定与应用初始化（须最后加载）。
 *
 * @author Family-Tree
 * @date 2026-08-02
 */
(function () {
    'use strict';

    const FT = window.FT;

    async function init() {
        FT.setupScrollOverlay();
        try {
            const res = await FT.api('/api/auth/me');
            if (res.code !== 200) {
                window.location.href = '/login.html';
                return;
            }
            FT.state.currentUser = res.data;
            document.getElementById('user-nickname').textContent = FT.state.currentUser.nickname || '';
        } catch (e) {
            window.location.href = '/login.html';
            return;
        }
        bindEvents();
        await FT.loadTree();
    }

    // ========== 事件绑定 ==========
    function bindEvents() {
        document.getElementById('btn-logout').addEventListener('click', async function () {
            await FT.api('/api/auth/logout', {method: 'POST'});
            window.location.href = '/login.html';
        });

        document.getElementById('btn-add-root').addEventListener('click', function () {
            FT.showNodeModal('添加始祖', {});
        });

        document.getElementById('btn-generation').addEventListener('click', function () {
            FT.showGenerationModal();
        });

        // 导出族谱：将整棵树（含宣纸背景与辈分水印）导出为单页 PDF
        document.getElementById('btn-export').addEventListener('click', function () {
            FT.exportToPdf();
        });

        document.getElementById('chk-hide-deceased').addEventListener('change', function () {
            FT.state.hideDeceased = this.checked;
            FT.renderTree();
        });

        document.getElementById('chk-hide-marryout').addEventListener('change', function () {
            FT.state.hideMarryOut = this.checked;
            FT.renderTree();
        });

        document.getElementById('sel-layout').addEventListener('change', function () {
            FT.state.layoutDirection = this.value;
            FT.renderTree();
        });

        // 颜色图例折叠开关：默认隐藏，点击展开 / 收起
        document.getElementById('legend-toggle').addEventListener('click', function () {
            document.getElementById('legend').classList.toggle('open');
        });

        // 工具栏整体下拉隐藏：点击把手收起 / 展开，画布随之上移填补
        document.getElementById('toolbar-handle').addEventListener('click', function () {
            const collapsed = document.getElementById('toolbar').classList.toggle('collapsed');
            document.body.classList.toggle('toolbar-hidden', collapsed);
            this.title = collapsed ? '展开工具栏' : '收起工具栏';
        });
    }

    // 启动
    init();
})();
