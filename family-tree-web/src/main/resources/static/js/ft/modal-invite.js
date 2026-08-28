/**
 * 族谱前端 - 邀请模块
 * 展示邀请链接和二维码。
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
(function () {
    'use strict';

    var FT = window.FT;

    /**
     * 显示邀请弹框，加载邀请链接和二维码
     */
    async function showInviteModal() {
        FT.showModal(
            '<h3 style="text-align:center;margin-bottom:16px;">邀请族人加入</h3>'
            + '<div id="invite-content" style="text-align:center;min-height:200px;">'
            + '<p style="color:#8a7f6a;">加载中...</p>'
            + '</div>'
            + '<div style="text-align:right;margin-top:12px;padding-top:8px;border-top:1px solid #d8cba8;">'
            + '<button class="btn-sm" data-close-modal>关闭</button>'
            + '</div>',
            'modal-medium'
        );

        await loadInviteInfo();
    }

    /**
     * 加载邀请信息并渲染到弹框中
     */
    async function loadInviteInfo() {
        var contentEl = document.getElementById('invite-content');
        if (!contentEl) {
            return;
        }

        try {
            var res = await FT.api('/api/invite/link');
            if (res.code === 200 && res.data) {
                var inviteUrl = res.data.inviteUrl || '';
                var inviteCode = res.data.inviteCode || '';

                contentEl.innerHTML = ''
                    + '<div style="margin-bottom:16px;">'
                    + '<img src="/api/invite/qrcode" alt="邀请二维码" style="width:200px;height:200px;border:1px solid #d8cba8;border-radius:8px;">'
                    + '</div>'
                    + '<p style="font-size:13px;color:#6b6156;margin-bottom:12px;">扫描二维码或复制邀请链接分享给族人</p>'
                    + '<div style="display:flex;gap:8px;align-items:center;">'
                    + '<input type="text" id="invite-link-input" value="' + FT.escapeHtml(inviteUrl) + '" readonly '
                    + 'style="flex:1;padding:8px 12px;border:1px solid #d8cba8;border-radius:4px;font-size:13px;background:#fdfaf0;color:#2b2622;">'
                    + '<button class="btn-sm primary" id="btn-copy-invite">复制链接</button>'
                    + '</div>'
                    + '<p style="font-size:11px;color:#a09888;margin-top:8px;">邀请码: ' + FT.escapeHtml(inviteCode) + '</p>';

                // 绑定复制按钮事件
                var copyBtn = document.getElementById('btn-copy-invite');
                if (copyBtn) {
                    copyBtn.addEventListener('click', function () {
                        var input = document.getElementById('invite-link-input');
                        if (input) {
                            input.select();
                            navigator.clipboard.writeText(input.value).then(function () {
                                FT.toast('邀请链接已复制');
                            }).catch(function () {
                                // 降级方案
                                document.execCommand('copy');
                                FT.toast('邀请链接已复制');
                            });
                        }
                    });
                }
            } else {
                contentEl.innerHTML = '<p style="color:#a63a2b;">获取邀请信息失败</p>';
            }
        } catch (e) {
            contentEl.innerHTML = '<p style="color:#a63a2b;">请求失败</p>';
        }
    }

    FT.showInviteModal = showInviteModal;
})();
