/**
 * 族谱前端 - 右键菜单模块
 * 节点右键菜单的显示/隐藏与菜单动作分发。
 *
 * @author Family-Tree
 * @date 2026-08-02
 */
(function () {
    'use strict';

    const FT = window.FT;

    const contextMenu = document.getElementById('context-menu');

    function showContextMenu(x, y) {
        contextMenu.style.display = 'block';
        contextMenu.style.left = x + 'px';
        contextMenu.style.top = y + 'px';
    }

    function hideContextMenu() {
        contextMenu.style.display = 'none';
        FT.state.contextNodeId = null;
    }

    document.addEventListener('click', hideContextMenu);
    document.addEventListener('contextmenu', function (e) {
        if (!e.target.closest('.node-group')) {
            hideContextMenu();
        }
    });

    contextMenu.querySelectorAll('.menu-item').forEach(function (item) {
        item.addEventListener('click', function () {
            const action = this.getAttribute('data-action');
            const nodeId = FT.state.contextNodeId;
            hideContextMenu();
            if (!nodeId) return;

            switch (action) {
                case 'add-child': FT.showNodeModal('添加子女', {parentNodeId: nodeId}); break;
                case 'add-spouse': FT.showNodeModal('添加配偶', {spouseNodeId: nodeId}); break;
                case 'link-spouse': FT.showLinkSpouseModal(nodeId); break;
                case 'add-parent': FT.showNodeModal('添加父母', {childNodeId: nodeId}); break;
                case 'birth-order': FT.showBirthOrderModal(nodeId); break;
                case 'edit': FT.editNode(nodeId); break;
                case 'color': FT.showColorModal(nodeId); break;
                case 'delete': FT.deleteNode(nodeId); break;
            }
        });
    });

    FT.showContextMenu = showContextMenu;
    FT.hideContextMenu = hideContextMenu;
})();
