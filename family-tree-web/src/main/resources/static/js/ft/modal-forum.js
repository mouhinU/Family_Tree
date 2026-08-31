/**
 * 族谱前端 - 家族论坛模块
 * 主题列表（分页）、富文本发帖、主题详情与回复。
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
(function () {
    'use strict';

    var FT = window.FT;

    var PAGE_SIZE = 10;

    async function showForumModal(page) {
        page = page || 1;
        var res = await FT.api('/api/forum?page=' + page + '&size=' + PAGE_SIZE);
        if (res.code !== 200) { FT.toast(res.message || '加载失败'); return; }
        var data = res.data || {};
        var topics = data.records || [];
        var totalPages = Math.max(data.totalPages || 1, 1);

        var html = '<h3 style="margin-bottom:10px;">家族论坛</h3>' +
            '<div class="forum-toolbar">' +
            '<button class="btn-confirm" id="forum-new-btn">发布新话题</button>' +
            '<span class="album-tip">共 ' + (data.total || 0) + ' 个话题</span>' +
            '</div>' +
            '<div class="forum-list" id="forum-list"></div>' +
            '<div class="forum-page">' + buildPageHtml(page, totalPages) + '</div>' +
            '<div class="modal-actions"><button class="btn-cancel" data-close-modal>关闭</button></div>';
        FT.showModal(html, true);

        var list = document.getElementById('forum-list');
        if (topics.length === 0) {
            list.innerHTML = '<p style="color:#999;text-align:center;padding:30px 0;">还没有话题，来发布第一个吧</p>';
        } else {
            var items = '';
            topics.forEach(function (t) {
                items += '<div class="forum-item js-forum-open" data-topic-id="' + t.id + '">' +
                    '<div class="forum-item-title">' + FT.escapeHtml(t.title) + '</div>' +
                    '<div class="forum-item-summary">' + FT.escapeHtml(t.summary || '') + '</div>' +
                    '<div class="forum-item-meta">' + FT.escapeHtml(t.username || '') +
                    ' · ' + formatTime(t.createTime) +
                    ' · 浏览 ' + (t.viewCount || 0) +
                    ' · 回复 ' + (t.replyCount || 0) +
                    (t.own ? ' · <span class="js-forum-del" data-topic-id="' + t.id + '" style="color:#a63a2b;">删除</span>' : '') +
                    '</div></div>';
            });
            list.innerHTML = items;

            list.querySelectorAll('.js-forum-open').forEach(function (el) {
                el.addEventListener('click', function (e) {
                    if (e.target.closest('.js-forum-del')) { return; }
                    showTopicModal(parseInt(el.dataset.topicId, 10));
                });
            });
            list.querySelectorAll('.js-forum-del').forEach(function (el) {
                el.addEventListener('click', function (e) {
                    e.stopPropagation();
                    var topicId = parseInt(el.dataset.topicId, 10);
                    FT.confirm('确定删除该话题及其全部回复吗？', async function () {
                        var delRes = await FT.api('/api/forum/' + topicId, {method: 'DELETE'});
                        if (delRes.code === 200) {
                            FT.toast('已删除', 'success');
                            showForumModal(page);
                        }
                    }, {title: '删除话题'});
                });
            });
        }

        document.getElementById('forum-new-btn').addEventListener('click', showPostModal);
        document.querySelectorAll('.forum-page-btn').forEach(function (btn) {
            btn.addEventListener('click', function () { showForumModal(parseInt(btn.dataset.page, 10)); });
        });
    }

    function buildPageHtml(page, totalPages) {
        var html = '<span>第 ' + page + ' / ' + totalPages + ' 页</span><span>';
        if (page > 1) {
            html += '<button class="btn-sm forum-page-btn" data-page="' + (page - 1) + '">上一页</button> ';
        }
        if (page < totalPages) {
            html += '<button class="btn-sm forum-page-btn" data-page="' + (page + 1) + '">下一页</button>';
        }
        return html + '</span>';
    }

    function formatTime(time) {
        return time ? String(time).replace('T', ' ').substring(0, 16) : '';
    }

    // ========== 发帖（富文本） ==========
    function showPostModal() {
        var html = '<h3 style="margin-bottom:10px;">发布新话题</h3>' +
            '<div class="form-group"><label>标题</label>' +
            '<input type="text" id="forum-post-title" maxlength="100" placeholder="话题标题（100字以内）"></div>' +
            '<div class="form-group"><label>内容</label>' +
            '<div class="forum-editor-bar">' +
            '<button type="button" data-cmd="bold" title="加粗"><b>B</b></button>' +
            '<button type="button" data-cmd="italic" title="斜体"><i>I</i></button>' +
            '<button type="button" data-cmd="underline" title="下划线"><u>U</u></button>' +
            '<button type="button" id="forum-post-img" title="插入图片">图片</button>' +
            '<input type="file" id="forum-img-input" accept="image/jpeg,image/png,image/gif,image/webp" style="display:none;">' +
            '</div>' +
            '<div class="forum-editor" id="forum-post-content" contenteditable="true"></div>' +
            '</div>' +
            '<div class="modal-actions">' +
            '<button class="btn-cancel" data-close-modal>取消</button>' +
            '<button class="btn-confirm" id="forum-post-save">发布</button>' +
            '</div>';
        FT.showModal(html, true);
        wireEditor();

        document.getElementById('forum-post-save').addEventListener('click', async function () {
            var title = document.getElementById('forum-post-title').value.trim();
            var content = document.getElementById('forum-post-content').innerHTML.trim();
            if (!title) { FT.toast('请填写标题', 'warning'); return; }
            if (!content || content === '<br>') { FT.toast('请填写内容', 'warning'); return; }
            var res = await FT.api('/api/forum', {
                method: 'POST',
                body: JSON.stringify({title: title, content: content})
            });
            if (res.code === 200) {
                FT.toast('发布成功', 'success');
                showForumModal(1);
            }
        });
    }

    // 富文本工具栏：加粗/斜体/下划线/插图
    function wireEditor() {
        document.querySelectorAll('.forum-editor-bar [data-cmd]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                document.execCommand(btn.dataset.cmd, false, null);
                document.getElementById('forum-post-content').focus();
            });
        });
        var imgBtn = document.getElementById('forum-post-img');
        var imgInput = document.getElementById('forum-img-input');
        if (imgBtn && imgInput) {
            imgBtn.addEventListener('click', function () { imgInput.click(); });
            imgInput.addEventListener('change', async function () {
                if (!imgInput.files || !imgInput.files[0]) { return; }
                var formData = new FormData();
                formData.append('file', imgInput.files[0]);
                try {
                    var upRes = await FT.uploadFile('/api/forum/upload-image', formData);
                    if (upRes.code === 200 && upRes.data && upRes.data.url) {
                        document.getElementById('forum-post-content').focus();
                        document.execCommand('insertImage', false, upRes.data.url);
                    }
                } catch (e) {
                    // uploadFile 内部已提示错误
                }
                imgInput.value = '';
            });
        }
    }

    // ========== 主题详情与回复 ==========
    async function showTopicModal(topicId) {
        var res = await FT.api('/api/forum/' + topicId);
        if (res.code !== 200) { FT.toast(res.message || '加载失败'); return; }
        var topic = res.data;

        // 富文本内容已由服务端白名单清洗，可直接渲染
        var html = '<h3 style="margin-bottom:6px;">' + FT.escapeHtml(topic.title) + '</h3>' +
            '<div class="forum-item-meta">' + FT.escapeHtml(topic.username || '') +
            ' · ' + formatTime(topic.createTime) +
            ' · 浏览 ' + (topic.viewCount || 0) + '</div>' +
            '<div class="forum-content">' + (topic.content || '') + '</div>' +
            '<div class="forum-replies" id="forum-replies"></div>' +
            '<div class="forum-reply-box">' +
            '<textarea id="forum-reply-input" maxlength="500" placeholder="写下你的回复（500字以内）"></textarea>' +
            '<button class="btn-confirm" id="forum-reply-send">回复</button>' +
            '</div>' +
            '<div class="modal-actions"><button class="btn-cancel" data-close-modal>关闭</button></div>';
        FT.showModal(html, true);
        renderReplies(topic);

        document.getElementById('forum-reply-send').addEventListener('click', async function () {
            var input = document.getElementById('forum-reply-input');
            var content = input.value.trim();
            if (!content) { FT.toast('请输入回复内容', 'warning'); return; }
            var replyRes = await FT.api('/api/forum/' + topicId + '/reply', {
                method: 'POST',
                body: JSON.stringify({content: content})
            });
            if (replyRes.code === 200) {
                input.value = '';
                FT.toast('回复成功', 'success');
                showTopicModal(topicId);
            }
        });
    }

    function renderReplies(topic) {
        var box = document.getElementById('forum-replies');
        if (!box) { return; }
        var replies = topic.replies || [];
        if (replies.length === 0) {
            box.innerHTML = '<p style="color:#999;text-align:center;padding:10px 0;">暂无回复</p>';
            return;
        }
        var html = '<h4 style="margin:10px 0 6px;">回复（' + replies.length + '）</h4>';
        replies.forEach(function (r) {
            html += '<div class="forum-reply-item">' +
                '<div class="forum-item-meta">' + FT.escapeHtml(r.username || '') + ' · ' + formatTime(r.createTime) +
                (r.own ? ' · <span class="js-reply-del" data-reply-id="' + r.id + '" style="color:#a63a2b;">删除</span>' : '') +
                '</div>' +
                '<div class="forum-reply-content">' + FT.escapeHtml(r.content) + '</div>' +
                '</div>';
        });
        box.innerHTML = html;

        box.querySelectorAll('.js-reply-del').forEach(function (el) {
            el.addEventListener('click', function () {
                var replyId = parseInt(el.dataset.replyId, 10);
                FT.confirm('确定删除这条回复吗？', async function () {
                    var delRes = await FT.api('/api/forum/reply/' + replyId, {method: 'DELETE'});
                    if (delRes.code === 200) {
                        FT.toast('已删除', 'success');
                        showTopicModal(topic.id);
                    }
                }, {title: '删除回复'});
            });
        });
    }

    FT.showForumModal = showForumModal;
})();
