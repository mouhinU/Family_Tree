/**
 * 族谱前端 - 私信模块
 * 会话列表、发起私信、聊天窗口。
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
(function () {
    'use strict';

    var FT = window.FT;

    async function showChatModal() {
        var res = await FT.api('/api/private-message/conversations');
        if (res.code !== 200) { FT.toast(res.message || '加载失败'); return; }
        var conversations = res.data || [];

        var html = '<h3 style="margin-bottom:10px;">私信</h3>' +
            '<div class="forum-toolbar">' +
            '<button class="btn-confirm" id="chat-new-btn">发起私信</button>' +
            '</div>' +
            '<div class="chat-list" id="chat-list"></div>' +
            '<div class="modal-actions"><button class="btn-cancel" data-close-modal>关闭</button></div>';
        FT.showModal(html, true);

        var list = document.getElementById('chat-list');
        if (conversations.length === 0) {
            list.innerHTML = '<p style="color:#999;text-align:center;padding:30px 0;">暂无私信，点击"发起私信"与族人聊聊吧</p>';
        } else {
            var items = '';
            conversations.forEach(function (c) {
                var unread = c.unreadCount > 0
                    ? '<span class="chat-badge">' + c.unreadCount + '</span>' : '';
                items += '<div class="chat-item js-chat-open" data-peer-id="' + c.peerUserId + '" data-peer-name="' + FT.escapeAttr(c.peerName) + '">' +
                    '<div class="chat-item-main">' +
                    '<div class="chat-item-name">' + FT.escapeHtml(c.peerName) + unread + '</div>' +
                    '<div class="chat-item-last">' + FT.escapeHtml(c.lastContent || '') + '</div>' +
                    '</div>' +
                    '<div class="chat-item-time">' + formatTime(c.lastTime) + '</div>' +
                    '</div>';
            });
            list.innerHTML = items;
            list.querySelectorAll('.js-chat-open').forEach(function (el) {
                el.addEventListener('click', function () {
                    showConversationModal(parseInt(el.dataset.peerId, 10), el.dataset.peerName);
                });
            });
        }

        document.getElementById('chat-new-btn').addEventListener('click', showNewChatModal);
    }

    // 发起私信：选择收信人
    async function showNewChatModal() {
        var res = await FT.api('/api/private-message/contacts');
        if (res.code !== 200) { FT.toast(res.message || '加载失败'); return; }
        var contacts = res.data || [];

        var options = '<option value="">请选择族人</option>';
        contacts.forEach(function (c) {
            options += '<option value="' + c.id + '">' + FT.escapeHtml(c.username) + '</option>';
        });

        var html = '<h3 style="margin-bottom:10px;">发起私信</h3>' +
            '<div class="form-group"><label>收信人</label>' +
            '<select id="chat-peer-select">' + options + '</select></div>' +
            '<div class="modal-actions">' +
            '<button class="btn-cancel" data-close-modal>取消</button>' +
            '<button class="btn-confirm" id="chat-open-btn">开始聊天</button>' +
            '</div>';
        FT.showModal(html);

        document.getElementById('chat-open-btn').addEventListener('click', function () {
            var select = document.getElementById('chat-peer-select');
            if (!select.value) { FT.toast('请选择族人', 'warning'); return; }
            var peerName = select.options[select.selectedIndex].textContent;
            showConversationModal(parseInt(select.value, 10), peerName);
        });
    }

    // 聊天窗口
    async function showConversationModal(peerId, peerName) {
        var res = await FT.api('/api/private-message/conversation/' + peerId);
        if (res.code !== 200) { FT.toast(res.message || '加载失败'); return; }
        var messages = res.data || [];

        var bubbles = '';
        messages.forEach(function (m) {
            bubbles += '<div class="chat-bubble ' + (m.own ? 'chat-bubble--own' : 'chat-bubble--peer') + '">' +
                '<div class="chat-bubble-name">' + FT.escapeHtml(m.senderName || '') + ' · ' + formatTime(m.createTime) + '</div>' +
                '<div class="chat-bubble-text">' + FT.escapeHtml(m.content) + '</div>' +
                '</div>';
        });

        var html = '<h3 style="margin-bottom:10px;">与 ' + FT.escapeHtml(peerName) + ' 的对话</h3>' +
            '<div class="chat-window" id="chat-window">' +
            (bubbles || '<p style="color:#999;text-align:center;padding:20px 0;">还没有消息，说点什么吧</p>') +
            '</div>' +
            '<div class="chat-input-box">' +
            '<textarea id="chat-input" maxlength="500" placeholder="输入消息（500字以内），回车发送"></textarea>' +
            '<button class="btn-confirm" id="chat-send-btn">发送</button>' +
            '</div>' +
            '<div class="modal-actions">' +
            '<button class="btn-cancel" id="chat-back-btn">返回会话列表</button>' +
            '<button class="btn-cancel" data-close-modal>关闭</button>' +
            '</div>';
        FT.showModal(html);

        var windowEl = document.getElementById('chat-window');
        windowEl.scrollTop = windowEl.scrollHeight;

        async function send() {
            var input = document.getElementById('chat-input');
            var content = input.value.trim();
            if (!content) { return; }
            input.disabled = true;
            var sendRes = await FT.api('/api/private-message', {
                method: 'POST',
                body: JSON.stringify({receiverId: peerId, content: content})
            });
            input.disabled = false;
            if (sendRes.code === 200) {
                input.value = '';
                showConversationModal(peerId, peerName);
            }
        }

        document.getElementById('chat-send-btn').addEventListener('click', send);
        document.getElementById('chat-input').addEventListener('keydown', function (e) {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                send();
            }
        });
        document.getElementById('chat-back-btn').addEventListener('click', showChatModal);
    }

    function formatTime(time) {
        return time ? String(time).replace('T', ' ').substring(0, 16) : '';
    }

    FT.showChatModal = showChatModal;
})();
