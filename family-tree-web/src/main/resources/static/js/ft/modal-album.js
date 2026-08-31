/**
 * 族谱前端 - 家族相册模块
 * 照片网格、上传、人物标记与大图预览。
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
(function () {
    'use strict';

    var FT = window.FT;

    // 照片列表缓存（标记人物弹窗复用）
    var allNodes = [];

    async function showAlbumModal() {
        var res = await FT.api('/api/photo');
        if (res.code !== 200) { FT.toast(res.message || '加载失败'); return; }
        var photos = res.data || [];

        var html = '<h3 style="margin-bottom:10px;">家族相册</h3>' +
            '<div class="album-toolbar">' +
            '<button class="btn-confirm" id="album-upload-btn">上传照片</button>' +
            '<input type="file" id="album-file-input" accept="image/jpeg,image/png,image/gif,image/webp" style="display:none;">' +
            '<span class="album-tip">共 ' + photos.length + ' 张，照片全家族共享</span>' +
            '</div>' +
            '<div class="album-grid" id="album-grid"></div>' +
            '<div class="modal-actions"><button class="btn-cancel" data-close-modal>关闭</button></div>';
        FT.showModal(html, true);
        renderGrid(photos);

        var fileInput = document.getElementById('album-file-input');
        document.getElementById('album-upload-btn').addEventListener('click', function () {
            fileInput.click();
        });
        fileInput.addEventListener('change', async function () {
            if (!fileInput.files || !fileInput.files[0]) { return; }
            var formData = new FormData();
            formData.append('file', fileInput.files[0]);
            try {
                var upRes = await FT.uploadFile('/api/photo/upload', formData);
                if (upRes.code === 200) {
                    FT.toast('上传成功', 'success');
                    showAlbumModal();
                }
            } catch (e) {
                // uploadFile 内部已提示错误
            }
        });
    }

    function renderGrid(photos) {
        var grid = document.getElementById('album-grid');
        if (!grid) { return; }
        if (photos.length === 0) {
            grid.innerHTML = '<p style="color:#999;text-align:center;padding:30px 0;grid-column:1/-1;">还没有照片，快来上传第一张吧</p>';
            return;
        }
        var html = '';
        photos.forEach(function (p) {
            var tags = (p.tags || []).map(function (t) {
                return '<span class="album-tag">' + FT.escapeHtml(t.nodeName) + '</span>';
            }).join('');
            html += '<div class="album-card">' +
                '<img class="album-img js-album-view" src="' + FT.escapeAttr(p.photoUrl) + '" alt="' + FT.escapeAttr(p.title || '照片') + '" loading="lazy">' +
                '<div class="album-info">' +
                '<div class="album-title">' + FT.escapeHtml(p.title || '未命名照片') + '</div>' +
                '<div class="album-meta">' + FT.escapeHtml(p.username || '') + ' 上传</div>' +
                (tags ? '<div class="album-tags">' + tags + '</div>' : '') +
                '</div>' +
                '<div class="album-actions">' +
                '<button class="btn-sm js-album-tag" data-photo-id="' + p.id + '">标记人物</button>' +
                (p.own ? '<button class="btn-sm danger js-album-del" data-photo-id="' + p.id + '">删除</button>' : '') +
                '</div>' +
                '</div>';
        });
        grid.innerHTML = html;

        grid.querySelectorAll('.js-album-view').forEach(function (img) {
            img.addEventListener('click', function () { showViewer(img.src); });
        });
        grid.querySelectorAll('.js-album-tag').forEach(function (btn) {
            btn.addEventListener('click', function () { showTagModal(parseInt(btn.dataset.photoId, 10)); });
        });
        grid.querySelectorAll('.js-album-del').forEach(function (btn) {
            btn.addEventListener('click', function () {
                var photoId = parseInt(btn.dataset.photoId, 10);
                FT.confirm('确定删除这张照片吗？删除后不可恢复。', async function () {
                    var res = await FT.api('/api/photo/' + photoId, {method: 'DELETE'});
                    if (res.code === 200) {
                        FT.toast('已删除', 'success');
                        showAlbumModal();
                    }
                }, {title: '删除照片'});
            });
        });
    }

    // 大图预览浮层
    function showViewer(src) {
        var overlay = document.createElement('div');
        overlay.className = 'album-viewer';
        overlay.innerHTML = '<img src="' + FT.escapeAttr(src) + '" alt="照片预览">' +
            '<button class="album-viewer-close" title="关闭">&times;</button>';
        document.body.appendChild(overlay);
        overlay.addEventListener('click', function () { overlay.remove(); });
    }

    // 标记人物弹窗：从家族节点中选择
    async function showTagModal(photoId) {
        if (!allNodes.length) {
            var listRes = await FT.api('/api/node/list');
            if (listRes.code === 200) { allNodes = listRes.data || []; }
        }
        var options = '<option value="">请选择人物</option>';
        allNodes.forEach(function (n) {
            var gen = n.generation != null ? '（第' + n.generation + '世）' : '';
            options += '<option value="' + n.id + '">' + FT.escapeHtml(n.name) + gen + '</option>';
        });

        var html = '<h3 style="margin-bottom:10px;">标记照片中的人物</h3>' +
            '<div class="form-group"><label>选择人物</label><select id="album-tag-select">' + options + '</select></div>' +
            '<div class="modal-actions">' +
            '<button class="btn-cancel" data-close-modal>取消</button>' +
            '<button class="btn-confirm" id="album-tag-save">保存</button>' +
            '</div>';
        FT.showModal(html);

        document.getElementById('album-tag-save').addEventListener('click', async function () {
            var nodeId = document.getElementById('album-tag-select').value;
            if (!nodeId) { FT.toast('请选择人物', 'warning'); return; }
            var res = await FT.api('/api/photo/' + photoId + '/tags', {
                method: 'POST',
                body: JSON.stringify({nodeId: parseInt(nodeId, 10)})
            });
            if (res.code === 200) {
                FT.toast('标记成功', 'success');
                showAlbumModal();
            }
        });
    }

    FT.showAlbumModal = showAlbumModal;
})();
