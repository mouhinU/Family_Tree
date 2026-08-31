/**
 * 族谱前端 - 活动组织模块
 * 活动列表、发起活动、报名/取消、费用AA与截止管理。
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
(function () {
    'use strict';

    var FT = window.FT;

    async function showEventModal() {
        var res = await FT.api('/api/event');
        if (res.code !== 200) { FT.toast(res.message || '加载失败'); return; }
        var events = res.data || [];

        var html = '<h3 style="margin-bottom:10px;">家族活动</h3>' +
            '<div class="forum-toolbar">' +
            '<button class="btn-confirm" id="event-new-btn">发起活动</button>' +
            '<span class="album-tip">共 ' + events.length + ' 个活动</span>' +
            '</div>' +
            '<div class="event-list" id="event-list"></div>' +
            '<div class="modal-actions"><button class="btn-cancel" data-close-modal>关闭</button></div>';
        FT.showModal(html, true);

        var list = document.getElementById('event-list');
        if (events.length === 0) {
            list.innerHTML = '<p style="color:#999;text-align:center;padding:30px 0;">暂无活动，快来发起一场家族聚会吧</p>';
        } else {
            var items = '';
            events.forEach(function (ev) {
                var statusClass = ev.status === 'OPEN' ? 'event-status--open' : 'event-status--closed';
                items += '<div class="event-item js-event-open" data-event-id="' + ev.id + '">' +
                    '<div class="event-item-head">' +
                    '<span class="event-item-title">' + FT.escapeHtml(ev.title) + '</span>' +
                    '<span class="event-status ' + statusClass + '">' + FT.escapeHtml(ev.statusDesc) + '</span>' +
                    '</div>' +
                    '<div class="event-item-meta">时间：' + formatTime(ev.eventTime) +
                    (ev.location ? ' · 地点：' + FT.escapeHtml(ev.location) : '') + '</div>' +
                    '<div class="event-item-meta">已报名 ' + (ev.totalAttendees || 0) + ' 人' +
                    (ev.perPersonCost != null ? ' · 人均 ¥' + ev.perPersonCost : '') +
                    (ev.signedUp ? ' · <span style="color:#3a6b4f;">我已报名</span>' : '') +
                    '</div>' +
                    '</div>';
            });
            list.innerHTML = items;
            list.querySelectorAll('.js-event-open').forEach(function (el) {
                el.addEventListener('click', function () {
                    showEventDetail(parseInt(el.dataset.eventId, 10));
                });
            });
        }

        document.getElementById('event-new-btn').addEventListener('click', showCreateModal);
    }

    function formatTime(time) {
        return time ? String(time).replace('T', ' ').substring(0, 16) : '';
    }

    // ========== 发起活动 ==========
    function showCreateModal() {
        var html = '<h3 style="margin-bottom:10px;">发起活动</h3>' +
            '<div class="form-group"><label>活动标题</label>' +
            '<input type="text" id="event-title" maxlength="100" placeholder="如：中秋家族聚会"></div>' +
            '<div class="form-group"><label>活动时间</label>' +
            '<input type="datetime-local" id="event-time"></div>' +
            '<div class="form-group"><label>活动地点</label>' +
            '<input type="text" id="event-location" maxlength="200" placeholder="如：老家祠堂"></div>' +
            '<div class="form-group"><label>活动总费用（元，用于AA，可不填）</label>' +
            '<input type="number" id="event-cost" min="0" step="0.01" placeholder="0.00"></div>' +
            '<div class="form-group"><label>活动说明</label>' +
            '<textarea id="event-desc" maxlength="1000" rows="3" placeholder="活动安排、注意事项等"></textarea></div>' +
            '<div class="modal-actions">' +
            '<button class="btn-cancel" data-close-modal>取消</button>' +
            '<button class="btn-confirm" id="event-save-btn">发起</button>' +
            '</div>';
        FT.showModal(html);

        document.getElementById('event-save-btn').addEventListener('click', async function () {
            var title = document.getElementById('event-title').value.trim();
            var eventTime = document.getElementById('event-time').value;
            var location = document.getElementById('event-location').value.trim();
            var costStr = document.getElementById('event-cost').value;
            var description = document.getElementById('event-desc').value.trim();
            if (!title) { FT.toast('请填写活动标题', 'warning'); return; }
            if (!eventTime) { FT.toast('请选择活动时间', 'warning'); return; }
            var payload = {
                title: title,
                eventTime: eventTime.replace('T', ' '),
                location: location,
                description: description,
                totalCost: costStr === '' ? null : parseFloat(costStr)
            };
            var res = await FT.api('/api/event', {method: 'POST', body: JSON.stringify(payload)});
            if (res.code === 200) {
                FT.toast('活动已发起', 'success');
                showEventModal();
            }
        });
    }

    // ========== 活动详情 ==========
    async function showEventDetail(eventId) {
        var res = await FT.api('/api/event/' + eventId);
        if (res.code !== 200) { FT.toast(res.message || '加载失败'); return; }
        var ev = res.data;
        var signups = ev.signups || [];

        var signupRows = '';
        signups.forEach(function (s) {
            signupRows += '<div class="event-signup-row">' +
                '<span>' + FT.escapeHtml(s.username) + '（' + (s.attendeeCount || 1) + '人）</span>' +
                '<span style="color:#999;font-size:12px;">' + FT.escapeHtml(s.remark || '') + '</span>' +
                '</div>';
        });

        var ownerActions = '';
        if (ev.own) {
            ownerActions = ev.status === 'OPEN'
                ? '<button class="btn-sm" id="event-close-btn">截止报名</button>'
                : '<button class="btn-sm" id="event-open-btn">重新开放</button>';
            ownerActions += ' <button class="btn-sm danger" id="event-del-btn">删除活动</button>';
        }

        var html = '<h3 style="margin-bottom:6px;">' + FT.escapeHtml(ev.title) +
            ' <small style="font-weight:normal;color:' + (ev.status === 'OPEN' ? '#3a6b4f' : '#999') + ';">' + FT.escapeHtml(ev.statusDesc) + '</small></h3>' +
            '<div class="event-item-meta">发起人：' + FT.escapeHtml(ev.username || '') +
            ' · 时间：' + formatTime(ev.eventTime) +
            (ev.location ? ' · 地点：' + FT.escapeHtml(ev.location) : '') + '</div>' +
            (ev.description ? '<p style="font-size:13px;color:#555;margin:6px 0;">' + FT.escapeHtml(ev.description) + '</p>' : '') +
            '<div class="event-cost-box">' +
            (ev.totalCost != null && ev.totalCost > 0
                ? '总费用 ¥' + ev.totalCost + '，已报名 ' + (ev.totalAttendees || 0) + ' 人' +
                (ev.perPersonCost != null ? '，<b>人均 ¥' + ev.perPersonCost + '</b>' : '')
                : '本活动未设置费用') +
            '</div>' +
            '<h4 style="margin:10px 0 6px;">报名名单（' + (ev.totalAttendees || 0) + '人）</h4>' +
            '<div class="event-signups">' + (signupRows || '<p style="color:#999;">暂无人报名</p>') + '</div>' +
            '<div class="modal-actions" style="flex-wrap:wrap;">' + ownerActions +
            (ev.signedUp
                ? '<button class="btn-cancel" id="event-cancel-btn">取消报名</button>'
                : (ev.status === 'OPEN' ? '<button class="btn-confirm" id="event-signup-btn">我要报名</button>' : '')) +
            '<button class="btn-cancel" id="event-back-btn">返回列表</button>' +
            '</div>';
        FT.showModal(html, true);

        var signupBtn = document.getElementById('event-signup-btn');
        if (signupBtn) {
            signupBtn.addEventListener('click', function () { showSignupModal(eventId); });
        }
        var cancelBtn = document.getElementById('event-cancel-btn');
        if (cancelBtn) {
            cancelBtn.addEventListener('click', function () {
                FT.confirm('确定取消报名吗？', async function () {
                    var r = await FT.api('/api/event/' + eventId + '/signup', {method: 'DELETE'});
                    if (r.code === 200) { FT.toast('已取消报名', 'success'); showEventDetail(eventId); }
                }, {title: '取消报名'});
            });
        }
        var closeBtn = document.getElementById('event-close-btn');
        if (closeBtn) {
            closeBtn.addEventListener('click', async function () {
                var r = await FT.api('/api/event/' + eventId + '/close', {method: 'POST'});
                if (r.code === 200) { FT.toast('报名已截止', 'success'); showEventDetail(eventId); }
            });
        }
        var openBtn = document.getElementById('event-open-btn');
        if (openBtn) {
            openBtn.addEventListener('click', async function () {
                var r = await FT.api('/api/event/' + eventId + '/open', {method: 'POST'});
                if (r.code === 200) { FT.toast('已重新开放报名', 'success'); showEventDetail(eventId); }
            });
        }
        var delBtn = document.getElementById('event-del-btn');
        if (delBtn) {
            delBtn.addEventListener('click', function () {
                FT.confirm('确定删除该活动吗？报名记录将一并删除。', async function () {
                    var r = await FT.api('/api/event/' + eventId, {method: 'DELETE'});
                    if (r.code === 200) { FT.toast('活动已删除', 'success'); showEventModal(); }
                }, {title: '删除活动'});
            });
        }
        document.getElementById('event-back-btn').addEventListener('click', showEventModal);
    }

    // 报名弹窗：人数 + 备注
    function showSignupModal(eventId) {
        var html = '<h3 style="margin-bottom:10px;">活动报名</h3>' +
            '<div class="form-group"><label>参加人数（含本人）</label>' +
            '<input type="number" id="signup-count" min="1" max="20" value="1"></div>' +
            '<div class="form-group"><label>备注（可不填）</label>' +
            '<input type="text" id="signup-remark" maxlength="200" placeholder="如：开车可捎带2人"></div>' +
            '<div class="modal-actions">' +
            '<button class="btn-cancel" data-close-modal>取消</button>' +
            '<button class="btn-confirm" id="signup-save-btn">确认报名</button>' +
            '</div>';
        FT.showModal(html);

        document.getElementById('signup-save-btn').addEventListener('click', async function () {
            var count = parseInt(document.getElementById('signup-count').value, 10);
            var remark = document.getElementById('signup-remark').value.trim();
            if (!count || count < 1) { FT.toast('参加人数至少为1人', 'warning'); return; }
            var res = await FT.api('/api/event/' + eventId + '/signup', {
                method: 'POST',
                body: JSON.stringify({attendeeCount: count, remark: remark})
            });
            if (res.code === 200) {
                FT.toast('报名成功', 'success');
                showEventDetail(eventId);
            }
        });
    }

    FT.showEventModal = showEventModal;
})();
