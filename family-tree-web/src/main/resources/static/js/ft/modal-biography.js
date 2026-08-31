/**
 * 族谱前端 - 人物传记模块
 * 富文本撰写生平故事（图文混排），内容经服务端白名单清洗后存储。
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
(function () {
    'use strict';

    var FT = window.FT;

    async function showBiographyModal(nodeId) {
        var node = FT.findNodeById(FT.state.treeData, nodeId);
        var nodeName = node ? node.name : '人物';

        var res = await FT.api('/api/biography/' + nodeId);
        if (res.code !== 200) { FT.toast(res.message || '加载失败'); return; }
        var biography = (res.data && res.data.biography) || '';

        var html = '<h3 style="margin-bottom:10px;">' + FT.escapeHtml(nodeName) + ' 的传记</h3>' +
            '<div class="forum-editor-bar">' +
            '<button type="button" data-cmd="bold" title="加粗"><b>B</b></button>' +
            '<button type="button" data-cmd="italic" title="斜体"><i>I</i></button>' +
            '<button type="button" data-cmd="underline" title="下划线"><u>U</u></button>' +
            '<button type="button" id="bio-img-btn" title="插入图片">图片</button>' +
            '<input type="file" id="bio-img-input" accept="image/jpeg,image/png,image/gif,image/webp" style="display:none;">' +
            '</div>' +
            '<div class="forum-editor bio-editor" id="bio-editor" contenteditable="true">' + biography + '</div>' +
            '<p style="font-size:12px;color:#999;margin-top:6px;">支持图文混排，保存时服务端会进行安全清洗。</p>' +
            '<div class="modal-actions">' +
            '<button class="btn-cancel" data-close-modal>关闭</button>' +
            '<button class="btn-confirm" id="bio-save-btn">保存传记</button>' +
            '</div>';
        FT.showModal(html, true);

        document.querySelectorAll('.forum-editor-bar [data-cmd]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                document.execCommand(btn.dataset.cmd, false, null);
                document.getElementById('bio-editor').focus();
            });
        });

        var imgInput = document.getElementById('bio-img-input');
        document.getElementById('bio-img-btn').addEventListener('click', function () { imgInput.click(); });
        imgInput.addEventListener('change', async function () {
            if (!imgInput.files || !imgInput.files[0]) { return; }
            var formData = new FormData();
            formData.append('file', imgInput.files[0]);
            try {
                var upRes = await FT.uploadFile('/api/forum/upload-image', formData);
                if (upRes.code === 200 && upRes.data && upRes.data.url) {
                    document.getElementById('bio-editor').focus();
                    document.execCommand('insertImage', false, upRes.data.url);
                }
            } catch (e) {
                // uploadFile 内部已提示错误
            }
            imgInput.value = '';
        });

        document.getElementById('bio-save-btn').addEventListener('click', async function () {
            var content = document.getElementById('bio-editor').innerHTML.trim();
            var saveRes = await FT.api('/api/biography/' + nodeId, {
                method: 'PUT',
                body: JSON.stringify({biography: content === '<br>' ? '' : content})
            });
            if (saveRes.code === 200) {
                FT.toast('传记已保存', 'success');
            }
        });
    }

    FT.showBiographyModal = showBiographyModal;
})();
