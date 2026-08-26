/**
 * 族谱前端 - 留言板模块
 * 家族留言板：发布留言、分页列表、删除自己的留言、点赞/取消点赞。
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
(function () {
    'use strict';

    var FT = window.FT;
    var PAGE_SIZE = 20;

    /**
     * 打开留言板弹窗
     * @param {number} [page] 页码（默认 1）
     */
    async function showMessageModal(page) {
        page = page || 1;

        var res = await FT.api('/api/message?page=' + page + '&size=' + PAGE_SIZE);
        if (res.code !== 200) {
            FT.toast(res.message || '加载留言失败');
            return;
        }

        var data = res.data;
        var records = data.records || [];
        var total = data.total || 0;
        var totalPages = data.totalPages || 1;

        // 留言列表
        var listHtml = '';
        if (records.length === 0) {
            listHtml = '<div class="msg-empty">暂无留言，写下第一条留言吧</div>';
        } else {
            listHtml = '<div class="msg-list">';
            records.forEach(function (msg) {
                var time = msg.createTime ? msg.createTime.replace('T', ' ').substring(0, 16) : '';
                var deleteBtn = '';
                if (msg.own) {
                    deleteBtn = '<button class="msg-delete-btn" data-msg-id="' + msg.id + '" title="删除">&times;</button>';
                }
                var likeCount = msg.likeCount || 0;
                var likeClass = msg.liked ? 'msg-liked' : '';
                var likeIcon = msg.liked ? '&#9829;' : '&#9825;';
                listHtml += '<div class="msg-item">' +
                    '<div class="msg-header">' +
                    '<span class="msg-username">' + FT.escapeHtml(msg.username) + '</span>' +
                    '<span class="msg-time">' + FT.escapeHtml(time) + '</span>' +
                    deleteBtn +
                    '</div>' +
                    '<div class="msg-content">' + FT.escapeHtml(msg.content) + '</div>' +
                    '<div class="msg-footer">' +
                    '<button class="msg-like-btn ' + likeClass + '" data-msg-id="' + msg.id + '">' +
                    '<span class="msg-like-icon">' + likeIcon + '</span>' +
                    '<span class="msg-like-count">' + likeCount + '</span>' +
                    '</button>' +
                    '</div>' +
                    '</div>';
            });
            listHtml += '</div>';
        }

        // 分页
        var pageHtml = '';
        if (totalPages > 1) {
            pageHtml = '<div class="msg-pagination">';
            pageHtml += '<span class="msg-page-info">共 ' + total + ' 条</span>';
            pageHtml += '<span class="msg-page-nav">';
            if (page > 1) {
                pageHtml += '<button class="msg-page-btn" data-page="' + (page - 1) + '">&laquo; 上一页</button>';
            }
            pageHtml += '<span class="msg-page-current">第 ' + page + ' / ' + totalPages + ' 页</span>';
            if (page < totalPages) {
                pageHtml += '<button class="msg-page-btn" data-page="' + (page + 1) + '">下一页 &raquo;</button>';
            }
            pageHtml += '</span></div>';
        }

        // 输入区域
        var inputHtml = '<div class="msg-input-area">' +
            '<textarea id="msg-input" class="msg-textarea" placeholder="写下你想说的..." maxlength="500" rows="3"></textarea>' +
            '<div class="msg-input-footer">' +
            '<span class="msg-char-count" id="msg-char-count">0 / 500</span>' +
            '<button class="msg-submit-btn" id="msg-submit-btn">发布留言</button>' +
            '</div></div>';

        var bodyHtml = '<h3 class="msg-modal-title">家族留言板</h3>' +
            listHtml + pageHtml + inputHtml +
            '<div class="msg-modal-actions">' +
            '<button class="msg-close-btn" data-close-modal>关闭</button></div>';

        FT.showModal(bodyHtml, 'modal-wide');

        // 绑定字数统计
        var textarea = document.getElementById('msg-input');
        var charCount = document.getElementById('msg-char-count');
        textarea.addEventListener('input', function () {
            charCount.textContent = this.value.length + ' / 500';
        });

        // 绑定发布
        document.getElementById('msg-submit-btn').addEventListener('click', async function () {
            var content = textarea.value.trim();
            if (!content) {
                FT.toast('请输入留言内容', 'warning');
                return;
            }
            this.disabled = true;
            this.textContent = '发布中...';
            var submitRes = await FT.api('/api/message', {
                method: 'POST',
                body: JSON.stringify({ content: content })
            });
            if (submitRes.code === 200) {
                FT.toast('留言成功');
                if (FT.refreshMessageCarousel) FT.refreshMessageCarousel();
                showMessageModal(1);
            } else {
                FT.toast(submitRes.message || '发布失败');
                this.disabled = false;
                this.textContent = '发布留言';
            }
        });

        // 绑定分页
        document.querySelectorAll('.msg-page-btn').forEach(function (btn) {
            btn.addEventListener('click', function () {
                showMessageModal(parseInt(btn.dataset.page, 10));
            });
        });

        // 绑定删除
        document.querySelectorAll('.msg-delete-btn').forEach(function (btn) {
            btn.addEventListener('click', async function () {
                var msgId = btn.dataset.msgId;
                if (!confirm('确定删除这条留言吗？')) return;
                var delRes = await FT.api('/api/message/' + msgId, { method: 'DELETE' });
                if (delRes.code === 200) {
                    FT.toast('已删除');
                    if (FT.refreshMessageCarousel) FT.refreshMessageCarousel();
                    showMessageModal(page);
                } else {
                    FT.toast(delRes.message || '删除失败');
                }
            });
        });

        // 绑定点赞/取消点赞
        document.querySelectorAll('.msg-like-btn').forEach(function (btn) {
            btn.addEventListener('click', async function () {
                var msgId = btn.dataset.msgId;
                var isLiked = btn.classList.contains('msg-liked');
                var url = '/api/message/' + msgId + '/like';
                var method = isLiked ? 'DELETE' : 'POST';

                try {
                    var likeRes = await FT.api(url, { method: method });
                    if (likeRes.code === 200) {
                        // 刷新当前页
                        showMessageModal(page);
                    } else {
                        FT.toast(likeRes.message || '操作失败');
                    }
                } catch (e) {
                    FT.toast('网络错误，请重试');
                }
            });
        });
    }

    FT.showMessageModal = showMessageModal;
})();
