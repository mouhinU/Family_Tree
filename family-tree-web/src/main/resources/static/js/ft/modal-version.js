/**
 * 族谱前端 - 版本控制模块
 * 提供节点历史时间线、版本对比、快照管理功能。
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
(function () {
    'use strict';

    var FT = window.FT;

    /* ================================================================
     * 节点历史弹框（入口）
     * ================================================================ */

    /**
     * 打开节点版本历史弹框
     *
     * @param {number} nodeId 节点ID
     */
    async function showVersionHistoryModal(nodeId) {
        FT.showModal(
            '<div class="version-modal-header">'
            + '<h3 style="text-align:center;margin-bottom:0;">版本历史</h3>'
            + '<div class="version-tabs">'
            + '<button class="version-tab active" data-tab="timeline">历史时间线</button>'
            + '<button class="version-tab" data-tab="snapshot">快照管理</button>'
            + '</div>'
            + '</div>'
            + '<div id="version-tab-content" class="version-tab-content">'
            + '<div id="timeline-panel" class="version-panel active">'
            + '<div id="node-history-list" class="history-list"><p class="loading-text">加载中...</p></div>'
            + '</div>'
            + '<div id="snapshot-panel" class="version-panel">'
            + '<div style="margin-bottom:12px;text-align:right;">'
            + '<button class="btn-sm primary" id="btn-create-snapshot">创建快照</button>'
            + '</div>'
            + '<div id="snapshot-list" class="snapshot-list"><p class="loading-text">加载中...</p></div>'
            + '</div>'
            + '</div>'
            + '<div style="text-align:right;margin-top:12px;padding-top:8px;border-top:1px solid #d8cba8;">'
            + '<button class="btn-sm" data-close-modal>关闭</button>'
            + '</div>',
            'modal-medium'
        );

        bindTabEvents();
        await loadNodeHistory(nodeId);
        loadSnapshots();
    }

    /* ================================================================
     * Tab 切换
     * ================================================================ */

    function bindTabEvents() {
        document.querySelectorAll('.version-tab').forEach(function (tab) {
            tab.addEventListener('click', function () {
                document.querySelectorAll('.version-tab').forEach(function (t) {
                    t.classList.remove('active');
                });
                document.querySelectorAll('.version-panel').forEach(function (p) {
                    p.classList.remove('active');
                });
                tab.classList.add('active');
                var panelId = tab.dataset.tab === 'timeline' ? 'timeline-panel' : 'snapshot-panel';
                document.getElementById(panelId).classList.add('active');
            });
        });
    }

    /* ================================================================
     * 节点历史时间线
     * ================================================================ */

    async function loadNodeHistory(nodeId) {
        var listEl = document.getElementById('node-history-list');
        if (!listEl) { return; }

        try {
            var res = await FT.api('/api/version/node/' + nodeId + '/history?page=1&size=50');
            if (res.code === 200 && res.data) {
                var list = res.data.list || [];
                if (list.length === 0) {
                    listEl.innerHTML = '<p class="empty-text">暂无历史记录</p>';
                    return;
                }
                var html = '';
                list.forEach(function (h, idx) {
                    var opIcon = getOperationIcon(h.operationType);
                    var opClass = 'op-' + h.operationType.toLowerCase();
                    html += '<div class="history-item">'
                        + '<div class="history-item-line">'
                        + '<span class="history-dot ' + opClass + '"></span>'
                        + (idx < list.length - 1 ? '<span class="history-connector"></span>' : '')
                        + '</div>'
                        + '<div class="history-item-body">'
                        + '<div class="history-item-header">'
                        + '<span class="history-op-icon">' + opIcon + '</span>'
                        + '<span class="history-op-type ' + opClass + '">' + getOperationLabel(h.operationType) + '</span>'
                        + '<span class="history-version">v' + h.versionNumber + '</span>'
                        + '</div>'
                        + '<div class="history-summary">' + FT.escapeHtml(h.changeSummary || '') + '</div>'
                        + '<div class="history-meta">'
                        + '<span>' + FT.escapeHtml(h.operatorName || '未知') + '</span>'
                        + '<span>' + formatTime(h.createTime) + '</span>'
                        + '</div>'
                        + '<div class="history-actions">'
                        + (h.afterData || h.beforeData ? '<button class="btn-sm" onclick="FT.showVersionDetail(' + nodeId + ',' + h.versionNumber + ')">查看详情</button>' : '')
                        + '</div>'
                        + '</div>'
                        + '</div>';
                });
                listEl.innerHTML = html;
            } else {
                listEl.innerHTML = '<p class="empty-text error">加载失败</p>';
            }
        } catch (e) {
            listEl.innerHTML = '<p class="empty-text error">请求失败</p>';
        }
    }

    /* ================================================================
     * 版本详情 / 对比
     * ================================================================ */

    /**
     * 显示单个版本的数据详情
     */
    async function showVersionDetail(nodeId, versionNumber) {
        try {
            var res = await FT.api('/api/version/node/' + nodeId + '/history?page=1&size=50');
            if (res.code !== 200) {
                FT.toast('加载失败', 'error');
                return;
            }
            var list = res.data.list || [];
            var history = null;
            for (var i = 0; i < list.length; i++) {
                if (list[i].versionNumber === versionNumber) {
                    history = list[i];
                    break;
                }
            }
            if (!history) {
                FT.toast('版本不存在', 'error');
                return;
            }

            var data = history.afterData || history.beforeData;
            var jsonData = {};
            try { jsonData = JSON.parse(data); } catch (e) { /* ignore */ }

            var html = '<div style="text-align:left;">'
                + '<h4 style="margin-bottom:8px;">v' + versionNumber + ' ' + getOperationLabel(history.operationType) + '</h4>'
                + '<div class="version-detail-grid">';
            var fieldLabels = {
                name: '姓名', gender: '性别', birthDate: '出生日期', deathDate: '去世日期',
                generation: '世代', birthOrder: '排次', colorLabel: '颜色标签',
                remark: '备注', lunarBirthDate: '农历生日', lunarDeathDate: '农历忌日',
                zi: '字', hao: '号', hui: '讳', graveLocation: '墓地位置',
                spouseName: '配偶姓名', spouseOriginFamily: '配偶原籍'
            };
            Object.keys(fieldLabels).forEach(function (key) {
                if (jsonData[key] !== undefined && jsonData[key] !== null) {
                    html += '<div class="version-detail-row">'
                        + '<span class="version-detail-label">' + fieldLabels[key] + '</span>'
                        + '<span class="version-detail-value">' + FT.escapeHtml(String(jsonData[key])) + '</span>'
                        + '</div>';
                }
            });
            html += '</div>'
                + '<div style="text-align:right;margin-top:12px;">'
                + '<button class="btn-sm" id="btn-close-detail">关闭</button>'
                + '</div></div>';

            FT.showModal(html, 'modal-medium');
            document.getElementById('btn-close-detail').addEventListener('click', function () {
                FT.closeModal();
            });
        } catch (e) {
            FT.toast('加载版本详情失败', 'error');
        }
    }

    /* ================================================================
     * 快照管理
     * ================================================================ */

    async function loadSnapshots() {
        var listEl = document.getElementById('snapshot-list');
        if (!listEl) { return; }

        try {
            var res = await FT.api('/api/version/snapshot/list');
            if (res.code === 200 && res.data) {
                var list = res.data;
                if (!Array.isArray(list) || list.length === 0) {
                    listEl.innerHTML = '<p class="empty-text">暂无快照</p>';
                    bindCreateSnapshot();
                    return;
                }
                var html = '';
                list.forEach(function (s) {
                    html += '<div class="snapshot-item">'
                        + '<div class="snapshot-item-header">'
                        + '<strong>' + FT.escapeHtml(s.snapshotName || '未命名快照') + '</strong>'
                        + '<span class="snapshot-time">' + formatTime(s.createTime) + '</span>'
                        + '</div>'
                        + '<div class="snapshot-item-meta">'
                        + '<span>' + (s.nodeCount || 0) + ' 节点</span>'
                        + '<span>' + (s.relationCount || 0) + ' 关系</span>'
                        + '<span>创建者: ' + FT.escapeHtml(s.creatorName || '未知') + '</span>'
                        + '</div>'
                        + (s.description ? '<div class="snapshot-item-desc">' + FT.escapeHtml(s.description) + '</div>' : '')
                        + '<div class="snapshot-item-actions">'
                        + '<button class="btn-sm" onclick="FT.previewSnapshot(' + s.id + ')">预览恢复</button> '
                        + '<button class="btn-sm danger" onclick="FT.deleteSnapshot(' + s.id + ')">删除</button>'
                        + '</div>'
                        + '</div>';
                });
                listEl.innerHTML = html;
            } else {
                listEl.innerHTML = '<p class="empty-text error">加载失败</p>';
            }
        } catch (e) {
            listEl.innerHTML = '<p class="empty-text error">请求失败</p>';
        }

        bindCreateSnapshot();
    }

    function bindCreateSnapshot() {
        var btn = document.getElementById('btn-create-snapshot');
        if (btn) {
            btn.addEventListener('click', function () {
                showCreateSnapshotForm();
            });
        }
    }

    function showCreateSnapshotForm() {
        var html = '<div style="text-align:left;">'
            + '<h4 style="margin-bottom:12px;">创建快照</h4>'
            + '<div style="margin-bottom:10px;">'
            + '<label>快照名称</label>'
            + '<input type="text" id="snapshot-name" class="modal-input" placeholder="例如：春节备份" style="width:100%;">'
            + '</div>'
            + '<div style="margin-bottom:10px;">'
            + '<label>描述（可选）</label>'
            + '<textarea id="snapshot-desc" class="modal-input" rows="3" placeholder="快照描述..." style="width:100%;resize:vertical;"></textarea>'
            + '</div>'
            + '<div style="text-align:right;margin-top:12px;">'
            + '<button class="btn-sm" id="btn-cancel-snapshot">取消</button> '
            + '<button class="btn-sm primary" id="btn-confirm-snapshot">创建</button>'
            + '</div></div>';

        FT.showModal(html, 'modal-medium');

        document.getElementById('btn-cancel-snapshot').addEventListener('click', function () {
            FT.closeModal();
        });
        document.getElementById('btn-confirm-snapshot').addEventListener('click', async function () {
            var name = document.getElementById('snapshot-name').value.trim();
            var desc = document.getElementById('snapshot-desc').value.trim();
            if (!name) {
                FT.toast('请输入快照名称', 'warning');
                return;
            }
            try {
                var res = await FT.api('/api/version/snapshot', {
                    method: 'POST',
                    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                    body: 'snapshotName=' + encodeURIComponent(name)
                        + '&description=' + encodeURIComponent(desc)
                });
                if (res.code === 200) {
                    FT.toast('快照创建成功');
                    FT.closeModal();
                } else {
                    FT.toast('创建失败: ' + (res.message || ''), 'error');
                }
            } catch (e) {
                FT.toast('创建失败', 'error');
            }
        });
    }

    /**
     * 预览快照恢复
     */
    async function previewSnapshot(snapshotId) {
        try {
            var res = await FT.api('/api/version/snapshot/' + snapshotId + '/preview', {
                method: 'POST'
            });
            if (res.code === 200 && res.data) {
                var nodeCount = res.data.nodes ? res.data.nodes.length : 0;
                var relationCount = res.data.relations ? res.data.relations.length : 0;
                var html = '<div style="text-align:left;">'
                    + '<h4 style="margin-bottom:12px;">快照预览</h4>'
                    + '<p>此快照包含 <strong>' + nodeCount + '</strong> 个节点和 <strong>'
                    + relationCount + '</strong> 个关系。</p>'
                    + '<p style="color:#a63a2b;margin-top:8px;">注意：恢复操作将覆盖当前数据，请谨慎操作。</p>'
                    + '<div style="text-align:right;margin-top:12px;">'
                    + '<button class="btn-sm" id="btn-close-preview">关闭</button>'
                    + '</div></div>';

                FT.showModal(html, 'modal-medium');
                document.getElementById('btn-close-preview').addEventListener('click', function () {
                    FT.closeModal();
                });
            } else {
                FT.toast('预览失败', 'error');
            }
        } catch (e) {
            FT.toast('请求失败', 'error');
        }
    }

    /**
     * 删除快照
     */
    async function deleteSnapshot(snapshotId) {
        if (!confirm('确定删除此快照？')) { return; }
        try {
            var res = await FT.api('/api/version/snapshot/' + snapshotId, {
                method: 'DELETE'
            });
            if (res.code === 200) {
                FT.toast('快照已删除');
                loadSnapshots();
            } else {
                FT.toast('删除失败', 'error');
            }
        } catch (e) {
            FT.toast('请求失败', 'error');
        }
    }

    /* ================================================================
     * 工具函数
     * ================================================================ */

    function getOperationIcon(type) {
        switch (type) {
            case 'CREATE': return '&#10010;';
            case 'UPDATE': return '&#9998;';
            case 'DELETE': return '&#10006;';
            default: return '&#8226;';
        }
    }

    function getOperationLabel(type) {
        switch (type) {
            case 'CREATE': return '创建';
            case 'UPDATE': return '修改';
            case 'DELETE': return '删除';
            default: return type;
        }
    }

    function formatTime(timeStr) {
        if (!timeStr) { return ''; }
        try {
            var d = new Date(timeStr);
            if (isNaN(d.getTime())) { return timeStr; }
            var month = (d.getMonth() + 1).toString().padStart(2, '0');
            var day = d.getDate().toString().padStart(2, '0');
            var hour = d.getHours().toString().padStart(2, '0');
            var min = d.getMinutes().toString().padStart(2, '0');
            return d.getFullYear() + '-' + month + '-' + day + ' ' + hour + ':' + min;
        } catch (e) {
            return timeStr;
        }
    }

    /* ================================================================
     * 导出到 FT 命名空间
     * ================================================================ */
    FT.showVersionHistoryModal = showVersionHistoryModal;
    FT.showVersionDetail = showVersionDetail;
    FT.previewSnapshot = previewSnapshot;
    FT.deleteSnapshot = deleteSnapshot;
})();
