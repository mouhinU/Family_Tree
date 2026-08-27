/**
 * 族谱前端 - 留言板模块
 * 家族留言板：发布留言、分页列表、删除自己的留言、点赞/取消点赞、分类筛选、回复。
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
(function () {
    'use strict';

    var FT = window.FT;
    var PAGE_SIZE = 20;

    /** 当前选中的分类（null 表示全部） */
    var currentCategory = null;

    /**
     * 分类配置
     */
    var CATEGORIES = [
        { code: null, label: '全部' },
        { code: 'GENERAL', label: '普通留言' },
        { code: 'FEATURE', label: '功能需求' }
    ];

    /**
     * 获取分类标签样式类名
     * @param {string} category 分类编码
     * @returns {string} CSS 类名
     */
    function getCategoryTagClass(category) {
        if (category === 'FEATURE') {
            return 'msg-tag-feature';
        }
        return 'msg-tag-general';
    }

    /**
     * 渲染单条回复 HTML
     * @param {Object} reply 回复数据
     * @returns {string} HTML
     */
    function renderReplyItem(reply) {
        var time = reply.createTime ? reply.createTime.replace('T', ' ').substring(0, 16) : '';
        var deleteBtn = '';
        if (reply.own) {
            deleteBtn = '<button class="msg-reply-delete-btn" data-reply-id="' + reply.id + '" title="删除">&times;</button>';
        }
        var likeCount = reply.likeCount || 0;
        var likeClass = reply.liked ? 'msg-liked' : '';
        var likeIcon = reply.liked ? '&#9829;' : '&#9825;';
        return '<div class="msg-reply-item" data-reply-id="' + reply.id + '">' +
            '<div class="msg-reply-header">' +
            '<span class="msg-reply-username">' + FT.escapeHtml(reply.username) + '</span>' +
            '<span class="msg-reply-time">' + FT.escapeHtml(time) + '</span>' +
            deleteBtn +
            '</div>' +
            '<div class="msg-reply-content">' + FT.escapeHtml(reply.content) + '</div>' +
            '<div class="msg-reply-footer">' +
            '<button class="msg-like-btn ' + likeClass + '" data-msg-id="' + reply.id + '">' +
            '<span class="msg-like-icon">' + likeIcon + '</span>' +
            '<span class="msg-like-count">' + likeCount + '</span>' +
            '</button>' +
            '</div>' +
            '</div>';
    }

    /**
     * 渲染回复列表 HTML
     * @param {Object} msg 留言数据
     * @returns {string} HTML
     */
    function renderRepliesSection(msg) {
        var replyCount = msg.replyCount || 0;
        var replies = msg.replies || [];
        var html = '<div class="msg-replies-section" data-parent-id="' + msg.id + '">';

        if (replyCount > 0) {
            html += '<div class="msg-replies-header">';
            html += '<span class="msg-replies-count">' + replyCount + ' 条回复</span>';
            html += '</div>';
            html += '<div class="msg-replies-list">';
            replies.forEach(function (reply) {
                html += renderReplyItem(reply);
            });
            html += '</div>';
        }

        // 回复输入区域（默认隐藏）
        html += '<div class="msg-reply-input-area" id="msg-reply-input-' + msg.id + '" style="display:none;">' +
            '<textarea class="msg-reply-textarea" placeholder="写下你的回复..." maxlength="500" rows="2"></textarea>' +
            '<div class="msg-reply-input-footer">' +
            '<span class="msg-reply-char-count">0 / 500</span>' +
            '<button class="msg-reply-submit-btn" data-parent-id="' + msg.id + '">回复</button>' +
            '</div></div>';

        html += '</div>';
        return html;
    }

    /**
     * 打开留言板弹窗
     * @param {number} [page] 页码（默认 1）
     */
    async function showMessageModal(page) {
        page = page || 1;

        var url = '/api/message?page=' + page + '&size=' + PAGE_SIZE;
        if (currentCategory) {
            url += '&category=' + currentCategory;
        }

        var res = await FT.api(url);
        if (res.code !== 200) {
            FT.toast(res.message || '加载留言失败');
            return;
        }

        var data = res.data;
        var records = data.records || [];
        var total = data.total || 0;
        var totalPages = data.totalPages || 1;

        // 分类筛选标签
        var tabsHtml = '<div class="msg-category-tabs">';
        CATEGORIES.forEach(function (cat) {
            var activeClass = (cat.code === currentCategory) ? ' msg-tab-active' : '';
            var dataAttr = cat.code ? ' data-category="' + cat.code + '"' : ' data-category=""';
            tabsHtml += '<button class="msg-category-tab' + activeClass + '"' + dataAttr + '>' +
                FT.escapeHtml(cat.label) + '</button>';
        });
        tabsHtml += '</div>';

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
                var tagClass = getCategoryTagClass(msg.category);
                var categoryDesc = msg.categoryDesc || '普通留言';
                listHtml += '<div class="msg-item" data-msg-id="' + msg.id + '">' +
                    '<div class="msg-header">' +
                    '<span class="msg-username">' + FT.escapeHtml(msg.username) + '</span>' +
                    '<span class="msg-time">' + FT.escapeHtml(time) + '</span>' +
                    '<span class="msg-category-tag ' + tagClass + '">' + FT.escapeHtml(categoryDesc) + '</span>' +
                    deleteBtn +
                    '</div>' +
                    '<div class="msg-content">' + FT.escapeHtml(msg.content) + '</div>' +
                    '<div class="msg-footer">' +
                    '<button class="msg-like-btn ' + likeClass + '" data-msg-id="' + msg.id + '">' +
                    '<span class="msg-like-icon">' + likeIcon + '</span>' +
                    '<span class="msg-like-count">' + likeCount + '</span>' +
                    '</button>' +
                    '<button class="msg-reply-btn" data-parent-id="' + msg.id + '">&#8617; 回复</button>' +
                    '</div>' +
                    renderRepliesSection(msg) +
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

        // 输入区域（含分类选择）
        var inputHtml = '<div class="msg-input-area">' +
            '<textarea id="msg-input" class="msg-textarea" placeholder="写下你想说的..." maxlength="500" rows="3"></textarea>' +
            '<div class="msg-input-footer">' +
            '<span class="msg-category-select">' +
            '<label class="msg-select-label">分类：</label>' +
            '<select id="msg-category-input" class="msg-select">' +
            '<option value="GENERAL">普通留言</option>' +
            '<option value="FEATURE">功能需求</option>' +
            '</select>' +
            '</span>' +
            '<span class="msg-char-count" id="msg-char-count">0 / 500</span>' +
            '<button class="msg-submit-btn" id="msg-submit-btn">发布留言</button>' +
            '</div></div>';

        var bodyHtml = '<h3 class="msg-modal-title">家族留言板</h3>' +
            tabsHtml + listHtml + pageHtml + inputHtml +
            '<div class="msg-modal-actions">' +
            '<button class="msg-close-btn" data-close-modal>关闭</button></div>';

        FT.showModal(bodyHtml, 'modal-wide');

        // 绑定分类筛选
        document.querySelectorAll('.msg-category-tab').forEach(function (tab) {
            tab.addEventListener('click', function () {
                var cat = tab.dataset.category || null;
                currentCategory = cat || null;
                showMessageModal(1);
            });
        });

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
            var category = document.getElementById('msg-category-input').value;
            this.disabled = true;
            this.textContent = '发布中...';
            var submitRes = await FT.api('/api/message', {
                method: 'POST',
                body: JSON.stringify({ content: content, category: category })
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

        // 绑定删除（顶级留言）
        document.querySelectorAll('.msg-delete-btn').forEach(function (btn) {
            btn.addEventListener('click', async function () {
                var msgId = btn.dataset.msgId;
                if (!confirm('确定删除这条留言吗？删除后所有回复也将一并删除。')) return;
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

        // 绑定点赞/取消点赞（含回复的点赞）
        document.querySelectorAll('.msg-like-btn').forEach(function (btn) {
            btn.addEventListener('click', async function () {
                var msgId = btn.dataset.msgId;
                var isLiked = btn.classList.contains('msg-liked');
                var url = '/api/message/' + msgId + '/like';
                var method = isLiked ? 'DELETE' : 'POST';

                try {
                    var likeRes = await FT.api(url, { method: method });
                    if (likeRes.code === 200) {
                        showMessageModal(page);
                    } else {
                        FT.toast(likeRes.message || '操作失败');
                    }
                } catch (e) {
                    FT.toast('网络错误，请重试');
                }
            });
        });

        // 绑定回复按钮（切换回复输入区域显示/隐藏）
        document.querySelectorAll('.msg-reply-btn').forEach(function (btn) {
            btn.addEventListener('click', function () {
                var parentId = btn.dataset.parentId;
                var replyArea = document.getElementById('msg-reply-input-' + parentId);
                if (replyArea) {
                    var isHidden = replyArea.style.display === 'none';
                    replyArea.style.display = isHidden ? 'block' : 'none';
                    if (isHidden) {
                        replyArea.querySelector('.msg-reply-textarea').focus();
                    }
                }
            });
        });

        // 绑定回复输入区域字数统计
        document.querySelectorAll('.msg-reply-textarea').forEach(function (ta) {
            ta.addEventListener('input', function () {
                var countSpan = ta.parentElement.querySelector('.msg-reply-char-count');
                if (countSpan) {
                    countSpan.textContent = ta.value.length + ' / 500';
                }
            });
        });

        // 绑定回复提交
        document.querySelectorAll('.msg-reply-submit-btn').forEach(function (btn) {
            btn.addEventListener('click', async function () {
                var parentId = btn.dataset.parentId;
                var replyArea = document.getElementById('msg-reply-input-' + parentId);
                var replyTextarea = replyArea.querySelector('.msg-reply-textarea');
                var content = replyTextarea.value.trim();
                if (!content) {
                    FT.toast('请输入回复内容', 'warning');
                    return;
                }
                btn.disabled = true;
                btn.textContent = '回复中...';
                var submitRes = await FT.api('/api/message', {
                    method: 'POST',
                    body: JSON.stringify({ content: content, parentId: parseInt(parentId, 10) })
                });
                if (submitRes.code === 200) {
                    FT.toast('回复成功');
                    showMessageModal(page);
                } else {
                    FT.toast(submitRes.message || '回复失败');
                    btn.disabled = false;
                    btn.textContent = '回复';
                }
            });
        });

        // 绑定回复删除
        document.querySelectorAll('.msg-reply-delete-btn').forEach(function (btn) {
            btn.addEventListener('click', async function () {
                var replyId = btn.dataset.replyId;
                if (!confirm('确定删除这条回复吗？')) return;
                var delRes = await FT.api('/api/message/' + replyId, { method: 'DELETE' });
                if (delRes.code === 200) {
                    FT.toast('已删除');
                    showMessageModal(page);
                } else {
                    FT.toast(delRes.message || '删除失败');
                }
            });
        });
    }

    FT.showMessageModal = showMessageModal;
})();
