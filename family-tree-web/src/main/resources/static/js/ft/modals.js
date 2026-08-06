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
        const sizeClass = typeof wide === 'string' ? wide : (wide ? 'modal-wide' : '');
        const closeBtn = '<button class="modal-close" onclick="window._closeModal()" title="关闭">&times;</button>';
        const modalContainer = document.getElementById('modal-container');
        modalContainer.innerHTML = '<div class="modal-overlay"><div class="modal' + (sizeClass ? ' ' + sizeClass : '') + '">' + closeBtn + html + '</div></div>';
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
            if (!name) { FT.toast('请输入姓名', 'warning'); return; }

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
                FT.toast(res.message || '操作失败');
            }
        });
    }

    // 关联现有成员为配偶（适用于表兄妹等近亲结婚：双方均已在族谱中有各自分支）
    async function showLinkSpouseModal(nodeId) {
        const nodeRes = await FT.api('/api/node/' + nodeId);
        if (nodeRes.code !== 200) { FT.toast(nodeRes.message || '节点不存在'); return; }
        const currentNode = nodeRes.data;

        const listRes = await FT.api('/api/node/list');
        if (listRes.code !== 200) { FT.toast(listRes.message || '加载成员列表失败'); return; }
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
            if (!selectedId) { FT.toast('请选择一位成员', 'warning'); return; }
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
                FT.toast(res.message || '关联失败');
            }
        });
    }

    // ========== 节点编辑 ==========
    // 编辑节点基本信息（不含去世日期）
    async function editNode(nodeId) {
        const res = await FT.api('/api/node/' + nodeId);
        if (res.code !== 200) { FT.toast(res.message); return; }
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
            '<div class="form-group"><label>农历出生</label><input type="text" id="modal-lunar-birth" value="' + FT.escapeAttr(node.lunarBirthDate || '') + '" placeholder="如：甲子年正月初一"></div>' +
            '<div class="form-group"><label>农历忌日</label><input type="text" id="modal-lunar-death" value="' + FT.escapeAttr(node.lunarDeathDate || '') + '" placeholder="如：丙寅年三月十五"></div>' +
            '<div class="form-group"><label>字</label><input type="text" id="modal-zi" value="' + FT.escapeAttr(node.zi || '') + '" placeholder="表字"></div>' +
            '<div class="form-group"><label>号</label><input type="text" id="modal-hao" value="' + FT.escapeAttr(node.hao || '') + '" placeholder="别号"></div>' +
            '<div class="form-group"><label>讳</label><input type="text" id="modal-hui" value="' + FT.escapeAttr(node.hui || '') + '" placeholder="讳名"></div>' +
            '<div class="form-group"><label>墓地位置</label><input type="text" id="modal-grave" value="' + FT.escapeAttr(node.graveLocation || '') + '" placeholder="选填"></div>' +
            '<div class="form-group"><label>配偶姓名</label><input type="text" id="modal-spouse-name" value="' + FT.escapeAttr(node.spouseName || '') + '" placeholder="外嫁女记录"></div>' +
            '<div class="form-group"><label>配偶原家族</label><input type="text" id="modal-spouse-origin" value="' + FT.escapeAttr(node.spouseOriginFamily || '') + '" placeholder="外嫁女记录"></div>' +
            '<div class="form-group"><label>排次</label><input type="number" id="modal-order" min="1" value="' + (node.birthOrder != null ? node.birthOrder : '') + '" placeholder="未设置"></div>' +
            '<div class="form-group"><label>辈分（第几世）</label><select id="modal-generation">' + genOpts + '</select></div>' +
            '<div class="form-group"><label>颜色标注</label><select id="modal-color">' + colorOpts + '</select></div>' +
            '<div class="form-group"><label>备注</label><input type="text" id="modal-remark" value="' + FT.escapeAttr(node.remark || '') + '"></div>' +
            '<div class="modal-actions">' +
            '<button class="btn-cancel" onclick="window._closeModal()">取消</button>' +
            '<button class="btn-confirm" id="modal-submit">保存</button></div>'
        , true);

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
                remark: document.getElementById('modal-remark').value || null,
                lunarBirthDate: document.getElementById('modal-lunar-birth').value || null,
                lunarDeathDate: document.getElementById('modal-lunar-death').value || null,
                zi: document.getElementById('modal-zi').value || null,
                hao: document.getElementById('modal-hao').value || null,
                hui: document.getElementById('modal-hui').value || null,
                graveLocation: document.getElementById('modal-grave').value || null,
                spouseName: document.getElementById('modal-spouse-name').value || null,
                spouseOriginFamily: document.getElementById('modal-spouse-origin').value || null
            };

            const res = await FT.api('/api/node', {method: 'PUT', body: JSON.stringify(body)});
            if (res.code === 200) {
                closeModal();
                await FT.loadTree();
            } else {
                FT.toast(res.message || '保存失败');
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
                    FT.toast(res.message || '保存失败');
                    return;
                }
            }
            closeModal();
            await FT.loadTree();
        });
    }

    // ========== 辈分管理 ==========
    // 辈分管理弹窗：支持自定义行列矩阵，初始从后端加载，可加行加列，上限 100 世
    function showGenerationModal() {
        const counts = FT.collectGenerationCounts(FT.state.treeData, {});
        const generationNames = FT.state.generationNames;
        const MAX_TOTAL = 100;

        // 从 state 读取当前布局
        let genCols = FT.state.generationCols || 5;
        let genRows = FT.state.generationRows || 5;

        // 计算已配置辈分名的最大世代号，确保矩阵能覆盖
        let maxConfigured = 0;
        for (let g = 1; g <= MAX_TOTAL; g++) {
            if (generationNames[g]) { maxConfigured = g; }
        }
        // 若已配置辈分超出当前矩阵范围，自动扩展行数
        while (genCols * genRows < maxConfigured && genCols * genRows < MAX_TOTAL) {
            genRows++;
        }

        function buildContentHtml() {
            const total = Math.min(genCols * genRows, MAX_TOTAL);
            let cells = '';
            for (let g = 1; g <= total; g++) {
                const count = counts[g] || 0;
                const hasName = !!generationNames[g];
                cells += '<div class="gen-cell' + (hasName ? ' gen-cell-configured' : '') + '">' +
                    '<div class="gen-cell-head"><span>第' + g + '世</span>' +
                    (count > 0 ? '<span class="gen-count">' + count + '人</span>' : '') +
                    '</div>' +
                    '<input type="text" class="gen-input" data-gen="' + g + '" maxlength="10" ' +
                    'value="' + FT.escapeAttr(generationNames[g] || '') + '" placeholder="辈分名">' +
                    '</div>';
            }

            const totalCount = Object.values(counts).reduce(function (s, v) { return s + v; }, 0);
            const canAddRow = (genCols * (genRows + 1)) <= MAX_TOTAL;
            const canAddCol = ((genCols + 1) * genRows) <= MAX_TOTAL;

            return '<h3 style="margin-bottom:8px;">辈分管理 <span style="font-weight:normal;font-size:13px;color:#888;">共 ' + totalCount + ' 人</span></h3>' +
                '<div class="gen-toolbar">' +
                '<span class="gen-matrix-info">' + genCols + ' 列 × ' + genRows + ' 行（' + total + ' 世）</span>' +
                '<div class="gen-toolbar-btns">' +
                '<button class="btn-sm" id="gen-add-row" ' + (canAddRow ? '' : 'disabled title="已达上限"') + '>+ 加一行</button>' +
                '<button class="btn-sm" id="gen-add-col" ' + (canAddCol ? '' : 'disabled title="已达上限"') + '>+ 加一列</button>' +
                '</div>' +
                '</div>' +
                '<div class="gen-grid" style="grid-template-columns:repeat(' + genCols + ',1fr);">' + cells + '</div>' +
                '<div class="modal-actions" style="margin-top:10px;">' +
                '<button class="btn-cancel" onclick="window._closeModal()">取消</button>' +
                '<button class="btn-confirm" id="gen-save">保存</button></div>';
        }

        // 首次用 showModal 打开弹框
        showModal(buildContentHtml(), true);

        function bindEvents() {
            // 加一行
            var addRowBtn = document.getElementById('gen-add-row');
            if (addRowBtn) {
                addRowBtn.addEventListener('click', function () {
                    saveCurrentInputs();
                    genRows++;
                    refreshGrid();
                });
            }
            // 加一列
            var addColBtn = document.getElementById('gen-add-col');
            if (addColBtn) {
                addColBtn.addEventListener('click', function () {
                    saveCurrentInputs();
                    genCols++;
                    refreshGrid();
                });
            }
            // 保存
            var saveBtn = document.getElementById('gen-save');
            if (saveBtn) {
                saveBtn.addEventListener('click', async function () {
                    var total = Math.min(genCols * genRows, MAX_TOTAL);
                    var payload = [];
                    // 收集当前矩阵内的输入
                    for (var g = 1; g <= total; g++) {
                        var input = document.querySelector('.gen-input[data-gen="' + g + '"]');
                        payload.push({generation: g, name: input ? input.value.trim() : ''});
                    }
                    // 超出当前矩阵但已配置的辈分也要提交（防止丢失）
                    for (var g2 = total + 1; g2 <= MAX_TOTAL; g2++) {
                        if (generationNames[g2]) {
                            payload.push({generation: g2, name: generationNames[g2]});
                        }
                    }
                    // 保存辈分名
                    var res = await FT.api('/api/generation', {
                        method: 'PUT',
                        body: JSON.stringify(payload)
                    });
                    if (res.code !== 200) {
                        FT.toast(res.message || '保存失败');
                        return;
                    }
                    // 保存行列布局
                    await FT.api('/api/generation/layout', {
                        method: 'PUT',
                        body: JSON.stringify({cols: genCols, rows: genRows})
                    });
                    // 更新 state
                    FT.state.generationCols = genCols;
                    FT.state.generationRows = genRows;
                    closeModal();
                    await FT.loadTree();
                });
            }
        }

        // 保存当前输入值到 generationNames（临时，用于矩阵重排时保留）
        function saveCurrentInputs() {
            document.querySelectorAll('.gen-input[data-gen]').forEach(function (inp) {
                var g = parseInt(inp.getAttribute('data-gen'), 10);
                generationNames[g] = inp.value.trim();
            });
        }

        // 刷新网格内容（加行/加列后调用）
        function refreshGrid() {
            var modal = document.querySelector('.modal');
            if (!modal) return;
            modal.innerHTML = buildContentHtml();
            bindEvents();
        }

        bindEvents();
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

    // 依据后端统计渲染祭奠面板：敬献（香烛+烧纸合一）/ 送鲜花 按钮 + 各自的总次数
    function buildOfferingPanelHtml(nodeId, stats) {
        const escId = FT.escapeAttr(nodeId);
        let html = '<div class="offering-actions">' +
            '<button class="offering-btn offering-btn--incense js-offering-btn" data-node-id="' + escId + '" data-type="4">敬献</button>' +
            '<button class="offering-btn offering-btn--flower js-offering-btn" data-node-id="' + escId + '" data-type="3">送鲜花</button>' +
            '</div>';
        const statClassMap = {3: 'flower', 4: 'worship'};
        const emptyTextMap = {3: '暂无人送鲜花', 4: '暂无人敬献'};
        html += '<div class="offering-stats">';
        (stats || []).forEach(function (stat) {
            if (stat.offeringType === 1 || stat.offeringType === 2) return;
            const cssClass = statClassMap[stat.offeringType] || 'incense';
            const emptyText = emptyTextMap[stat.offeringType] || '暂无记录';
            const countText = stat.totalCount > 0 ? stat.totalCount + ' 次' : emptyText;
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
        const scene = document.querySelector('.incense-scene');
        if (!scene) {
            return;
        }
        const fx = document.createElement('div');
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
                const marriageDateHtml = sp.marriageDate ? '<span class="spouse-date" style="margin-left:4px;color:#8a7f6a;font-size:12px;">' + FT.escapeHtml(sp.marriageDate.substring(0, 4)) + '年婚</span>' : '';
                // 参数经 data-* 属性传递（escapeAttr 转义），避免 inline onclick 拼接姓名导致 JS 注入
                return '<div class="' + rowCls + '">' +
                    '<span class="spouse-info">' +
                    '<span class="spouse-name">' + FT.escapeHtml(sp.name) + '</span>' +
                    bloodTag + badgeHtml + marriageDateHtml +
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
            (node.lunarBirthDate ? '<p style="margin:4px 0;color:#666;font-size:14px;">农历出生：' + FT.escapeHtml(node.lunarBirthDate) + '</p>' : '') +
            (node.lunarDeathDate ? '<p style="margin:4px 0;color:#666;font-size:14px;">农历忌日：' + FT.escapeHtml(node.lunarDeathDate) + '</p>' : '') +
            (node.zi ? '<p style="margin:4px 0;color:#666;font-size:14px;">字：' + FT.escapeHtml(node.zi) + '</p>' : '') +
            (node.hao ? '<p style="margin:4px 0;color:#666;font-size:14px;">号：' + FT.escapeHtml(node.hao) + '</p>' : '') +
            (node.hui ? '<p style="margin:4px 0;color:#666;font-size:14px;">讳：' + FT.escapeHtml(node.hui) + '</p>' : '') +
            (node.graveLocation ? '<p style="margin:4px 0;color:#666;font-size:14px;">墓地：' + FT.escapeHtml(node.graveLocation) + '</p>' : '') +
            (node.spouseName ? '<p style="margin:4px 0;color:#666;font-size:14px;">配偶姓名：' + FT.escapeHtml(node.spouseName) + '</p>' : '') +
            (node.spouseOriginFamily ? '<p style="margin:4px 0;color:#666;font-size:14px;">配偶原家族：' + FT.escapeHtml(node.spouseOriginFamily) + '</p>' : '') +
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
        , true);

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
                FT.toast(res.message || '保存失败');
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
                    FT.toast(res.message || '操作失败');
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
            '<div class="modal-actions" style="position:relative;">' +
            '<button class="btn-cancel" onclick="window._closeModal()">取消</button>' +
            '<button class="btn-confirm" id="date-only-save" style="background:#4a7c59;border-color:#4a7c59;">保存日期</button>' +
            '<div style="position:relative;display:inline-block;margin-left:8px;">' +
            '<button class="btn-sm" id="btn-more-marriage">更多 ▾</button>' +
            '<div class="marriage-more-dropdown" id="marriage-more-dropdown" style="display:none;position:absolute;bottom:100%;right:0;margin-bottom:4px;min-width:120px;background:#faf5e6;border:1px solid #d8cba8;border-radius:6px;box-shadow:0 -4px 12px rgba(0,0,0,0.1);z-index:1000;overflow:hidden;">' +
            '<div class="marriage-more-item" id="divorce-clear" style="padding:8px 16px;font-size:13px;cursor:pointer;color:#a63a2b;border-bottom:1px solid rgba(216,203,168,0.4);">恢复在婚</div>' +
            '<div class="marriage-more-item" id="widowed-save" style="padding:8px 16px;font-size:13px;cursor:pointer;color:#6d597a;border-bottom:1px solid rgba(216,203,168,0.4);">标记丧偶</div>' +
            '<div class="marriage-more-item" id="divorce-save" style="padding:8px 16px;font-size:13px;cursor:pointer;color:#2b2622;">标记离异</div>' +
            '</div></div></div>'
        , 'modal-medium');

        // 更多按钮下拉切换
        var btnMoreMarriage = document.getElementById('btn-more-marriage');
        var marriageDropdown = document.getElementById('marriage-more-dropdown');
        btnMoreMarriage.addEventListener('click', function (e) {
            e.stopPropagation();
            var isVisible = marriageDropdown.style.display !== 'none';
            marriageDropdown.style.display = isVisible ? 'none' : 'block';
        });
        // 点击空白处关闭下拉
        document.addEventListener('click', function (e) {
            if (!btnMoreMarriage.contains(e.target) && !marriageDropdown.contains(e.target)) {
                marriageDropdown.style.display = 'none';
            }
        });
        // 下拉项点击后关闭
        marriageDropdown.querySelectorAll('.marriage-more-item').forEach(function (item) {
            item.addEventListener('click', function () {
                marriageDropdown.style.display = 'none';
            });
            item.addEventListener('mouseenter', function () { this.style.background = '#d8cba8'; });
            item.addEventListener('mouseleave', function () { this.style.background = ''; });
        });

        // 仅保存日期（不改变离异/丧偶状态）
        document.getElementById('date-only-save').addEventListener('click', async function () {
            const marriageDate = document.getElementById('marriage-date').value || null;
            const divorceDate = document.getElementById('divorce-date').value || null;
            const res = await FT.api('/api/relation', {
                method: 'PUT',
                body: JSON.stringify({id: relationId, marriageDate: marriageDate, divorceDate: divorceDate})
            });
            if (res.code === 200) {
                closeModal();
                await FT.loadTree();
            } else {
                FT.toast(res.message || '保存失败');
            }
        });

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
                FT.toast(res.message || '操作失败');
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
                FT.toast(res.message || '操作失败');
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
                FT.toast(res.message || '操作失败');
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
                FT.toast(res.message || '操作失败');
            }
        });
    }

    async function deleteNode(nodeId) {
        if (!confirm('确定删除该节点？关联的关系也会一并删除。')) return;
        const res = await FT.api('/api/node/' + nodeId, {method: 'DELETE'});
        if (res.code === 200) {
            await FT.loadTree();
        } else {
            FT.toast(res.message || '删除失败');
        }
    }

    // ========== 家族管理弹窗 ==========
    async function showFamilyModal() {
        const user = FT.state.currentUser;
        if (!user || !user.hasFamily) {
            window.location.href = '/family-setup.html';
            return;
        }

        const isOwner = user.familyRole === 'OWNER';
        const isAdmin = isOwner || user.familyRole === 'ADMIN';
        let membersHtml = '<p>加载中...</p>';

        // 加载家族详情（含堂号、籍贯）
        let familyInfo = {};
        try {
            const familyRes = await FT.api('/api/family');
            if (familyRes.code === 200 && familyRes.data) {
                familyInfo = familyRes.data;
            }
        } catch (e) { /* ignore */ }

        // 加载成员列表
        try {
            const membersRes = await FT.api('/api/family/members');
            if (membersRes.code === 200 && membersRes.data) {
                if (membersRes.data.length === 0) {
                    membersHtml = '<p>暂无成员</p>';
                } else {
                    membersHtml = '<ul class="family-member-list">';
                    membersRes.data.forEach(function(m) {
                        let roleLabel = '';
                        if (m.role === 'OWNER') { roleLabel = '（族长）'; }
                        else if (m.role === 'ADMIN') { roleLabel = '（管理员）'; }

                        let actionBtns = '';
                        if (isOwner && m.role !== 'OWNER') {
                            if (m.role === 'ADMIN') {
                                actionBtns += ' <button class="btn-role-toggle" data-uid="' + m.userId + '" data-role="MEMBER">取消管理员</button>';
                            } else {
                                actionBtns += ' <button class="btn-role-toggle" data-uid="' + m.userId + '" data-role="ADMIN">设为管理员</button>';
                            }
                            actionBtns += ' <button class="btn-remove-member" data-uid="' + m.userId + '">移除</button>';
                        } else if (isAdmin && !isOwner && m.role === 'MEMBER') {
                            actionBtns += ' <button class="btn-remove-member" data-uid="' + m.userId + '">移除</button>';
                        }
                        membersHtml += '<li>' + (m.nickname || '用户' + m.userId) + roleLabel + actionBtns + '</li>';
                    });
                    membersHtml += '</ul>';
                }
            }
        } catch (e) {
            membersHtml = '<p>加载失败</p>';
        }

        let inviteSection = '';
        if (isAdmin) {
            inviteSection = '<div class="family-invite-section">' +
                '<div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;">' +
                '<label style="margin:0;">邀请码：<strong id="family-invite-code">' + (user.inviteCode || '') + '</strong></label>' +
                '<button id="btn-refresh-invite" class="btn-sm">刷新邀请码</button>' +
                '<button id="btn-gen-invite-link" class="btn-sm">生成邀请链接</button>' +
                '</div>' +
                '<div id="invite-link-box" class="invite-link-box" style="display:none;">' +
                '<input type="text" id="invite-link-input" class="invite-link-input" readonly>' +
                '<button id="btn-copy-invite-link" class="btn-sm">复制</button>' +
                '</div>' +
                '</div>';
        }

        // 堂号/籍贯编辑区（管理员可编辑）
        let hallSection = '';
        if (isAdmin) {
            hallSection = '<div class="family-hall-section" style="margin:12px 0;padding:10px;background:#faf8f2;border:1px solid #e8e0cc;border-radius:6px;">' +
                '<div style="display:flex;gap:16px;flex-wrap:wrap;">' +
                '<div class="form-group" style="flex:1;min-width:160px;margin-bottom:0;"><label style="font-size:13px;">堂号</label>' +
                '<input type="text" id="family-hall-name" value="' + FT.escapeAttr(familyInfo.hallName || '') + '" placeholder="如：三廉堂" style="width:100%;"></div>' +
                '<div class="form-group" style="flex:1;min-width:160px;margin-bottom:0;"><label style="font-size:13px;">籍贯</label>' +
                '<input type="text" id="family-ancestral-home" value="' + FT.escapeAttr(familyInfo.ancestralHome || '') + '" placeholder="如：广西蒙山" style="width:100%;"></div>' +
                '</div>' +
                '<button class="btn-sm" id="btn-save-family-info" style="margin-top:8px;">保存堂号/籍贯</button>' +
                '</div>';
        } else {
            // 非管理员只读展示
            var hallInfo = familyInfo.hallName || familyInfo.ancestralHome;
            if (hallInfo) {
                hallSection = '<div style="margin:8px 0;font-size:13px;color:#666;">' +
                    (familyInfo.hallName ? '堂号：' + FT.escapeHtml(familyInfo.hallName) + ' ' : '') +
                    (familyInfo.ancestralHome ? '籍贯：' + FT.escapeHtml(familyInfo.ancestralHome) : '') +
                    '</div>';
            }
        }

        const bodyHtml = '<h3>家族管理</h3>' +
            '<div class="family-modal-content">' +
            '<div class="family-info-row"><span class="family-name-label">家族名称：</span><strong>' + (user.familyName || '家族') + '</strong></div>' +
            hallSection +
            inviteSection +
            '<h4>已注册成员列表</h4>' +
            membersHtml +
            '</div>' +
            '<div class="modal-actions">' +
            '<button class="btn-cancel" onclick="window._closeModal()">关闭</button></div>';

        showModal(bodyHtml, true);

        // 绑定角色切换事件
        document.querySelectorAll('.btn-role-toggle').forEach(function(btn) {
            btn.addEventListener('click', async function() {
                const uid = this.getAttribute('data-uid');
                const role = this.getAttribute('data-role');
                const actionText = role === 'ADMIN' ? '设为管理员' : '取消管理员';
                if (!confirm('确定' + actionText + '？')) return;
                const res = await FT.api('/api/family/member/role', {
                    method: 'PUT',
                    body: JSON.stringify({userId: parseInt(uid, 10), role: role})
                });
                if (res.code === 200) {
                    closeModal();
                    showFamilyModal();
                } else {
                    FT.toast(res.message || '操作失败');
                }
            });
        });

        // 绑定移除成员事件
        document.querySelectorAll('.btn-remove-member').forEach(function(btn) {
            btn.addEventListener('click', async function() {
                const uid = this.getAttribute('data-uid');
                if (!confirm('确定移除该成员？')) return;
                const res = await FT.api('/api/family/member/' + uid, {method: 'DELETE'});
                if (res.code === 200) {
                    closeModal();
                    showFamilyModal();
                } else {
                    FT.toast(res.message || '操作失败');
                }
            });
        });

        // 绑定刷新邀请码事件
        const refreshBtn = document.getElementById('btn-refresh-invite');
        if (refreshBtn) {
            refreshBtn.addEventListener('click', async function() {
                const res = await FT.api('/api/family/invite-code', {method: 'PUT'});
                if (res.code === 200 && res.data) {
                    document.getElementById('family-invite-code').textContent = res.data.inviteCode;
                    FT.state.currentUser.inviteCode = res.data.inviteCode;
                    // 隐藏已生成的链接（邀请码已变更）
                    var linkBox = document.getElementById('invite-link-box');
                    if (linkBox) { linkBox.style.display = 'none'; }
                } else {
                    FT.toast(res.message || '刷新失败');
                }
            });
        }

        // 绑定生成邀请链接事件
        var genLinkBtn = document.getElementById('btn-gen-invite-link');
        if (genLinkBtn) {
            genLinkBtn.addEventListener('click', function() {
                var code = (FT.state.currentUser.inviteCode || '').toUpperCase();
                if (!code) { FT.toast('邀请码为空，请先刷新'); return; }
                var link = location.origin + '/login.html?invite=' + code;
                var linkInput = document.getElementById('invite-link-input');
                linkInput.value = link;
                document.getElementById('invite-link-box').style.display = 'flex';
                linkInput.select();
            });
        }

        // 绑定复制邀请链接事件
        var copyBtn = document.getElementById('btn-copy-invite-link');
        if (copyBtn) {
            copyBtn.addEventListener('click', function() {
                var linkInput = document.getElementById('invite-link-input');
                linkInput.select();
                if (navigator.clipboard && navigator.clipboard.writeText) {
                    navigator.clipboard.writeText(linkInput.value).then(function() {
                        FT.toast('链接已复制', 'success');
                    });
                } else {
                    document.execCommand('copy');
                    FT.toast('链接已复制', 'success');
                }
            });
        }

        // 绑定保存堂号/籍贯事件
        var saveFamilyInfoBtn = document.getElementById('btn-save-family-info');
        if (saveFamilyInfoBtn) {
            saveFamilyInfoBtn.addEventListener('click', async function() {
                var hallName = document.getElementById('family-hall-name').value.trim() || null;
                var ancestralHome = document.getElementById('family-ancestral-home').value.trim() || null;
                var res = await FT.api('/api/family/info', {
                    method: 'PUT',
                    body: JSON.stringify({hallName: hallName, ancestralHome: ancestralHome})
                });
                if (res.code === 200) {
                    FT.toast('保存成功', 'success');
                } else {
                    FT.toast(res.message || '保存失败');
                }
            });
        }
    }

    // ========== 个人信息维护 ==========
    // 编辑当前登录用户的基本信息：昵称、出生日期、辈分
    async function showProfileModal() {
        const user = FT.state.currentUser;
        if (!user) { return; }

        // 辈分下拉：从 generationNames 构建，补充当前值
        const generationNames = FT.state.generationNames;
        const genKeys = Object.keys(generationNames).map(Number).sort(function (a, b) { return a - b; });
        const currentGen = user.generation;
        if (currentGen != null && genKeys.indexOf(currentGen) === -1) {
            genKeys.push(currentGen);
            genKeys.sort(function (a, b) { return a - b; });
        }
        const genOpts = genKeys.map(function (g) {
            const sel = g === currentGen ? ' selected' : '';
            const label = generationNames[g] ? '第' + g + '世（' + FT.escapeHtml(generationNames[g]) + '）' : '第' + g + '世';
            return '<option value="' + g + '"' + sel + '>' + label + '</option>';
        }).join('');
        const genUnset = currentGen == null ? ' selected' : '';

        showModal(
            '<h3>个人信息</h3>' +
            '<div class="form-group"><label>昵称</label><input type="text" id="profile-nickname" value="' + FT.escapeAttr(user.nickname || '') + '" placeholder="请输入昵称"></div>' +
            '<div class="form-group"><label>出生日期</label><input type="date" id="profile-birth" value="' + FT.escapeAttr(user.birthDate || '') + '"></div>' +
            '<div class="form-group"><label>辈分（第几世）</label><select id="profile-generation"><option value=""' + genUnset + '>未设置</option>' + genOpts + '</select></div>' +
            '<div class="modal-actions">' +
            '<button class="btn-cancel" onclick="window._closeModal()">取消</button>' +
            '<button class="btn-confirm" id="profile-save">保存</button></div>'
        );

        document.getElementById('profile-save').addEventListener('click', async function () {
            const nickname = document.getElementById('profile-nickname').value.trim();
            const birthDate = document.getElementById('profile-birth').value || null;
            const genRaw = document.getElementById('profile-generation').value;

            if (!nickname) { FT.toast('昵称不能为空', 'warning'); return; }

            const body = {
                nickname: nickname,
                birthDate: birthDate,
                generation: genRaw ? parseInt(genRaw) : null
            };

            const res = await FT.api('/api/auth/profile', {method: 'PUT', body: JSON.stringify(body)});
            if (res.code === 200) {
                // 更新客户端缓存
                FT.state.currentUser.nickname = nickname;
                FT.state.currentUser.birthDate = birthDate;
                FT.state.currentUser.generation = body.generation;
                document.getElementById('user-nickname').textContent = nickname;
                closeModal();
            } else {
                FT.toast(res.message || '保存失败');
            }
        });
    }

    // ========== 标记为我 ==========
    // 将右键选中的节点标记为当前登录用户所在位置
    async function markAsSelf(nodeId) {
        const user = FT.state.currentUser;
        if (!user) { return; }

        // 若该节点已标记为"我"，则取消标记
        const newNodeId = (user.nodeId === nodeId) ? null : nodeId;

        const res = await FT.api('/api/auth/my-node', {
            method: 'PUT',
            body: JSON.stringify({nodeId: newNodeId})
        });
        if (res.code === 200) {
            user.nodeId = newNodeId;
            FT.renderTree();
        } else {
            FT.toast(res.message || '标记失败');
        }
    }

    // ========== 过继/收养 ==========
    async function showAdoptionModal(nodeId) {
        const nodeRes = await FT.api('/api/node/' + nodeId);
        if (nodeRes.code !== 200) { FT.toast(nodeRes.message || '节点不存在'); return; }
        const currentNode = nodeRes.data;

        const listRes = await FT.api('/api/node/list');
        if (listRes.code !== 200) { FT.toast(listRes.message || '加载成员列表失败'); return; }
        const allNodes = (listRes.data || []).filter(function (n) { return n.id !== currentNode.id; });

        const genText = function (g) {
            if (g == null) return '';
            const name = FT.state.generationNames[g];
            return '第' + g + '世' + (name ? '·' + FT.escapeHtml(name) : '');
        };

        function buildOptions(keyword) {
            const kw = (keyword || '').trim();
            const html = allNodes
                .filter(function (n) { return !kw || (n.name || '').indexOf(kw) >= 0; })
                .map(function (n) {
                    const meta = [genText(n.generation), n.gender === 1 ? '男' : (n.gender === 2 ? '女' : '')].filter(Boolean).join('·');
                    return '<div class="link-spouse-opt" data-id="' + n.id + '">' +
                        '<span class="opt-name">' + FT.escapeHtml(n.name) + '</span>' +
                        (meta ? '<span class="opt-meta">' + meta + '</span>' : '') +
                        '</div>';
                }).join('');
            return html || '<div class="link-spouse-empty">无匹配成员</div>';
        }

        showModal(
            '<h3>过继/收养</h3>' +
            '<p style="color:#6b6156;font-size:13px;margin-bottom:12px;">为「' + FT.escapeHtml(currentNode.name) + '」建立过继/收养关系</p>' +
            '<div class="form-group"><label>收养方式</label>' +
            '<select id="adoption-mode">' +
            '<option value="existing">关联族谱中已有成员为养子女</option>' +
            '<option value="new">新建养子女</option>' +
            '</select></div>' +
            '<div id="adoption-existing">' +
            '<div class="form-group"><input type="text" id="adoption-search" placeholder="搜索姓名…"></div>' +
            '<div class="link-spouse-list" id="adoption-list">' + buildOptions('') + '</div>' +
            '</div>' +
            '<div id="adoption-new" style="display:none;">' +
            '<div class="form-group"><label>养子女姓名</label><input type="text" id="adoption-child-name" placeholder="请输入姓名"></div>' +
            '<div class="form-group"><label>性别</label><select id="adoption-child-gender"><option value="1">男</option><option value="2">女</option></select></div>' +
            '</div>' +
            '<div class="modal-actions">' +
            '<button class="btn-cancel" onclick="window._closeModal()">取消</button>' +
            '<button class="btn-confirm" id="adoption-submit">确定</button></div>',
            true
        );

        let selectedId = null;

        // 切换收养方式
        document.getElementById('adoption-mode').addEventListener('change', function () {
            const isExisting = this.value === 'existing';
            document.getElementById('adoption-existing').style.display = isExisting ? '' : 'none';
            document.getElementById('adoption-new').style.display = isExisting ? 'none' : '';
        });

        // 搜索
        var searchEl = document.getElementById('adoption-search');
        if (searchEl) {
            searchEl.addEventListener('input', function () {
                document.getElementById('adoption-list').innerHTML = buildOptions(this.value);
                selectedId = null;
            });
        }

        // 选择成员
        var listEl = document.getElementById('adoption-list');
        if (listEl) {
            listEl.addEventListener('click', function (e) {
                var opt = e.target.closest('.link-spouse-opt');
                if (!opt) return;
                listEl.querySelectorAll('.link-spouse-opt').forEach(function (o) { o.classList.remove('selected'); });
                opt.classList.add('selected');
                selectedId = parseInt(opt.getAttribute('data-id'));
            });
        }

        // 提交
        document.getElementById('adoption-submit').addEventListener('click', async function () {
            var mode = document.getElementById('adoption-mode').value;

            if (mode === 'existing') {
                if (!selectedId) { FT.toast('请选择一位成员', 'warning'); return; }
                // from=养父母(nodeId), to=养子女(selectedId), type=3(ADOPTION)
                var res = await FT.api('/api/relation', {
                    method: 'POST',
                    body: JSON.stringify({fromNodeId: nodeId, toNodeId: selectedId, relationType: 3})
                });
                if (res.code === 200) {
                    closeModal();
                    await FT.loadTree();
                } else {
                    FT.toast(res.message || '操作失败');
                }
            } else {
                var childName = document.getElementById('adoption-child-name').value.trim();
                if (!childName) { FT.toast('请输入养子女姓名', 'warning'); return; }
                var gender = parseInt(document.getElementById('adoption-child-gender').value);
                // 先创建新节点
                var createRes = await FT.api('/api/node', {
                    method: 'POST',
                    body: JSON.stringify({
                        name: childName,
                        gender: gender,
                        parentNodeId: nodeId
                    })
                });
                if (createRes.code !== 200) {
                    FT.toast(createRes.message || '创建失败');
                    return;
                }
                var childNodeId = createRes.data.nodeId;
                // 再建立收养关系（type=3）
                var relRes = await FT.api('/api/relation', {
                    method: 'POST',
                    body: JSON.stringify({fromNodeId: nodeId, toNodeId: childNodeId, relationType: 3})
                });
                if (relRes.code === 200) {
                    closeModal();
                    await FT.loadTree();
                } else {
                    FT.toast(relRes.message || '收养关系创建失败');
                }
            }
        });
    }

    FT.showModal = showModal;
    FT.closeModal = closeModal;
    FT.showNodeModal = showNodeModal;
    FT.showLinkSpouseModal = showLinkSpouseModal;
    FT.showAdoptionModal = showAdoptionModal;
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
            '<button class="btn-cancel" onclick="window._closeModal()">关闭</button></div>';

        showModal(bodyHtml, true);

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
        html += '<div class="modal-actions"><button class="btn-cancel" onclick="window._closeModal()">关闭</button></div>';
        showModal(html, true);
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
            '<div class="modal-actions"><button class="btn-cancel" onclick="window._closeModal()">关闭</button></div>';

        showModal(html, true);

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
        html += '<div class="modal-actions"><button class="btn-cancel" onclick="window._closeModal()">关闭</button></div>';
        showModal(html);
    }

    // ========== 家族切换 ==========
    async function showFamilySwitcherModal() {
        var res = await FT.api('/api/family/my-list');
        if (res.code !== 200) { FT.toast(res.message || '加载失败'); return; }
        var families = res.data || [];
        var currentFamilyId = FT.state.currentUser.familyId;

        if (families.length <= 1) {
            FT.toast('您只属于一个家族', 'warning');
            return;
        }

        var html = '<h3 style="margin-bottom:10px;">切换家族</h3>';
        html += '<div style="max-height:300px;overflow-y:auto;">';
        families.forEach(function(f) {
            var isCurrent = f.id === currentFamilyId;
            var roleLabel = f.currentRole === 'OWNER' ? '（族长）' : (f.currentRole === 'ADMIN' ? '（管理员）' : '');
            html += '<div class="family-switch-item' + (isCurrent ? ' current' : '') + '" data-id="' + f.id + '" ' +
                'style="padding:10px;border:1px solid ' + (isCurrent ? '#a63a2b' : '#e0e0e0') + ';border-radius:6px;margin-bottom:8px;cursor:pointer;' +
                (isCurrent ? 'background:#fdf6f0;' : '') + '">' +
                '<strong>' + FT.escapeHtml(f.name) + '</strong>' + roleLabel +
                (isCurrent ? ' <span style="color:#a63a2b;font-size:12px;">← 当前</span>' : '') +
                '</div>';
        });
        html += '</div>';
        html += '<div class="modal-actions"><button class="btn-cancel" onclick="window._closeModal()">关闭</button></div>';
        showModal(html);

        document.querySelectorAll('.family-switch-item').forEach(function(el) {
            el.addEventListener('click', async function() {
                var familyId = parseInt(el.dataset.id);
                if (familyId === currentFamilyId) return;
                var switchRes = await FT.api('/api/family/switch/' + familyId, {method: 'PUT'});
                if (switchRes.code === 200) {
                    FT.toast('已切换家族', 'success');
                    closeModal();
                    location.reload();
                } else {
                    FT.toast(switchRes.message || '切换失败');
                }
            });
        });
    }

    FT.showTimelineModal = showTimelineModal;
    FT.showRelationPathModal = showRelationPathModal;
    FT.showDeathAnniversaryModal = showDeathAnniversaryModal;
    FT.showFamilySwitcherModal = showFamilySwitcherModal;

    FT.showOperationLogModal = showOperationLogModal;

    FT.editNode = editNode;
    FT.showBirthOrderModal = showBirthOrderModal;
    FT.showGenerationModal = showGenerationModal;
    FT.showDetailModal = showDetailModal;
    FT.showColorModal = showColorModal;
    FT.showFamilyModal = showFamilyModal;
    FT.showProfileModal = showProfileModal;
    FT.markAsSelf = markAsSelf;
    FT.deleteNode = deleteNode;
})();
