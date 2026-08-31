/**
 * 族谱前端 - 纪念日管理模块
 * 自定义纪念日（结婚周年、入学等）的增删改查与倒计时。
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
(function () {
    'use strict';

    var FT = window.FT;

    async function showAnniversaryModal() {
        var res = await FT.api('/api/anniversary');
        if (res.code !== 200) { FT.toast(res.message || '加载失败'); return; }
        var list = res.data || [];

        var html = '<h3 style="margin-bottom:10px;">纪念日管理</h3>' +
            '<div class="forum-toolbar">' +
            '<button class="btn-confirm" id="anni-new-btn">添加纪念日</button>' +
            '<span class="album-tip">共 ' + list.length + ' 个，按临近时间排序</span>' +
            '</div>' +
            '<div class="anni-list" id="anni-list"></div>' +
            '<div class="modal-actions"><button class="btn-cancel" data-close-modal>关闭</button></div>';
        FT.showModal(html, true);

        var listEl = document.getElementById('anni-list');
        if (list.length === 0) {
            listEl.innerHTML = '<p style="color:#999;text-align:center;padding:30px 0;">暂无纪念日，来添加一个吧（结婚周年、入学、乔迁…）</p>';
        } else {
            var items = '';
            list.forEach(function (a) {
                var urgency = a.daysUntil != null && a.daysUntil <= 7 ? 'color:#a63a2b;font-weight:600;' : '';
                var dayText = a.daysUntil === 0 ? '就是今天' : (a.daysUntil + ' 天后');
                items += '<div class="anni-item">' +
                    '<div class="anni-item-main">' +
                    '<div class="anni-item-title">' + FT.escapeHtml(a.title) +
                    ' <span class="album-tag">' + FT.escapeHtml(a.categoryDesc || '') + '</span></div>' +
                    '<div class="album-meta">' +
                    (a.nodeName ? FT.escapeHtml(a.nodeName) + ' · ' : '') +
                    '首次：' + FT.escapeHtml(a.anniversaryDate || '') +
                    (a.years > 0 ? ' · 已 ' + a.years + ' 周年' : '') +
                    (a.remark ? ' · ' + FT.escapeHtml(a.remark) : '') +
                    '</div></div>' +
                    '<div class="anni-item-side">' +
                    '<span style="' + urgency + 'font-size:13px;">' + dayText + '</span>' +
                    (a.own ? '<div>' +
                        '<button class="btn-sm js-anni-edit" data-anni-id="' + a.id + '">编辑</button> ' +
                        '<button class="btn-sm danger js-anni-del" data-anni-id="' + a.id + '">删除</button>' +
                        '</div>' : '') +
                    '</div></div>';
            });
            listEl.innerHTML = items;

            listEl.querySelectorAll('.js-anni-edit').forEach(function (btn) {
                btn.addEventListener('click', function () {
                    var target = list.find(function (a) { return a.id === parseInt(btn.dataset.anniId, 10); });
                    showFormModal(target);
                });
            });
            listEl.querySelectorAll('.js-anni-del').forEach(function (btn) {
                btn.addEventListener('click', function () {
                    var id = parseInt(btn.dataset.anniId, 10);
                    FT.confirm('确定删除这个纪念日吗？', async function () {
                        var r = await FT.api('/api/anniversary/' + id, {method: 'DELETE'});
                        if (r.code === 200) { FT.toast('已删除', 'success'); showAnniversaryModal(); }
                    }, {title: '删除纪念日'});
                });
            });
        }

        document.getElementById('anni-new-btn').addEventListener('click', function () {
            showFormModal(null);
        });
    }

    // 新增/编辑表单
    async function showFormModal(existing) {
        var nodeOptions = '<option value="">不关联成员</option>';
        var listRes = await FT.api('/api/node/list');
        if (listRes.code === 200) {
            (listRes.data || []).forEach(function (n) {
                var sel = existing && existing.nodeId === n.id ? ' selected' : '';
                nodeOptions += '<option value="' + n.id + '"' + sel + '>' + FT.escapeHtml(n.name) + '</option>';
            });
        }

        var categoryOptions = [
            {code: 'wedding', label: '结婚周年'},
            {code: 'school', label: '入学/毕业'},
            {code: 'memorial', label: '纪念'},
            {code: 'other', label: '其他'}
        ].map(function (c) {
            var sel = existing && existing.category === c.code ? ' selected' : '';
            return '<option value="' + c.code + '"' + sel + '>' + c.label + '</option>';
        }).join('');

        var html = '<h3 style="margin-bottom:10px;">' + (existing ? '编辑纪念日' : '添加纪念日') + '</h3>' +
            '<div class="form-group"><label>纪念日标题</label>' +
            '<input type="text" id="anni-title" maxlength="100" value="' + FT.escapeAttr(existing ? existing.title : '') + '" placeholder="如：金婚纪念"></div>' +
            '<div class="form-group"><label>分类</label>' +
            '<select id="anni-category">' + categoryOptions + '</select></div>' +
            '<div class="form-group"><label>首次日期（用于计算周年）</label>' +
            '<input type="date" id="anni-date" value="' + FT.escapeAttr(existing ? (existing.anniversaryDate || '') : '') + '"></div>' +
            '<div class="form-group"><label>关联成员（可不选）</label>' +
            '<select id="anni-node">' + nodeOptions + '</select></div>' +
            '<div class="form-group"><label>备注（可不填）</label>' +
            '<textarea id="anni-remark" maxlength="500" rows="2">' + FT.escapeHtml(existing ? (existing.remark || '') : '') + '</textarea></div>' +
            '<div class="modal-actions">' +
            '<button class="btn-cancel" id="anni-back-btn">返回列表</button>' +
            '<button class="btn-confirm" id="anni-save-btn">保存</button>' +
            '</div>';
        FT.showModal(html);

        document.getElementById('anni-back-btn').addEventListener('click', showAnniversaryModal);
        document.getElementById('anni-save-btn').addEventListener('click', async function () {
            var payload = {
                title: document.getElementById('anni-title').value.trim(),
                category: document.getElementById('anni-category').value,
                anniversaryDate: document.getElementById('anni-date').value,
                nodeId: document.getElementById('anni-node').value
                    ? parseInt(document.getElementById('anni-node').value, 10) : null,
                remark: document.getElementById('anni-remark').value.trim()
            };
            if (!payload.title) { FT.toast('请填写标题', 'warning'); return; }
            if (!payload.anniversaryDate) { FT.toast('请选择日期', 'warning'); return; }
            var res;
            if (existing) {
                res = await FT.api('/api/anniversary/' + existing.id, {method: 'PUT', body: JSON.stringify(payload)});
            } else {
                res = await FT.api('/api/anniversary', {method: 'POST', body: JSON.stringify(payload)});
            }
            if (res.code === 200) {
                FT.toast('保存成功', 'success');
                showAnniversaryModal();
            }
        });
    }

    FT.showAnniversaryModal = showAnniversaryModal;
})();
