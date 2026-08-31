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
        var MARGIN = 8;
        contextMenu.style.display = 'block';
        contextMenu.style.left = '0px';
        contextMenu.style.top = '0px';

        var menuW = contextMenu.offsetWidth;
        var menuH = contextMenu.offsetHeight;
        var vw = window.innerWidth;
        var vh = window.innerHeight;

        var left = x;
        var top = y;

        // 右侧溢出 → 移到触摸点左侧
        if (left + menuW + MARGIN > vw) {
            left = x - menuW;
        }
        // 左侧溢出 → 强制贴左边
        if (left < MARGIN) {
            left = MARGIN;
        }
        // 底部溢出 → 移到触摸点上方
        if (top + menuH + MARGIN > vh) {
            top = y - menuH;
        }
        // 顶部溢出 → 强制贴顶
        if (top < MARGIN) {
            top = MARGIN;
        }

        contextMenu.style.left = left + 'px';
        contextMenu.style.top = top + 'px';
    }

    function hideContextMenu() {
        contextMenu.style.display = 'none';
        FT.state.contextNodeId = null;
        // 重置"更多"展开状态
        var moreContainer = document.getElementById('context-menu-more');
        var moreToggle = document.getElementById('menu-more-toggle');
        if (moreContainer) {
            moreContainer.classList.remove('expanded');
        }
        if (moreToggle) {
            moreToggle.textContent = '更多 ▾';
        }
    }

    document.addEventListener('click', hideContextMenu);
    document.addEventListener('contextmenu', function (e) {
        if (!e.target.closest('.node-group')) {
            hideContextMenu();
        }
    });

    /* ===== 移动端长按触发菜单 ===== */
    var longPressTimer = null;
    var longPressTriggered = false;
    var LONG_PRESS_DURATION = 600;

    document.addEventListener('touchstart', function (e) {
        var nodeGroup = e.target.closest('.node-group');
        if (!nodeGroup) return;

        longPressTriggered = false;
        var touch = e.touches[0];

        longPressTimer = setTimeout(function () {
            longPressTriggered = true;
            longPressTimer = null;

            var nodeId = nodeGroup.__data__ && nodeGroup.__data__.id;
            if (nodeId) {
                FT.state.contextNodeId = nodeId;
                FT.showContextMenu(touch.clientX, touch.clientY);
            }
        }, LONG_PRESS_DURATION);
    }, {passive: true});

    document.addEventListener('touchmove', function () {
        if (longPressTimer) {
            clearTimeout(longPressTimer);
            longPressTimer = null;
        }
    }, {passive: true});

    document.addEventListener('touchend', function (e) {
        if (longPressTimer) {
            clearTimeout(longPressTimer);
            longPressTimer = null;
        }
        if (longPressTriggered) {
            e.preventDefault();
            longPressTriggered = false;
        }
    });

    document.addEventListener('touchcancel', function () {
        if (longPressTimer) {
            clearTimeout(longPressTimer);
            longPressTimer = null;
        }
        longPressTriggered = false;
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
                case 'adoption': FT.showAdoptionModal(nodeId); break;
                case 'birth-order': FT.showBirthOrderModal(nodeId); break;
                case 'mark-self': FT.markAsSelf(nodeId); break;
                case 'edit': FT.editNode(nodeId); break;
                case 'color': FT.showColorModal(nodeId); break;
                case 'biography': FT.showBiographyModal(nodeId); break;
                case 'history': FT.showVersionHistoryModal(nodeId); break;
                case 'delete': FT.deleteNode(nodeId); break;
            }
        });
    });

    FT.showContextMenu = showContextMenu;
    FT.hideContextMenu = hideContextMenu;

    /* ===== "更多"展开/收起（移动端） ===== */
    var moreToggleBtn = document.getElementById('menu-more-toggle');
    var moreContainer = document.getElementById('context-menu-more');
    if (moreToggleBtn && moreContainer) {
        moreToggleBtn.addEventListener('click', function (e) {
            e.stopPropagation();
            var isExpanded = moreContainer.classList.toggle('expanded');
            moreToggleBtn.textContent = isExpanded ? '收起 ▴' : '更多 ▾';
        });
    }
})();
