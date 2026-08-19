/**
 * 族谱前端 - 工具弹窗模块
 * 操作日志、时间线、关系路径分析、忌日提醒弹窗。
 *
 * @author Family-Tree
 * @date 2026-08-09
 */
(function () {
    'use strict';

    var FT = window.FT;

    // ========== 操作日志 ==========
    // 操作日志查看弹窗：分页展示家族操作记录，支持按类型筛选
    var OPERATION_TYPE_MAP = {
        'LOGIN': '登录',
        'LOGIN_FAIL': '登录失败',
        'LOGOUT': '登出',
        'REGISTER': '注册',
        'PROFILE_UPDATE': '修改个人信息',
        'MARK_SELF': '标记自己',
        'NODE_CREATE': '创建节点',
        'NODE_UPDATE': '更新节点',
        'NODE_DELETE': '删除节点',
        'NODE_COLOR': '修改颜色'
    };

    async function showOperationLogModal(currentPage, filterType) {
        currentPage = currentPage || 1;
        filterType = filterType || '';
        var pageSize = 15;

        var url = '/api/operation-log?page=' + currentPage + '&size=' + pageSize;
        if (filterType) { url += '&operationType=' + encodeURIComponent(filterType); }

        var res = await FT.api(url);
        if (res.code !== 200) {
            FT.toast(res.message || '加载失败');
            return;
        }

        var data = res.data;
        var records = data.records || [];
        var total = data.total || 0;
        var totalPages = Math.ceil(total / pageSize) || 1;

        // 筛选下拉
        var filterOptions = '<option value="">全部类型</option>';
        Object.keys(OPERATION_TYPE_MAP).forEach(function(k) {
            filterOptions += '<option value="' + k + '"' + (filterType === k ? ' selected' : '') + '>' + OPERATION_TYPE_MAP[k] + '</option>';
        });

        // 表格
        var tableHtml = '<table class="op-log-table"><thead><tr>' +
            '<th>时间</th><th>用户</th><th>操作</th><th>详情</th><th>IP</th>' +
            '</tr></thead><tbody>';
        if (records.length === 0) {
            tableHtml += '<tr><td colspan="5" style="text-align:center;color:#999;">暂无记录</td></tr>';
        } else {
            records.forEach(function(log) {
                var time = log.createTime ? log.createTime.replace('T', ' ').substring(0, 19) : '';
                var typeLabel = OPERATION_TYPE_MAP[log.operationType] || log.operationType;
                tableHtml += '<tr>' +
                    '<td>' + FT.escapeHtml(time) + '</td>' +
                    '<td>' + FT.escapeHtml(log.username || '-') + '</td>' +
                    '<td>' + FT.escapeHtml(typeLabel) + '</td>' +
                    '<td>' + FT.escapeHtml(log.operationDesc || '') + '</td>' +
                    '<td>' + FT.escapeHtml(log.ipAddress || '') + '</td>' +
                    '</tr>';
            });
        }
        tableHtml += '</tbody></table>';

        // 分页
        var pageHtml = '<div class="op-log-page">';
        pageHtml += '<span>共 ' + total + ' 条</span>';
        pageHtml += '<span>';
        if (currentPage > 1) {
            pageHtml += '<button class="btn-sm op-log-page-btn" data-page="' + (currentPage - 1) + '">上一页</button>';
        }
        pageHtml += ' 第 ' + currentPage + ' / ' + totalPages + ' 页 ';
        if (currentPage < totalPages) {
            pageHtml += '<button class="btn-sm op-log-page-btn" data-page="' + (currentPage + 1) + '">下一页</button>';
        }
        pageHtml += '</span></div>';

        var bodyHtml = '<h3 style="margin-bottom:10px;">操作日志</h3>' +
            '<div style="margin-bottom:10px;display:flex;align-items:center;gap:8px;">' +
            '<span style="font-size:13px;color:#999;">筛选：</span>' +
            '<select id="op-log-filter" style="padding:4px 8px;font-size:13px;border:1px solid var(--border);border-radius:4px;">' + filterOptions + '</select>' +
            '</div>' +
            tableHtml + pageHtml +
            '<div class="modal-actions" style="margin-top:10px;">' +
            '<button class="btn-cancel" data-close-modal>关闭</button></div>';

        FT.showModal(bodyHtml, true);

        // 绑定筛选
        document.getElementById('op-log-filter').addEventListener('change', function() {
            showOperationLogModal(1, this.value);
        });

        // 绑定分页
        document.querySelectorAll('.op-log-page-btn').forEach(function(btn) {
            btn.addEventListener('click', function() {
                showOperationLogModal(parseInt(btn.dataset.page, 10), filterType);
            });
        });
    }

    // ========== 时间线 ==========
    async function showTimelineModal() {
        var res = await FT.api('/api/timeline');
        if (res.code !== 200) { FT.toast(res.message || '加载失败'); return; }
        var events = res.data || [];

        var typeIcon = function(type) {
            if (type === 'BIRTH') return '<span style="color:#4caf50;">&#9679;</span>';
            if (type === 'DEATH') return '<span style="color:#999;">&#9679;</span>';
            if (type === 'MARRIAGE') return '<span style="color:#e91e63;">&#9679;</span>';
            return '&#9679;';
        };
        var typeLabel = function(type) {
            if (type === 'BIRTH') return '出生';
            if (type === 'DEATH') return '去世';
            if (type === 'MARRIAGE') return '婚姻';
            return type;
        };

        var html = '<h3 style="margin-bottom:10px;">家族时间线</h3>';
        if (events.length === 0) {
            html += '<p style="color:#999;padding:20px 0;text-align:center;">暂无事件记录</p>';
        } else {
            html += '<div class="timeline-list" style="max-height:400px;overflow-y:auto;">';
            events.forEach(function(ev) {
                html += '<div class="timeline-item" style="display:flex;align-items:flex-start;gap:10px;padding:8px 0;border-bottom:1px solid #f0f0f0;">' +
                    '<span style="font-size:16px;line-height:1;">' + typeIcon(ev.type) + '</span>' +
                    '<div style="flex:1;">' +
                    '<div style="font-size:14px;">' + FT.escapeHtml(ev.description) + '</div>' +
                    '<div style="font-size:12px;color:#999;margin-top:2px;">' + FT.escapeHtml(ev.date) + ' · ' + typeLabel(ev.type) + '</div>' +
                    '</div></div>';
            });
            html += '</div>';
        }
        html += '<div class="modal-actions"><button class="btn-cancel" data-close-modal>关闭</button></div>';
        FT.showModal(html, true);
    }

    // ========== 关系路径分析 ==========
    async function showRelationPathModal() {
        var listRes = await FT.api('/api/node/list');
        if (listRes.code !== 200) { FT.toast(listRes.message || '加载失败'); return; }
        var allNodes = listRes.data || [];

        // 找到最顶层节点（generation 最小的）
        var rootNode = null;
        allNodes.forEach(function(n) {
            if (n.generation != null) {
                if (!rootNode || n.generation < rootNode.generation) {
                    rootNode = n;
                }
            }
        });

        // 当前用户所在节点
        var myNodeId = FT.state.currentUser && FT.state.currentUser.nodeId;

        // 默认值：起始=最顶层节点，目标=自己的节点
        var defaultFromId = rootNode ? rootNode.id : '';
        var defaultToId = myNodeId || '';

        function buildNodeOptions(selectedId) {
            var html = '<option value="">请选择节点</option>';
            allNodes.forEach(function(n) {
                var sel = n.id === selectedId ? ' selected' : '';
                var gen = n.generation != null ? '第' + n.generation + '世' : '';
                html += '<option value="' + n.id + '"' + sel + '>' + FT.escapeHtml(n.name) + '（' + gen + '）</option>';
            });
            return html;
        }

        var html = '<h3 style="margin-bottom:10px;">关系路径分析</h3>' +
            '<p style="font-size:13px;color:#999;margin-bottom:10px;">选择两个节点，查看它们之间的最短关系路径</p>' +
            '<div style="display:flex;gap:10px;margin-bottom:12px;">' +
            '<div class="form-group" style="flex:1;margin-bottom:0;"><label>起始节点</label><select id="rpath-from">' + buildNodeOptions(defaultFromId) + '</select></div>' +
            '<div class="form-group" style="flex:1;margin-bottom:0;"><label>目标节点</label><select id="rpath-to">' + buildNodeOptions(defaultToId) + '</select></div>' +
            '</div>' +
            '<button class="btn-sm" id="rpath-calc" style="margin-bottom:12px;">计算路径</button>' +
            '<div id="rpath-result"></div>' +
            '<div class="modal-actions"><button class="btn-cancel" data-close-modal>关闭</button></div>';

        FT.showModal(html, true);

        document.getElementById('rpath-calc').addEventListener('click', async function() {
            var fromId = document.getElementById('rpath-from').value;
            var toId = document.getElementById('rpath-to').value;
            if (!fromId || !toId) { FT.toast('请选择两个节点', 'warning'); return; }
            if (fromId === toId) { FT.toast('请选择不同的节点', 'warning'); return; }

            var resultEl = document.getElementById('rpath-result');
            resultEl.innerHTML = '<p style="color:#999;">计算中...</p>';

            var res = await FT.api('/api/relation-path?fromNodeId=' + fromId + '&toNodeId=' + toId);
            if (res.code !== 200) {
                resultEl.innerHTML = '<p style="color:red;">' + FT.escapeHtml(res.message || '计算失败') + '</p>';
                return;
            }

            var data = res.data;
            if (!data.found) {
                resultEl.innerHTML = '<p style="color:#999;">两个节点之间没有关系路径</p>';
                return;
            }

            var relTypeText = function(t) {
                if (t === 1) return '父母→子女';
                if (t === 2) return '配偶';
                if (t === 3) return '收养';
                return '未知';
            };

            var pathHtml = '<div style="padding:10px;background:#faf8f2;border-radius:6px;">';
            pathHtml += '<p style="font-weight:600;margin-bottom:8px;">找到路径（共 ' + data.pathLength + ' 个节点）</p>';
            var path = data.path || [];
            path.forEach(function(step, idx) {
                pathHtml += '<span style="font-weight:600;color:#5b6b7c;">' + FT.escapeHtml(step.name) + '</span>';
                if (idx < path.length - 1) {
                    pathHtml += ' <span style="color:#999;font-size:12px;">—[' + relTypeText(step.relationType) + ']→</span> ';
                }
            });
            pathHtml += '</div>';
            resultEl.innerHTML = pathHtml;
        });
    }

    // ========== 忌日提醒 ==========
    async function showDeathAnniversaryModal() {
        var res = await FT.api('/api/death-anniversary?days=30');
        if (res.code !== 200) { FT.toast(res.message || '加载失败'); return; }
        var list = res.data || [];

        var html = '<h3 style="margin-bottom:10px;">忌日提醒 <small style="color:#999;font-weight:normal;">未来30天</small></h3>';
        if (list.length === 0) {
            html += '<p style="color:#999;padding:20px 0;text-align:center;">未来30天内没有忌日</p>';
        } else {
            html += '<div style="max-height:300px;overflow-y:auto;">';
            list.forEach(function(item) {
                var urgency = item.daysUntil <= 3 ? 'color:#a63a2b;font-weight:600;' : '';
                var dayText = item.daysUntil === 0 ? '今天' : (item.daysUntil + '天后');
                html += '<div style="display:flex;align-items:center;gap:10px;padding:8px 0;border-bottom:1px solid #f0f0f0;">' +
                    '<span style="' + urgency + '">' + FT.escapeHtml(item.name) + '</span>' +
                    '<span style="font-size:12px;color:#999;">忌日：' + FT.escapeHtml(item.deathDate) + '</span>' +
                    '<span style="font-size:12px;' + urgency + '">' + dayText + '</span>' +
                    '</div>';
            });
            html += '</div>';
        }
        html += '<div class="modal-actions"><button class="btn-cancel" data-close-modal>关闭</button></div>';
        FT.showModal(html);
    }

    FT.showOperationLogModal = showOperationLogModal;
    FT.showTimelineModal = showTimelineModal;
    FT.showRelationPathModal = showRelationPathModal;
    FT.showDeathAnniversaryModal = showDeathAnniversaryModal;
})();
