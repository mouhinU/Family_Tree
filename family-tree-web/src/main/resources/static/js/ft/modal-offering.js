/**
 * 族谱前端 - 祭奠模块
 * 香烛缅怀场景、祭奠面板、敬献/送鲜花按钮与动效（金光/火苗/青烟/花瓣）。
 *
 * @author Family-Tree
 * @date 2026-08-09
 */
(function () {
    'use strict';

    var FT = window.FT;

    // ========== 祭奠（香烛缅怀） ==========
    // 已故节点香烛缅怀场景：中央三炷香插于金铜小炉（火星明灭、青烟袅袅），
    // 左右各一支红烛（烛焰错峰摇曳），如供桌陈设。
    function buildOfferingHtml() {
        var phases = [[0, 1.7], [0.9, 2.5], [0.45, 2.1]];
        var sticks = '';
        phases.forEach(function (p) {
            sticks += '<span class="incense-unit">' +
                '<span class="smoke-track">' +
                '<i class="smoke" style="animation-delay:' + p[0] + 's;"></i>' +
                '<i class="smoke" style="animation-delay:' + p[1] + 's;animation-duration:3.8s;"></i>' +
                '</span>' +
                '<span class="incense-stick"></span>' +
                '</span>';
        });
        // 左右红烛：烛焰动画错峰，避免同步摇曳
        var candleLeft = '<span class="candle-unit">' +
            '<span class="candle-flame"></span>' +
            '<span class="candle-wick"></span>' +
            '<span class="candle-body"></span>' +
            '</span>';
        var candleRight = '<span class="candle-unit">' +
            '<span class="candle-flame" style="animation-delay:0.45s;animation-duration:1.6s;"></span>' +
            '<span class="candle-wick"></span>' +
            '<span class="candle-body"></span>' +
            '</span>';
        return '<div class="incense-scene" title="为逝者敬上香烛">' +
            '<div class="offering-row">' +
            candleLeft +
            '<span class="incense-middle">' +
            '<span class="incense-row">' + sticks + '</span>' +
            '<span class="incense-burner"></span>' +
            '</span>' +
            candleRight +
            '</div>' +
            '<div class="altar-table">' +
            '<div class="altar-top"></div>' +
            '<div class="altar-base">' +
            '<span class="altar-leg"></span>' +
            '<span class="altar-apron"></span>' +
            '<span class="altar-leg"></span>' +
            '</div>' +
            '</div>' +
            '<div class="incense-text">音容宛在 · 德泽长存</div>' +
            '</div>';
    }

    // 祭奠面板骨架：详情弹窗打开后由 loadOfferingPanel 异步填充统计与按钮
    function offeringPanelShell() {
        return '<div class="offering-panel" id="offering-panel">' +
            '<div class="offering-loading">载入祭奠记录…</div>' +
            '</div>';
    }

    // 依据后端统计渲染祭奠面板：敬献（香烛+烧纸合一）/ 送鲜花 按钮 + 各自的总次数
    function buildOfferingPanelHtml(nodeId, stats) {
        var escId = FT.escapeAttr(nodeId);
        var html = '<div class="offering-actions">' +
            '<button class="offering-btn offering-btn--incense js-offering-btn" data-node-id="' + escId + '" data-type="4">敬献</button>' +
            '<button class="offering-btn offering-btn--flower js-offering-btn" data-node-id="' + escId + '" data-type="3">送鲜花</button>' +
            '</div>';
        var statClassMap = {3: 'flower', 4: 'worship'};
        var emptyTextMap = {3: '暂无人送鲜花', 4: '暂无人敬献'};
        html += '<div class="offering-stats">';
        (stats || []).forEach(function (stat) {
            if (stat.offeringType === 1 || stat.offeringType === 2) return;
            var cssClass = statClassMap[stat.offeringType] || 'incense';
            var emptyText = emptyTextMap[stat.offeringType] || '暂无记录';
            var countText = stat.totalCount > 0 ? stat.totalCount + ' 次' : emptyText;
            html += '<div class="offering-stat offering-stat--' + cssClass + '">' +
                '<div class="offering-stat-head">' +
                '<span class="offering-stat-name">' + FT.escapeHtml(stat.typeName) + '</span>' +
                '<span class="offering-stat-count">' + countText + '</span>' +
                '</div>' +
                '</div>';
        });
        html += '</div>';
        return html;
    }

    // 拉取某节点祭奠统计并渲染面板（上香/烧纸后复用此函数刷新）
    async function loadOfferingPanel(nodeId) {
        var panel = document.getElementById('offering-panel');
        if (!panel) {
            return;
        }
        var res = await FT.api('/api/offering/node/' + nodeId);
        if (res.code !== 200) {
            panel.innerHTML = '<div class="offering-loading">' + FT.escapeHtml(res.message || '载入失败') + '</div>';
            return;
        }
        panel.innerHTML = buildOfferingPanelHtml(nodeId, res.data || []);
        wireOfferingButtons(nodeId);
    }

    // 绑定敬献 / 送鲜花按钮：每次点击记录一次，成功后刷新统计
    function wireOfferingButtons(nodeId) {
        document.querySelectorAll('.js-offering-btn').forEach(function (btn) {
            btn.addEventListener('click', async function () {
                var offeringType = parseInt(btn.dataset.type, 10);
                btn.disabled = true;
                var res = await FT.api('/api/offering', {
                    method: 'POST',
                    body: JSON.stringify({nodeId: nodeId, offeringType: offeringType})
                });
                btn.disabled = false;
                if (res.code !== 200) {
                    FT.toast(res.message || '操作失败');
                    return;
                }
                playOfferingEffect(offeringType);
                await loadOfferingPanel(nodeId);
            });
        });
    }

    // 播放一次性祭奠动效：在香案场景（.incense-scene）上叠加动效层，
    // 动画结束后自动移除，不残留任何 DOM。
    function playOfferingEffect(type) {
        var scene = document.querySelector('.incense-scene');
        if (!scene) {
            return;
        }
        var fx = document.createElement('div');
        fx.className = 'offering-fx';
        if (type === 4) {
            fx.innerHTML = buildWorshipFxHtml();
        } else if (type === 3) {
            fx.innerHTML = buildFlowerFxHtml();
        } else if (type === 1) {
            fx.innerHTML = buildIncenseFxHtml();
        } else {
            fx.innerHTML = buildPaperFxHtml();
        }
        scene.appendChild(fx);
        setTimeout(function () {
            fx.remove();
        }, FT.OFFERING_FX_DURATION);
    }

    // 敬献动效（香烛+烧纸合一）：金光 + 火苗 + 青烟 + 浮字
    function buildWorshipFxHtml() {
        return '<div class="fx-glow"></div>' +
            '<span class="fx-smoke fx-smoke--1"></span>' +
            '<span class="fx-smoke fx-smoke--2"></span>' +
            '<span class="fx-smoke fx-smoke--3"></span>' +
            '<div class="fx-paper fx-paper--mini">' +
            '<span class="fx-flame fx-flame--1"></span>' +
            '<span class="fx-flame fx-flame--2"></span>' +
            '<span class="fx-ember fx-ember--1"></span>' +
            '<span class="fx-ember fx-ember--2"></span>' +
            '</div>' +
            '<div class="fx-text fx-text--worship">祭品已敬上</div>';
    }

    // 上香烛动效：香炉泛起金光、青烟缭绕升起、浮字"香烛已敬上"
    function buildIncenseFxHtml() {
        return '<div class="fx-glow"></div>' +
            '<span class="fx-smoke fx-smoke--1"></span>' +
            '<span class="fx-smoke fx-smoke--2"></span>' +
            '<span class="fx-smoke fx-smoke--3"></span>' +
            '<div class="fx-text">香烛已敬上</div>';
    }

    // 烧纸动效：供桌前纸钱焚化（层叠火苗 + 金色火星 + 青烟）、浮字"纸钱已焚化"
    function buildPaperFxHtml() {
        return '<div class="fx-paper">' +
            '<span class="fx-flame fx-flame--1"></span>' +
            '<span class="fx-flame fx-flame--2"></span>' +
            '<span class="fx-flame fx-flame--3"></span>' +
            '<span class="fx-ember fx-ember--1"></span>' +
            '<span class="fx-ember fx-ember--2"></span>' +
            '<span class="fx-ember fx-ember--3"></span>' +
            '<span class="fx-ember fx-ember--4"></span>' +
            '<div class="fx-paper-pile"></div>' +
            '</div>' +
            '<span class="fx-smoke fx-smoke--paper"></span>' +
            '<div class="fx-text fx-text--paper">纸钱已焚化</div>';
    }

    // 送鲜花动效：花瓣从上方飘落、浮字"鲜花已敬献"
    function buildFlowerFxHtml() {
        return '<span class="fx-petal fx-petal--1"></span>' +
            '<span class="fx-petal fx-petal--2"></span>' +
            '<span class="fx-petal fx-petal--3"></span>' +
            '<span class="fx-petal fx-petal--4"></span>' +
            '<span class="fx-petal fx-petal--5"></span>' +
            '<span class="fx-petal fx-petal--6"></span>' +
            '<span class="fx-petal fx-petal--7"></span>' +
            '<span class="fx-petal fx-petal--8"></span>' +
            '<div class="fx-flower-bloom"></div>' +
            '<div class="fx-text fx-text--flower">鲜花已敬献</div>';
    }

    FT.buildOfferingHtml = buildOfferingHtml;
    FT.offeringPanelShell = offeringPanelShell;
    FT.buildOfferingPanelHtml = buildOfferingPanelHtml;
    FT.loadOfferingPanel = loadOfferingPanel;
    FT.wireOfferingButtons = wireOfferingButtons;
    FT.playOfferingEffect = playOfferingEffect;
    FT.buildWorshipFxHtml = buildWorshipFxHtml;
    FT.buildIncenseFxHtml = buildIncenseFxHtml;
    FT.buildPaperFxHtml = buildPaperFxHtml;
    FT.buildFlowerFxHtml = buildFlowerFxHtml;
})();
