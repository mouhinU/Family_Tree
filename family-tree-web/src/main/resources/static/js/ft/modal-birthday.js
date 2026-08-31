/**
 * 族谱前端 - 生日提醒模块
 * 未来 N 天内过生日的在世成员列表。
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
(function () {
    'use strict';

    var FT = window.FT;

    async function showBirthdayModal() {
        var res = await FT.api('/api/birthday?days=30');
        if (res.code !== 200) { FT.toast(res.message || '加载失败'); return; }
        var list = res.data || [];

        var html = '<h3 style="margin-bottom:10px;">生日提醒 <small style="color:#999;font-weight:normal;">未来30天</small></h3>';
        if (list.length === 0) {
            html += '<p style="color:#999;padding:20px 0;text-align:center;">未来30天内没有过生日的成员</p>';
        } else {
            html += '<div style="max-height:300px;overflow-y:auto;">';
            list.forEach(function (item) {
                var urgency = item.daysUntil <= 3 ? 'color:#a63a2b;font-weight:600;' : '';
                var dayText = item.daysUntil === 0 ? '今天生日 🎂' : (item.daysUntil + '天后');
                html += '<div style="display:flex;align-items:center;gap:10px;padding:8px 0;border-bottom:1px solid #f0f0f0;">' +
                    '<span style="' + urgency + '">' + FT.escapeHtml(item.name) + '</span>' +
                    '<span style="font-size:12px;color:#999;">生日：' + FT.escapeHtml(item.birthDate || '') +
                    (item.age != null ? '（将满 ' + item.age + ' 岁）' : '') + '</span>' +
                    '<span style="font-size:12px;' + urgency + '">' + dayText + '</span>' +
                    '</div>';
            });
            html += '</div>';
        }
        html += '<div class="modal-actions"><button class="btn-cancel" data-close-modal>关闭</button></div>';
        FT.showModal(html);
    }

    FT.showBirthdayModal = showBirthdayModal;
})();
