/**
 * 族谱前端 - 弹窗模块
 * 模态框基座、节点增删改弹窗、排次管理、辈分管理、详情弹窗（含配偶婚姻管理）、
 * 去世日期设置、祭奠（香烛/烧纸）面板与动效。
 *
 * @author Family-Tree
 * @date 2026-08-02
 */
(function () {
    'use strict';

    const FT = window.FT;

    // ========== 模态框基座 ==========
    function showModal(html, wide) {
        const modalContainer = document.getElementById('modal-container');
        modalContainer.innerHTML = '<div class="modal-overlay"><div class="modal' + (wide ? ' modal-wide' : '') + '">' + html + '</div></div>';
        modalContainer.querySelector('.modal-overlay').addEventListener('click', function (e) {
            if (e.target === this) closeModal();
        });
    }

    function closeModal() {
        document.getElementById('modal-container').innerHTML = '';
    }

    // 暴露关闭模态框方法（供弹窗内 inline onclick 调用）
    window._closeModal = closeModal;

    // ========== 节点新增 ==========
    // 新增节点表单（不含去世日期）
    function showNodeModal(title, relationOpts) {
        const colorOpts = FT.buildColorOptions();

        // 排次仅在新增子女时可指定，缺省由后端自动排在末位
        const orderField = relationOpts.parentNodeId
            ? '<div class="form-group"><label>排次</label><input type="number" id="modal-order" min="1" placeholder="自动（排在末位）"></div>'
            : '';

        showModal(
            '<h3>' + title + '</h3>' +
            '<div class="form-group"><label>姓名</label><input type="text" id="modal-name" placeholder="请输入姓名"></div>' +
            '<div class="form-group"><label>性别</label><select id="modal-gender">' +
            '<option value="0">未知</option><option value="1">男</option><option value="2">女</option></select></div>' +
            '<div class="form-group"><label>出生日期</label><input type="date" id="modal-birth"></div>' +
            orderField +
            '<div class="form-group"><label>颜色标注</label><select id="modal-color">' + colorOpts + '</select></div>' +
            '<div class="form-group"><label>备注</label><input type="text" id="modal-remark" placeholder="可选"></div>' +
            '<div class="modal-actions">' +
            '<button class="btn-cancel" onclick="window._closeModal()">取消</button>' +
            '<button class="btn-confirm" id="modal-submit">确定</button></div>'
        );

        // 添加配偶时按目标节点性别自动预选相反性别：男→女，女→男；未知则保持"未知"
        if (relationOpts.spouseNodeId) {
            const targetNode = FT.findNodeIncludeSpouseById(FT.state.treeData, relationOpts.spouseNodeId);
            if (targetNode) {
                const genderSelect = document.getElementById('modal-gender');
                if (targetNode.gender === 1) {
                    genderSelect.value = '2';
                } else if (targetNode.gender === 2) {
                    genderSelect.value = '1';
                }
            }
        }

        document.getElementById('modal-submit').addEventListener('click', async function () {
            const name = document.getElementById('modal-name').value.trim();
            if (!name) { alert('请输入姓名'); return; }

            const orderEl = document.getElementById('modal-order');
            const body = {
                name: name,
                gender: parseInt(document.getElementById('modal-gender').value),
                birthDate: document.getElementById('modal-birth').value || null,
                birthOrder: orderEl && orderEl.value ? parseInt(orderEl.value) : null,
                colorLabel: document.getElementById('modal-color').value,
                remark: document.getElementById('modal-remark').value || null,
                parentNodeId: relationOpts.parentNodeId || null,
                spouseNodeId: relationOpts.spouseNodeId || null,
                childNodeId: relationOpts.childNodeId || null
            };

            const res = await FT.api('/api/node', {method: 'POST', body: JSON.stringify(body)});
            if (res.code === 200) {
                closeModal();
                await FT.loadTree();
            } else {
                alert(res.message || '操作失败');
            }
        });
    }

    // 关联现有成员为配偶（适用于表兄妹等近亲结婚：双方均已在族谱中有各自分支）
    async function showLinkSpouseModal(nodeId) {
        const nodeRes = await FT.api('/api/node/' + nodeId);
        if (nodeRes.code !== 200) { alert(nodeRes.message || '节点不存在'); return; }
        const currentNode = nodeRes.data;

        const listRes = await FT.api('/api/node/list');
        if (listRes.code !== 200) { alert(listRes.message || '加载成员列表失败'); return; }
        const allNodes = (listRes.data || []).filter(function (n) { return n.id !== currentNode.id; });

        const genderText = function (g) { return g === 1 ? '男' : (g === 2 ? '女' : ''); };
        const genText = function (g) {
            if (g == null) { return ''; }
            const name = FT.state.generationNames[g];
            return '第' + g + '世' + (name ? '·' + FT.escapeHtml(name) : '');
        };

        function buildOptions(keyword) {
            const kw = (keyword || '').trim();
            const html = allNodes
                .filter(function (n) { return !kw || (n.name || '').indexOf(kw) >= 0; })
                .map(function (n) {
                    const meta = [genText(n.generation), genderText(n.gender)].filter(Boolean).join('·');
                    return '<div class="link-spouse-opt" data-id="' + n.id + '">' +
                        '<span class="opt-name">' + FT.escapeHtml(n.name) + '</span>' +
                        (meta ? '<span class="opt-meta">' + meta + '</span>' : '') +
                        '</div>';
                }).join('');
            return html || '<div class="link-spouse-empty">无匹配成员</div>';
        }

        showModal(
            '<h3>关联配偶</h3>' +
            '<p class="link-spouse-tip">为「' + FT.escapeHtml(currentNode.name) + '」关联族谱中已有成员为配偶' +
            '（适用于表兄妹等近亲结婚，双方将各自保留在原分支并连线）</p>' +
            '<div class="form-group"><input type="text" id="link-spouse-search" placeholder="搜索姓名…"></div>' +
            '<div class="link-spouse-list" id="link-spouse-list">' + buildOptions('') + '</div>' +
            '<div class="modal-actions">' +
            '<button class="btn-cancel" onclick="window._closeModal()">取消</button>' +
            '<button class="btn-confirm" id="link-spouse-submit">确定</button></div>'
        );

        let selectedId = null;
        const listEl = document.getElementById('link-spouse-list');
        listEl.addEventListener('click', function (e) {
            const opt = e.target.closest('.link-spouse-opt');
            if (!opt) { return; }
            listEl.querySelectorAll('.link-spouse-opt').forEach(function (o) { o.classList.remove('selected'); });
            opt.classList.add('selected');
            selectedId = parseInt(opt.getAttribute('data-id'));
        });
        document.getElementById('link-spouse-search').addEventListener('input', function () {
            listEl.innerHTML = buildOptions(this.value);
            selectedId = null;
        });

        document.getElementById('link-spouse-submit').addEventListener('click', async function () {
            if (!selectedId) { alert('请选择一位成员'); return; }
            const selected = allNodes.find(function (n) { return n.id === selectedId; });
            // 关系方向约定：from 为男方。任一为男性则以其为 from，否则以当前节点为 from。
            let fromId = currentNode.id, toId = selectedId;
            if (currentNode.gender !== 1 && selected && selected.gender === 1) {
                fromId = selectedId;
                toId = currentNode.id;
            }
            const res = await FT.api('/api/relation', {
                method: 'POST',
                body: JSON.stringify({fromNodeId: fromId, toNodeId: toId, relationType: 2})
            });
            if (res.code === 200) {
                closeModal();
                await FT.loadTree();
            } else {
                alert(res.message || '关联失败');
            }
        });
    }

    // ========== 节点编辑 ==========
    // 编辑节点基本信息（不含去世日期）
    async function editNode(nodeId) {
        const res = await FT.api('/api/node/' + nodeId);
        if (res.code !== 200) { alert(res.message); return; }
        const node = res.data;

        const colorOpts = FT.buildColorOptions(node.colorLabel);

        const genderOpts = [0, 1, 2].map(function (g) {
            const labels = ['未知', '男', '女'];
            const sel = g === node.gender ? ' selected' : '';
            return '<option value="' + g + '"' + sel + '>' + labels[g] + '</option>';
        }).join('');

        // 辈分下拉选项：从辈分管理列表（generationNames）获取，按世代升序排列。
        // 选项文案为"第X世（辈分名）"，未配置辈分名时标注"未配置辈分名"。
        // 若节点当前辈分不在列表中，补充为选项，避免丢失当前值。
        const generationNames = FT.state.generationNames;
        const genKeys = Object.keys(generationNames).map(Number).sort(function (a, b) { return a - b; });
        if (node.generation != null && genKeys.indexOf(node.generation) === -1) {
            genKeys.push(node.generation);
            genKeys.sort(function (a, b) { return a - b; });
        }
        const genOpts = genKeys.map(function (g) {
            const sel = g === node.generation ? ' selected' : '';
            const label = generationNames[g] ? '第' + g + '世（' + FT.escapeHtml(generationNames[g]) + '）' : '第' + g + '世（未配置辈分名）';
            return '<option value="' + g + '"' + sel + '>' + label + '</option>';
        }).join('');

        showModal(
            '<h3>编辑基本信息</h3>' +
            '<div class="form-group"><label>姓名</label><input type="text" id="modal-name" value="' + FT.escapeAttr(node.name || '') + '"></div>' +
            '<div class="form-group"><label>性别</label><select id="modal-gender">' + genderOpts + '</select></div>' +
            '<div class="form-group"><label>出生日期</label><input type="date" id="modal-birth" value="' + FT.escapeAttr(node.birthDate || '') + '"></div>' +
            '<div class="form-group"><label>排次</label><input type="number" id="modal-order" min="1" value="' + (node.birthOrder != null ? node.birthOrder : '') + '" placeholder="未设置"></div>' +
            '<div class="form-group"><label>辈分（第几世）</label><select id="modal-generation">' + genOpts + '</select></div>' +
            '<div class="form-group"><label>颜色标注</label><select id="modal-color">' + colorOpts + '</select></div>' +
            '<div class="form-group"><label>备注</label><input type="text" id="modal-remark" value="' + FT.escapeAttr(node.remark || '') + '"></div>' +
            '<div class="modal-actions">' +
            '<button class="btn-cancel" onclick="window._closeModal()">取消</button>' +
            '<button class="btn-confirm" id="modal-submit">保存</button></div>'
        );

        document.getElementById('modal-submit').addEventListener('click', async function () {
            const orderRaw = document.getElementById('modal-order').value;
            const genRaw = document.getElementById('modal-generation').value;
            const body = {
                id: node.id,
                name: document.getElementById('modal-name').value.trim(),
                gender: parseInt(document.getElementById('modal-gender').value),
                birthDate: document.getElementById('modal-birth').value,
                birthOrder: orderRaw ? parseInt(orderRaw) : null,
                generation: genRaw ? parseInt(genRaw) : null,
                colorLabel: document.getElementById('modal-color').value,
                remark: document.getElementById('modal-remark').value || null
            };

            const res = await FT.api('/api/node', {method: 'PUT', body: JSON.stringify(body)});
            if (res.code === 200) {
                closeModal();
                await FT.loadTree();
            } else {
                alert(res.message || '保存失败');
            }
        });
    }

    // ========== 排次管理 ==========
    // 批量管理子女排次
    function showBirthOrderModal(nodeId) {
        const node = FT.findNodeById(FT.state.treeData, nodeId);
        if (!node) { return; }
        const children = (node.children || []).slice();

        if (children.length === 0) {
            showModal('<h3>排次管理</h3>' +
                '<p style="color:#999;padding:12px 0;">该节点暂无子女，无需设置排次。</p>' +
                '<div class="modal-actions"><button class="btn-cancel" onclick="window._closeModal()">关闭</button></div>');
            return;
        }

        // 按当前排次排序（未设置的按 ID 排在后面）
        children.sort(function (a, b) {
            const ao = a.birthOrder != null ? a.birthOrder : Number.MAX_SAFE_INTEGER;
            const bo = b.birthOrder != null ? b.birthOrder : Number.MAX_SAFE_INTEGER;
            return ao - bo || a.id - b.id;
        });

        let rows = '';
        children.forEach(function (c) {
            const birthYear = c.birthDate ? c.birthDate.substring(0, 4) : '';
            rows += '<div style="display:flex;align-items:center;gap:10px;padding:6px 0;border-bottom:1px solid #f0f0f0;">' +
                '<input type="number" min="1" class="bo-input" data-id="' + c.id + '" value="' + (c.birthOrder != null ? c.birthOrder : '') + '" ' +
                'style="width:64px;padding:4px 6px;border:1px solid #ddd;border-radius:4px;text-align:center;">' +
                '<span style="flex:1;font-size:14px;">' + FT.escapeHtml(c.name) + '</span>' +
                (birthYear ? '<span style="color:#999;font-size:12px;">' + birthYear + '年生</span>' : '') +
                '</div>';
        });

        showModal(
            '<h3>排次管理 - ' + FT.escapeHtml(node.name) + ' 的子女（共' + children.length + '人）</h3>' +
            '<p style="font-size:13px;color:#999;margin-bottom:8px;">为每个子女设置排次，数字小的排在前面，可用下方按钮快速编号。</p>' +
            '<div id="bo-rows" style="max-height:320px;overflow-y:auto;">' + rows + '</div>' +
            '<div style="display:flex;gap:8px;margin-top:12px;">' +
            '<button class="btn-sm" id="bo-by-birth">按出生日期编号</button>' +
            '<button class="btn-sm" id="bo-by-order">按当前顺序编号</button>' +
            '</div>' +
            '<div class="modal-actions">' +
            '<button class="btn-cancel" onclick="window._closeModal()">取消</button>' +
            '<button class="btn-confirm" id="bo-save">保存</button></div>'
        );

        function assignNumbers(ordered) {
            ordered.forEach(function (c, idx) {
                const input = document.querySelector('.bo-input[data-id="' + c.id + '"]');
                if (input) { input.value = idx + 1; }
            });
        }

        // 按出生日期编号（无出生日期的排在最后）
        document.getElementById('bo-by-birth').addEventListener('click', function () {
            const sorted = children.slice().sort(function (a, b) {
                const ad = a.birthDate || '9999';
                const bd = b.birthDate || '9999';
                if (ad !== bd) { return ad < bd ? -1 : 1; }
                return a.id - b.id;
            });
            assignNumbers(sorted);
        });

        // 按当前顺序编号
        document.getElementById('bo-by-order').addEventListener('click', function () {
            assignNumbers(children);
        });

        // 保存：仅更新排次发生变化的子女
        document.getElementById('bo-save').addEventListener('click', async function () {
            const updates = [];
            children.forEach(function (c) {
                const input = document.querySelector('.bo-input[data-id="' + c.id + '"]');
                const val = input && input.value !== '' ? parseInt(input.value) : null;
                if (val !== c.birthOrder) {
                    updates.push({id: c.id, birthOrder: val});
                }
            });
            if (updates.length === 0) {
                closeModal();
                return;
            }
            for (let i = 0; i < updates.length; i++) {
                const res = await FT.api('/api/node', {method: 'PUT', body: JSON.stringify(updates[i])});
                if (res.code !== 200) {
                    alert(res.message || '保存失败');
                    return;
                }
            }
            closeModal();
            await FT.loadTree();
        });
    }

    // ========== 辈分管理 ==========
    // 辈分管理弹窗：10 行 × 5 列网格（共 50 世），可预先规划字辈
    function showGenerationModal() {
        const counts = FT.collectGenerationCounts(FT.state.treeData, {});
        const generationNames = FT.state.generationNames;
        const TOTAL = 50;
        const ROWS = 10;
        const COLS = 5;

        let cells = '';
        for (let g = 1; g <= TOTAL; g++) {
            const count = counts[g] || 0;
            cells += '<div class="gen-cell">' +
                '<div class="gen-cell-head"><span>第' + g + '世</span>' +
                (count > 0 ? '<span class="gen-count">' + count + '人</span>' : '') +
                '</div>' +
                '<input type="text" class="gen-input" data-gen="' + g + '" maxlength="10" ' +
                'value="' + FT.escapeAttr(generationNames[g] || '') + '" placeholder="辈分名">' +
                '</div>';
        }

        showModal(
            '<h3>辈分管理</h3>' +
            '<p style="font-size:13px;color:#999;margin-bottom:12px;">为各世代设置辈分名（字辈），留空表示清除。共 ' + TOTAL + ' 世（' + ROWS + ' 行 × ' + COLS + ' 列）。</p>' +
            '<div class="gen-grid">' + cells + '</div>' +
            '<div class="modal-actions">' +
            '<button class="btn-cancel" onclick="window._closeModal()">取消</button>' +
            '<button class="btn-confirm" id="gen-save">保存</button></div>',
            true
        );

        document.getElementById('gen-save').addEventListener('click', async function () {
            const payload = [];
            for (let g = 1; g <= TOTAL; g++) {
                const input = document.querySelector('.gen-input[data-gen="' + g + '"]');
                payload.push({generation: g, name: input ? input.value.trim() : ''});
            }
            const res = await FT.api('/api/generation', {
                method: 'PUT',
                body: JSON.stringify(payload)
            });
            if (res.code === 200) {
                closeModal();
                await FT.loadTree();
            } else {
                alert(res.message || '保存失败');
            }
        });
    }

    // ========== 祭奠（香烛缅怀） ==========
    // 已故节点香烛缅怀场景：中央三炷香插于金铜小炉（火星明灭、青烟袅袅），
    // 左右各一支红烛（烛焰错峰摇曳），如供桌陈设。
    function buildOfferingHtml() {
        const phases = [[0, 1.7], [0.9, 2.5], [0.45, 2.1]];
        let sticks = '';
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
        const candleLeft = '<span class="candle-unit">' +
            '<span class="candle-flame"></span>' +
            '<span class="candle-wick"></span>' +
            '<span class="candle-body"></span>' +
            '</span>';
        const candleRight = '<span class="candle-unit">' +
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

    // 依据后端统计渲染祭奠面板：上香烛 / 烧纸按钮 + 各自的次数与人员明细
    function buildOfferingPanelHtml(nodeId, stats) {
        let html = '<div class="offering-actions">' +
            '<button class="offering-btn offering-btn--incense js-offering-btn" data-node-id="' + FT.escapeAttr(nodeId) + '" data-type="1">上香烛</button>' +
            '<button class="offering-btn offering-btn--paper js-offering-btn" data-node-id="' + FT.escapeAttr(nodeId) + '" data-type="2">烧纸</button>' +
            '</div>';
        html += '<div class="offering-stats">';
        (stats || []).forEach(function (stat) {
            const isIncense = stat.offeringType === 1;
            const emptyText = isIncense ? '暂无人上香烛' : '暂无人烧纸';
            let usersHtml;
            if (stat.users && stat.users.length > 0) {
                usersHtml = stat.users.map(function (u) {
                    return '<span class="offering-user">' + FT.escapeHtml(u.nickname) +
                        '<em>×' + u.count + '</em></span>';
                }).join('');
            } else {
                usersHtml = '<span class="offering-empty">' + emptyText + '</span>';
            }
            html += '<div class="offering-stat offering-stat--' + (isIncense ? 'incense' : 'paper') + '">' +
                '<div class="offering-stat-head">' +
                '<span class="offering-stat-name">' + FT.escapeHtml(stat.typeName) + '</span>' +
                '<span class="offering-stat-count">' + stat.totalCount + ' 次 · ' + stat.userCount + ' 人</span>' +
                '</div>' +
                '<div class="offering-stat-users">' + usersHtml + '</div>' +
                '</div>';
        });
        html += '</div>';
        return html;
    }

    // 拉取某节点祭奠统计并渲染面板（上香/烧纸后复用此函数刷新）
    async function loadOfferingPanel(nodeId) {
        const panel = document.getElementById('offering-panel');
        if (!panel) {
            return;
        }
        const res = await FT.api('/api/offering/node/' + nodeId);
        if (res.code !== 200) {
            panel.innerHTML = '<div class="offering-loading">' + FT.escapeHtml(res.message || '载入失败') + '</div>';
            return;
        }
        panel.innerHTML = buildOfferingPanelHtml(nodeId, res.data || []);
        wireOfferingButtons(nodeId);
    }

    // 绑定上香烛 / 烧纸按钮：每次点击记录一次，成功后刷新统计
    function wireOfferingButtons(nodeId) {
        document.querySelectorAll('.js-offering-btn').forEach(function (btn) {
            btn.addEventListener('click', async function () {
                const type = parseInt(btn.dataset.type, 10);
                btn.disabled = true;
                const res = await FT.api('/api/offering', {
                    method: 'POST',
                    body: JSON.stringify({nodeId: nodeId, offeringType: type})
                });
                btn.disabled = false;
                if (res.code !== 200) {
                    alert(res.message || '操作失败');
                    return;
                }
                playOfferingEffect(type);
                await loadOfferingPanel(nodeId);
            });
        });
    }

    // 播放一次性祭奠动效：在香案场景（.incense-scene）上叠加动效层，
    // 动画结束后自动移除，不残留任何 DOM。
    function playOfferingEffect(type) {
        const scene = document.querySelector('.incense-scene');
        if (!scene) {
            return;
        }
        const fx = document.createElement('div');
        fx.className = 'offering-fx';
        fx.innerHTML = type === 1 ? buildIncenseFxHtml() : buildPaperFxHtml();
        scene.appendChild(fx);
        setTimeout(function () {
            fx.remove();
        }, FT.OFFERING_FX_DURATION);
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

    // ========== 节点详情 ==========
    // 节点详情（含去世日期编辑 + 配偶离异管理）
    function showDetailModal(node) {
        const generationNames = FT.state.generationNames;
        const genderText = node.gender === 1 ? '男' : node.gender === 2 ? '女' : '未知';
        const deceased = node.deathDate != null && node.deathDate !== '';
        // 香烛拜台仅敬献给「长辈」：节点已故且辈分高于登录人（世代数更小，更靠近始祖）。
        // 登录人或节点辈分缺失时无法论资排辈，不予展示。
        const currentUser = FT.state.currentUser;
        const myGeneration = currentUser && currentUser.generation;
        const isSenior = deceased && myGeneration != null && node.generation != null && node.generation < myGeneration;
        const color = deceased ? FT.DECEASED_COLOR : (FT.COLOR_MAP[node.colorLabel] || FT.COLOR_MAP['default']);
        const genName = generationNames[node.generation] ? '（' + generationNames[node.generation] + '）' : '';

        let spouseHtml = '';
        const hasSpouses = (node.spouses && node.spouses.length > 0) || (node.bloodSpouses && node.bloodSpouses.length > 0) || (node.formerSpouses && node.formerSpouses.length > 0);
        if (hasSpouses) {
            spouseHtml = '<div class="detail-section" style="margin-top:14px;"><label style="font-weight:600;display:block;margin-bottom:6px;">配偶关系</label>';
            const renderSpouseRow = function (sp, isBlood, isFormer) {
                // 已故节点不展示婚姻状态（在婚/已离异），配偶名后留空；
                // 在婚（含再婚）与离异配偶用不同底色卡片 + 徽章区分，一眼可辨。
                // 离异配偶引用（isFormer：改嫁/再婚至他处）始终展示"已离异"徽章，不受节点已故影响。
                let rowCls = 'spouse-row';
                let badgeHtml = '';
                if (!deceased || isFormer) {
                    if (sp.divorced) {
                        rowCls += ' spouse-row--divorced';
                        badgeHtml = '<span class="spouse-badge spouse-badge--divorced">已离异</span>' +
                            (sp.divorceDate ? '<span class="spouse-date">（' + FT.escapeHtml(sp.divorceDate) + '）</span>' : '');
                    } else if (sp.widowed) {
                        rowCls += ' spouse-row--widowed';
                        badgeHtml = '<span class="spouse-badge spouse-badge--widowed">丧偶</span>';
                    } else {
                        rowCls += ' spouse-row--current';
                        badgeHtml = '<span class="spouse-badge spouse-badge--current">在婚</span>';
                    }
                }
                const bloodLabel = (isBlood && sp.bloodRelationLabel) ? sp.bloodRelationLabel : '血亲';
                const bloodTag = isBlood ? '<span class="spouse-blood-tag">（' + FT.escapeHtml(bloodLabel) + '·各留本支）</span>' : '';
                // 参数经 data-* 属性传递（escapeAttr 转义），避免 inline onclick 拼接姓名导致 JS 注入
                return '<div class="' + rowCls + '">' +
                    '<span class="spouse-info">' +
                    '<span class="spouse-name">' + FT.escapeHtml(sp.name) + '</span>' +
                    bloodTag + badgeHtml +
                    '</span>' +
                    '<button class="btn-sm js-divorce-btn" data-relation-id="' + FT.escapeAttr(sp.relationId) +
                    '" data-marriage="' + FT.escapeAttr(sp.marriageDate || '') +
                    '" data-divorce="' + FT.escapeAttr(sp.divorceDate || '') +
                    '" data-widowed="' + (sp.widowed ? '1' : '') +
                    '" data-name="' + FT.escapeAttr(sp.name) + '">婚姻设置</button>' +
                    '</div>';
            };
            (node.spouses || []).forEach(function (sp) { spouseHtml += renderSpouseRow(sp, false, false); });
            (node.bloodSpouses || []).forEach(function (sp) { spouseHtml += renderSpouseRow(sp, true, false); });
            (node.formerSpouses || []).forEach(function (sp) { spouseHtml += renderSpouseRow(sp, false, true); });
            spouseHtml += '</div>';
        }

        showModal(
            '<h3 style="display:flex;align-items:center;gap:10px;">' +
            '<span style="display:inline-block;width:14px;height:14px;border-radius:50%;background:' + color + ';"></span>' +
            FT.escapeHtml(node.name) + ' <small style="color:#999;font-weight:normal;">第' + node.generation + '世' + FT.escapeHtml(genName) + '</small></h3>' +
            (isSenior ? buildOfferingHtml() + offeringPanelShell() : '') +
            '<div style="margin:12px 0;">' +
            '<p style="margin:4px 0;color:#666;font-size:14px;">性别：' + genderText + '</p>' +
            '<p style="margin:4px 0;color:#666;font-size:14px;">出生：' + FT.escapeHtml(node.birthDate || '未知') + '</p>' +
            (deceased ? '<p style="margin:4px 0;color:#666;font-size:14px;">去世：' + FT.escapeHtml(node.deathDate) + '</p>' : '') +
            (node.birthOrder != null ? '<p style="margin:4px 0;color:#666;font-size:14px;">排次：' + FT.birthOrderText(node.birthOrder) + '</p>' : '') +
            (node.remark ? '<p style="margin:4px 0;color:#666;font-size:14px;">备注：' + FT.escapeHtml(node.remark) + '</p>' : '') +
            '</div>' +
            spouseHtml +
            '<div style="margin-top:16px;padding-top:14px;border-top:1px solid #eee;display:flex;gap:10px;">' +
            '<button class="btn-sm js-death-btn" data-node-id="' + FT.escapeAttr(node.id) +
            '" data-death="' + FT.escapeAttr(node.deathDate || '') +
            '" data-name="' + FT.escapeAttr(node.name) + '">设置去世日期</button>' +
            '</div>' +
            '<div class="modal-actions" style="margin-top:20px;">' +
            '<button class="btn-cancel" onclick="window._closeModal()">关闭</button></div>'
        );

        // 事件委托：从 data-* 读取参数（dataset 自动反转义），杜绝 inline onclick 注入
        document.querySelectorAll('.js-divorce-btn').forEach(function (btn) {
            btn.addEventListener('click', function () {
                window._showDivorceModal(parseInt(btn.dataset.relationId, 10),
                    btn.dataset.marriage, btn.dataset.divorce, btn.dataset.widowed, btn.dataset.name);
            });
        });
        const deathBtn = document.querySelector('.js-death-btn');
        if (deathBtn) {
            deathBtn.addEventListener('click', function () {
                window._showDeathModal(parseInt(deathBtn.dataset.nodeId, 10),
                    deathBtn.dataset.death, deathBtn.dataset.name);
            });
        }

        // 已故长辈：异步加载祭奠统计面板（上香烛 / 烧纸）
        if (isSenior) {
            loadOfferingPanel(node.id);
        }
    }

    // 去世日期设置模态框
    window._showDeathModal = function (nodeId, currentDeathDate, nodeName) {
        showModal(
            '<h3>设置去世日期 - ' + FT.escapeHtml(nodeName) + '</h3>' +
            '<div class="form-group"><label>去世日期</label>' +
            '<input type="date" id="death-date" value="' + FT.escapeAttr(currentDeathDate || '') + '"></div>' +
            '<div class="modal-actions">' +
            '<button class="btn-cancel" onclick="window._closeModal()">取消</button>' +
            (currentDeathDate ? '<button class="btn-sm danger" id="death-clear">清除</button>' : '') +
            '<button class="btn-confirm" id="death-save">保存</button></div>'
        );

        document.getElementById('death-save').addEventListener('click', async function () {
            const deathDate = document.getElementById('death-date').value;
            const res = await FT.api('/api/node', {
                method: 'PUT',
                body: JSON.stringify({id: nodeId, deathDate: deathDate})
            });
            if (res.code === 200) {
                closeModal();
                await FT.loadTree();
            } else {
                alert(res.message || '保存失败');
            }
        });

        const clearBtn = document.getElementById('death-clear');
        if (clearBtn) {
            clearBtn.addEventListener('click', async function () {
                const res = await FT.api('/api/node', {
                    method: 'PUT',
                    body: JSON.stringify({id: nodeId, deathDate: ''})
                });
                if (res.code === 200) {
                    closeModal();
                    await FT.loadTree();
                } else {
                    alert(res.message || '操作失败');
                }
            });
        }
    };

    // 婚姻设置模态框（结婚日期、离异日期均为非必填；支持离异/丧偶状态切换）
    window._showDivorceModal = function (relationId, currentMarriageDate, currentDivorceDate, currentWidowed, spouseName) {
        showModal(
            '<h3>婚姻设置 - ' + FT.escapeHtml(spouseName) + '</h3>' +
            '<div class="form-group"><label>结婚日期（选填）</label>' +
            '<input type="date" id="marriage-date" value="' + FT.escapeAttr(currentMarriageDate || '') + '"></div>' +
            '<div class="form-group"><label>离异日期（选填）</label>' +
            '<input type="date" id="divorce-date" value="' + FT.escapeAttr(currentDivorceDate || '') + '"></div>' +
            '<p style="font-size:13px;color:#999;margin-bottom:12px;">日期均为选填。「标记离异」可不填日期直接标记；「标记丧偶」表示配偶一方已去世；「恢复在婚」将清除离异和丧偶状态。</p>' +
            '<div class="modal-actions">' +
            '<button class="btn-cancel" onclick="window._closeModal()">取消</button>' +
            '<button class="btn-sm danger" id="divorce-clear">恢复在婚</button>' +
            '<button class="btn-sm" id="widowed-save" style="background:#6d597a;border-color:#6d597a;">标记丧偶</button>' +
            '<button class="btn-confirm" id="divorce-save">标记离异</button></div>'
        );

        // 标记离异：日期非必填，同时清除丧偶状态
        document.getElementById('divorce-save').addEventListener('click', async function () {
            const marriageDate = document.getElementById('marriage-date').value || null;
            const divorceDate = document.getElementById('divorce-date').value || null;
            const res = await FT.api('/api/relation', {
                method: 'PUT',
                body: JSON.stringify({id: relationId, divorced: true, widowed: false, marriageDate: marriageDate, divorceDate: divorceDate})
            });
            if (res.code === 200) {
                closeModal();
                await FT.loadTree();
            } else {
                alert(res.message || '操作失败');
            }
        });

        // 标记丧偶：配偶一方去世，同时清除离异状态
        document.getElementById('widowed-save').addEventListener('click', async function () {
            const marriageDate = document.getElementById('marriage-date').value || null;
            const res = await FT.api('/api/relation', {
                method: 'PUT',
                body: JSON.stringify({id: relationId, widowed: true, divorced: false, marriageDate: marriageDate, divorceDate: null})
            });
            if (res.code === 200) {
                closeModal();
                await FT.loadTree();
            } else {
                alert(res.message || '操作失败');
            }
        });

        // 恢复在婚：清除离异和丧偶标记，保留结婚日期
        document.getElementById('divorce-clear').addEventListener('click', async function () {
            const marriageDate = document.getElementById('marriage-date').value || null;
            const res = await FT.api('/api/relation', {
                method: 'PUT',
                body: JSON.stringify({id: relationId, divorced: false, widowed: false, marriageDate: marriageDate, divorceDate: null})
            });
            if (res.code === 200) {
                closeModal();
                await FT.loadTree();
            } else {
                alert(res.message || '操作失败');
            }
        });
    };

    // ========== 颜色标注与删除 ==========
    function showColorModal(nodeId) {
        const opts = FT.buildColorOptions();

        showModal(
            '<h3>修改颜色标注</h3>' +
            '<div class="form-group"><label>选择颜色</label><select id="modal-color">' + opts + '</select></div>' +
            '<div class="modal-actions">' +
            '<button class="btn-cancel" onclick="window._closeModal()">取消</button>' +
            '<button class="btn-confirm" id="modal-submit">确定</button></div>'
        );

        document.getElementById('modal-submit').addEventListener('click', async function () {
            const colorLabel = document.getElementById('modal-color').value;
            const res = await FT.api('/api/node/color', {
                method: 'PUT',
                body: JSON.stringify({nodeIds: [nodeId], colorLabel: colorLabel})
            });
            if (res.code === 200) {
                closeModal();
                await FT.loadTree();
            } else {
                alert(res.message || '操作失败');
            }
        });
    }

    async function deleteNode(nodeId) {
        if (!confirm('确定删除该节点？关联的关系也会一并删除。')) return;
        const res = await FT.api('/api/node/' + nodeId, {method: 'DELETE'});
        if (res.code === 200) {
            await FT.loadTree();
        } else {
            alert(res.message || '删除失败');
        }
    }

    FT.showModal = showModal;
    FT.closeModal = closeModal;
    FT.showNodeModal = showNodeModal;
    FT.showLinkSpouseModal = showLinkSpouseModal;
    FT.editNode = editNode;
    FT.showBirthOrderModal = showBirthOrderModal;
    FT.showGenerationModal = showGenerationModal;
    FT.showDetailModal = showDetailModal;
    FT.showColorModal = showColorModal;
    FT.deleteNode = deleteNode;
})();
