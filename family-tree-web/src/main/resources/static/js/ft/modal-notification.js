/**
 * 族谱前端 - 通知弹框模块
 * 展示用户通知列表，支持标记已读。
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
(function () {
    'use strict';

    var FT = window.FT;

    /**
     * 打开通知弹框
     */
    async function showNotificationModal() {
        FT.showModal('<h3 style="text-align:center;margin-bottom:12px;">通知中心</h3>'
            + '<div id="notification-list" style="min-height:100px;text-align:center;color:#6b6156;">加载中...</div>'
            + '<div style="text-align:right;margin-top:12px;padding-top:8px;border-top:1px solid #d8cba8;">'
            + '<button class="btn-sm" id="btn-mark-all-read" style="display:none;">全部已读</button> '
            + '<button class="btn-sm" data-close-modal>关闭</button>'
            + '</div>', 'modal-medium');

        bindModalEvents();
        await loadNotifications();
    }

    /**
     * 绑定弹框内事件
     */
    function bindModalEvents() {
        var markAllBtn = document.getElementById('btn-mark-all-read');
        if (markAllBtn) {
            markAllBtn.addEventListener('click', async function () {
                try {
                    await FT.api('/api/notification/read-all', { method: 'POST' });
                    FT.toast('已全部标记为已读');
                    await loadNotifications();
                    if (FT.wsLoadUnreadCount) { FT.wsLoadUnreadCount(); }
                } catch (e) {
                    FT.toast('操作失败', 'error');
                }
            });
        }
    }

    /**
     * 加载通知列表
     */
    async function loadNotifications() {
        var listEl = document.getElementById('notification-list');
        if (!listEl) { return; }

        try {
            var res = await FT.api('/api/notification/list?page=1&size=50');
            if (res.code === 200) {
                var list = res.data.list || [];
                if (list.length === 0) {
                    listEl.innerHTML = '<p style="padding:20px 0;color:#8a7f6a;">暂无通知</p>';
                    return;
                }
                var html = '<div style="text-align:left;">';
                list.forEach(function (n) {
                    var readClass = n.read ? '' : ' notification-unread';
                    var typeIcon = getTypeIcon(n.notificationType);
                    html += '<div class="notification-item' + readClass + '" data-id="' + n.id + '">'
                        + '<div class="notification-item-header">'
                        + '<span class="notification-type-icon">' + typeIcon + '</span>'
                        + '<strong>' + FT.escapeHtml(n.title) + '</strong>'
                        + '<span class="notification-time">' + FT.escapeHtml(n.timeAgo || '') + '</span>'
                        + '</div>'
                        + '<div class="notification-item-content">' + FT.escapeHtml(n.content || '') + '</div>'
                        + '</div>';
                });
                html += '</div>';
                listEl.innerHTML = html;

                // 显示全部已读按钮
                var markAllBtn = document.getElementById('btn-mark-all-read');
                var hasUnread = list.some(function (n) { return !n.read; });
                if (markAllBtn) {
                    markAllBtn.style.display = hasUnread ? 'inline-block' : 'none';
                }

                // 绑定单项点击标记已读
                listEl.querySelectorAll('.notification-item:not(.notification-unread)').forEach(function () {});
                listEl.querySelectorAll('.notification-unread').forEach(function (item) {
                    item.addEventListener('click', function () {
                        var id = item.dataset.id;
                        markAsRead(id, item);
                    });
                    item.style.cursor = 'pointer';
                });
            } else {
                listEl.innerHTML = '<p style="color:#a63a2b;">加载失败</p>';
            }
        } catch (e) {
            listEl.innerHTML = '<p style="color:#a63a2b;">请求失败</p>';
        }
    }

    /**
     * 标记单条通知已读
     */
    async function markAsRead(id, itemEl) {
        try {
            await FT.api('/api/notification/' + id + '/read', { method: 'POST' });
            itemEl.classList.remove('notification-unread');
            itemEl.style.cursor = 'default';
            if (FT.wsLoadUnreadCount) { FT.wsLoadUnreadCount(); }
        } catch (e) {
            // 忽略
        }
    }

    /**
     * 获取通知类型图标
     */
    function getTypeIcon(type) {
        switch (type) {
            case 'MEMBER_JOIN': return '&#128101;';
            case 'NODE_CREATE': return '&#128100;';
            case 'SYSTEM': return '&#128276;';
            default: return '&#128233;';
        }
    }

    FT.showNotificationModal = showNotificationModal;
})();
