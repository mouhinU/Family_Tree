/**
 * 族谱前端 - 节点弹窗模块
 * 节点新增/编辑/删除、排次管理、辈分管理、详情弹窗（含配偶婚姻管理）、
 * 颜色标注、过继/收养弹窗。
 *
 * @author Family-Tree
 * @date 2026-08-09
 */
(function () {
    'use strict';

    var FT = window.FT;

    // ========== 节点新增 ==========
    // 新增节点表单（不含去世日期）
    function showNodeModal(title, relationOpts) {
        var colorOpts = FT.buildColorOptions();

        // 排次仅在新增子女时可指定，缺省由后端自动排在末位
        var orderField = relationOpts.parentNodeId
            ? '<div class="form-group"><label>排次</label><input type="number" id="modal-order" min="1" placeholder="自动（排在末位）"></div>'
            : '';

        FT.showModal(
            '<h3>' + title + '</h3>' +
            '<div class="form-group"><label>姓名</label><input type="text" id="modal-name" placeholder="请输入姓名"></div>' +
            '<div class="form-group"><label>性别</label><select id="modal-gender">' +
            '<option value="0">未知</option><option value="1">男</option><option value="2">女</option></select></div>' +
            '<div class="form-group"><label>出生日期</label><input type="date" id="modal-birth"></div>' +
            orderField +
            '<div class="form-group"><label>颜色标注</label><select id="modal-color">' + colorOpts + '</select></div>' +
            '<div class="form-group"><label>备注</label><input type="text" id="modal-remark" placeholder="可选"></div>' +
            '<div class="modal-actions">' +
            '<button class="btn-cancel" data-close-modal>取消</button>' +
            '<button class="btn-confirm" id="modal-submit">确定</button></div>'
        );

        // 添加配偶时按目标节点性别自动预选相反性别：男→女，女→男；未知则保持"未知"
        if (relationOpts.spouseNodeId) {
            var targetNode = FT.findNodeIncludeSpouseById(FT.state.treeData, relationOpts.spouseNodeId);
            if (targetNode) {
                var genderSelect = document.getElementById('modal-gender');
                if (targetNode.gender === 1) {
                    genderSelect.value = '2';
                } else if (targetNode.gender === 2) {
                    genderSelect.value = '1';
                }
            }
        }

        document.getElementById('modal-submit').addEventListener('click', async function () {
            var name = document.getElementById('modal-name').value.trim();
            if (!name) { FT.toast('请输入姓名', 'warning'); return; }

            var orderEl = document.getElementById('modal-order');
            var body = {
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

            var res = await FT.api('/api/node', {method: 'POST', body: JSON.stringify(body)});
            if (res.code === 200) {
                FT.closeModal();
                await FT.loadTree();
            } else {
                FT.toast(res.message || '操作失败');
            }
        });
    }

    // 关联现有成员为配偶（适用于表兄妹等近亲结婚：双方均已在族谱中有各自分支）
    async function showLinkSpouseModal(nodeId) {
        var nodeRes = await FT.api('/api/node/' + nodeId);
        if (nodeRes.code !== 200) { FT.toast(nodeRes.message || '节点不存在'); return; }
        var currentNode = nodeRes.data;

        var listRes = await FT.api('/api/node/list');
        if (listRes.code !== 200) { FT.toast(listRes.message || '加载成员列表失败'); return; }
        var allNodes = (listRes.data || []).filter(function (n) { return n.id !== currentNode.id; });

        var genderText = function (g) { return g === 1 ? '男' : (g === 2 ? '女' : ''); };
        var genText = function (g) {
            if (g == null) { return ''; }
            var name = FT.state.generationNames[g];
            return '第' + g + '世' + (name ? '·' + FT.escapeHtml(name) : '');
        };

        function buildOptions(keyword) {
            var kw = (keyword || '').trim();
            var html = allNodes
                .filter(function (n) { return !kw || (n.name || '').indexOf(kw) >= 0; })
                .map(function (n) {
                    var meta = [genText(n.generation), genderText(n.gender)].filter(Boolean).join('·');
                    return '<div class="link-spouse-opt" data-id="' + n.id + '">' +
                        '<span class="opt-name">' + FT.escapeHtml(n.name) + '</span>' +
                        (meta ? '<span class="opt-meta">' + meta + '</span>' : '') +
                        '</div>';
                }).join('');
            return html || '<div class="link-spouse-empty">无匹配成员</div>';
        }

        FT.showModal(
            '<h3>关联配偶</h3>' +
            '<p class="link-spouse-tip">为「' + FT.escapeHtml(currentNode.name) + '」关联族谱中已有成员为配偶' +
            '（适用于表兄妹等近亲结婚，双方将各自保留在原分支并连线）</p>' +
            '<div class="form-group"><input type="text" id="link-spouse-search" placeholder="搜索姓名…"></div>' +
            '<div class="link-spouse-list" id="link-spouse-list">' + buildOptions('') + '</div>' +
            '<div class="modal-actions">' +
            '<button class="btn-cancel" data-close-modal>取消</button>' +
            '<button class="btn-confirm" id="link-spouse-submit">确定</button></div>'
        );

        var selectedId = null;
        var listEl = document.getElementById('link-spouse-list');
        listEl.addEventListener('click', function (e) {
            var opt = e.target.closest('.link-spouse-opt');
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
            var selected = allNodes.find(function (n) { return n.id === selectedId; });
            // 关系方向约定：from 为男方。任一为男性则以其为 from，否则以当前节点为 from。
            var fromId = currentNode.id, toId = selectedId;
            if (currentNode.gender !== 1 && selected && selected.gender === 1) {
                fromId = selectedId;
                toId = currentNode.id;
            }
            var res = await FT.api('/api/relation', {
                method: 'POST',
                body: JSON.stringify({fromNodeId: fromId, toNodeId: toId, relationType: 2})
            });
            if (res.code === 200) {
                FT.closeModal();
                await FT.loadTree();
            } else {
                FT.toast(res.message || '关联失败');
            }
        });
    }

    // ========== 节点编辑 ==========
    // 编辑节点基本信息（不含去世日期）
    async function editNode(nodeId) {
        var res = await FT.api('/api/node/' + nodeId);
        if (res.code !== 200) { FT.toast(res.message); return; }
        var node = res.data;

        var colorOpts = FT.buildColorOptions(node.colorLabel);

        var genderOpts = [0, 1, 2].map(function (g) {
            var labels = ['未知', '男', '女'];
            var sel = g === node.gender ? ' selected' : '';
            return '<option value="' + g + '"' + sel + '>' + labels[g] + '</option>';
        }).join('');

        // 辈分下拉选项：从辈分管理列表（generationNames）获取，按世代升序排列。
        // 选项文案为"第X世（辈分名）"，未配置辈分名时标注"未配置辈分名"。
        // 若节点当前辈分不在列表中，补充为选项，避免丢失当前值。
        var generationNames = FT.state.generationNames;
        var genKeys = Object.keys(generationNames).map(Number).sort(function (a, b) { return a - b; });
        if (node.generation != null && genKeys.indexOf(node.generation) === -1) {
            genKeys.push(node.generation);
            genKeys.sort(function (a, b) { return a - b; });
        }
        var genOpts = genKeys.map(function (g) {
            var sel = g === node.generation ? ' selected' : '';
            var label = generationNames[g] ? '第' + g + '世（' + FT.escapeHtml(generationNames[g]) + '）' : '第' + g + '世（未配置辈分名）';
            return '<option value="' + g + '"' + sel + '>' + label + '</option>';
        }).join('');

        FT.showModal(
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
            '<button class="btn-cancel" data-close-modal>取消</button>' +
            '<button class="btn-confirm" id="modal-submit">保存</button></div>'
        , true);

        document.getElementById('modal-submit').addEventListener('click', async function () {
            var orderRaw = document.getElementById('modal-order').value;
            var genRaw = document.getElementById('modal-generation').value;
            var body = {
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

            var res = await FT.api('/api/node', {method: 'PUT', body: JSON.stringify(body)});
            if (res.code === 200) {
                FT.closeModal();
                await FT.loadTree();
            } else {
                FT.toast(res.message || '保存失败');
            }
        });
    }

    // ========== 排次管理 ==========
    // 批量管理子女排次
    function showBirthOrderModal(nodeId) {
        var node = FT.findNodeById(FT.state.treeData, nodeId);
        if (!node) { return; }
        var children = (node.children || []).slice();

        if (children.length === 0) {
            FT.showModal('<h3>排次管理</h3>' +
                '<p style="color:#999;padding:12px 0;">该节点暂无子女，无需设置排次。</p>' +
                '<div class="modal-actions"><button class="btn-cancel" data-close-modal>关闭</button></div>');
            return;
        }

        // 按当前排次排序（未设置的按 ID 排在后面）
        children.sort(function (a, b) {
            var ao = a.birthOrder != null ? a.birthOrder : Number.MAX_SAFE_INTEGER;
            var bo = b.birthOrder != null ? b.birthOrder : Number.MAX_SAFE_INTEGER;
            return ao - bo || a.id - b.id;
        });

        var rows = '';
        children.forEach(function (c) {
            var birthYear = c.birthDate ? c.birthDate.substring(0, 4) : '';
            rows += '<div style="display:flex;align-items:center;gap:10px;padding:6px 0;border-bottom:1px solid #f0f0f0;">' +
                '<input type="number" min="1" class="bo-input" data-id="' + c.id + '" value="' + (c.birthOrder != null ? c.birthOrder : '') + '" ' +
                'style="width:64px;padding:4px 6px;border:1px solid #ddd;border-radius:4px;text-align:center;">' +
                '<span style="flex:1;font-size:14px;">' + FT.escapeHtml(c.name) + '</span>' +
                (birthYear ? '<span style="color:#999;font-size:12px;">' + birthYear + '年生</span>' : '') +
                '</div>';
        });

        FT.showModal(
            '<h3>排次管理 - ' + FT.escapeHtml(node.name) + ' 的子女（共' + children.length + '人）</h3>' +
            '<p style="font-size:13px;color:#999;margin-bottom:8px;">为每个子女设置排次，数字小的排在前面，可用下方按钮快速编号。</p>' +
            '<div id="bo-rows" style="max-height:320px;overflow-y:auto;">' + rows + '</div>' +
            '<div style="display:flex;gap:8px;margin-top:12px;">' +
            '<button class="btn-sm" id="bo-by-birth">按出生日期编号</button>' +
            '<button class="btn-sm" id="bo-by-order">按当前顺序编号</button>' +
            '</div>' +
            '<div class="modal-actions">' +
            '<button class="btn-cancel" data-close-modal>取消</button>' +
            '<button class="btn-confirm" id="bo-save">保存</button></div>'
        );

        function assignNumbers(ordered) {
            ordered.forEach(function (c, idx) {
                var input = document.querySelector('.bo-input[data-id="' + c.id + '"]');
                if (input) { input.value = idx + 1; }
            });
        }

        // 按出生日期编号（无出生日期的排在最后）
        document.getElementById('bo-by-birth').addEventListener('click', function () {
            var sorted = children.slice().sort(function (a, b) {
                var ad = a.birthDate || '9999';
                var bd = b.birthDate || '9999';
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
            var updates = [];
            children.forEach(function (c) {
                var input = document.querySelector('.bo-input[data-id="' + c.id + '"]');
                var val = input && input.value !== '' ? parseInt(input.value) : null;
                if (val !== c.birthOrder) {
                    updates.push({id: c.id, birthOrder: val});
                }
            });
            if (updates.length === 0) {
                FT.closeModal();
                return;
            }
            for (var i = 0; i < updates.length; i++) {
                var res = await FT.api('/api/node', {method: 'PUT', body: JSON.stringify(updates[i])});
                if (res.code !== 200) {
                    FT.toast(res.message || '保存失败');
                    return;
                }
            }
            FT.closeModal();
            await FT.loadTree();
        });
    }

    // ========== 辈分管理 ==========
    // 辈分管理弹窗：支持自定义行列矩阵，初始从后端加载，可加行加列，上限 100 世
    function showGenerationModal() {
        var counts = FT.collectGenerationCounts(FT.state.treeData, {});
        var generationNames = FT.state.generationNames;
        var MAX_TOTAL = 100;

        // 从 state 读取当前布局
        var genCols = FT.state.generationCols || 5;
        var genRows = FT.state.generationRows || 5;

        // 计算已配置辈分名的最大世代号，确保矩阵能覆盖
        var maxConfigured = 0;
        for (var g = 1; g <= MAX_TOTAL; g++) {
            if (generationNames[g]) { maxConfigured = g; }
        }
        // 若已配置辈分超出当前矩阵范围，自动扩展行数
        while (genCols * genRows < maxConfigured && genCols * genRows < MAX_TOTAL) {
            genRows++;
        }

        function buildContentHtml() {
            var total = Math.min(genCols * genRows, MAX_TOTAL);
            var cells = '';
            for (var gi = 1; gi <= total; gi++) {
                var count = counts[gi] || 0;
                var hasName = !!generationNames[gi];
                cells += '<div class="gen-cell' + (hasName ? ' gen-cell-configured' : '') + '">' +
                    '<div class="gen-cell-head"><span>第' + gi + '世</span>' +
                    (count > 0 ? '<span class="gen-count">' + count + '人</span>' : '') +
                    '</div>' +
                    '<input type="text" class="gen-input" data-gen="' + gi + '" maxlength="10" ' +
                    'value="' + FT.escapeAttr(generationNames[gi] || '') + '" placeholder="辈分名">' +
                    '</div>';
            }

            var totalCount = Object.values(counts).reduce(function (s, v) { return s + v; }, 0);
            var canAddRow = (genCols * (genRows + 1)) <= MAX_TOTAL;
            var canAddCol = ((genCols + 1) * genRows) <= MAX_TOTAL;

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
                '<button class="btn-cancel" data-close-modal>取消</button>' +
                '<button class="btn-confirm" id="gen-save">保存</button></div>';
        }

        // 首次用 showModal 打开弹框
        FT.showModal(buildContentHtml(), true);

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
                    for (var gi = 1; gi <= total; gi++) {
                        var input = document.querySelector('.gen-input[data-gen="' + gi + '"]');
                        payload.push({generation: gi, name: input ? input.value.trim() : ''});
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
                    FT.closeModal();
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

    // ========== 节点详情 ==========
    // 节点详情（含去世日期编辑 + 配偶离异管理）
    function showDetailModal(node) {
        // 详情渲染使用树加载缓存的数据，此处异步通知后端记录查看日志（不阻塞弹窗，失败静默）
        if (node.id != null) {
            Promise.resolve(FT.api('/api/node/' + node.id)).catch(function () {});
        }
        var generationNames = FT.state.generationNames;
        var genderText = node.gender === 1 ? '男' : node.gender === 2 ? '女' : '未知';
        var deceased = node.deathDate != null && node.deathDate !== '';
        // 香烛拜台仅敬献给「长辈」：节点已故且辈分高于登录人（世代数更小，更靠近始祖）。
        // 登录人或节点辈分缺失时无法论资排辈，不予展示。
        var currentUser = FT.state.currentUser;
        var myGeneration = currentUser && currentUser.generation;
        var isSenior = deceased && myGeneration != null && node.generation != null && node.generation < myGeneration;
        var color = deceased ? FT.DECEASED_COLOR : (FT.COLOR_MAP[node.colorLabel] || FT.COLOR_MAP['default']);
        var genName = generationNames[node.generation] ? '（' + generationNames[node.generation] + '）' : '';

        var spouseHtml = '';
        var hasSpouses = (node.spouses && node.spouses.length > 0) || (node.bloodSpouses && node.bloodSpouses.length > 0) || (node.formerSpouses && node.formerSpouses.length > 0);
        if (hasSpouses) {
            spouseHtml = '<div class="detail-section" style="margin-top:14px;"><label style="font-weight:600;display:block;margin-bottom:6px;">配偶关系</label>';
            var renderSpouseRow = function (sp, isBlood, isFormer) {
                // 已故节点不展示婚姻状态（在婚/已离异），配偶名后留空；
                // 在婚（含再婚）与离异配偶用不同底色卡片 + 徽章区分，一眼可辨。
                // 离异配偶引用（isFormer：改嫁/再婚至他处）始终展示"已离异"徽章，不受节点已故影响。
                var rowCls = 'spouse-row';
                var badgeHtml = '';
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
                var bloodLabel = (isBlood && sp.bloodRelationLabel) ? sp.bloodRelationLabel : '血亲';
                var bloodTag = isBlood ? '<span class="spouse-blood-tag">（' + FT.escapeHtml(bloodLabel) + '·各留本支）</span>' : '';
                var marriageDateHtml = sp.marriageDate ? '<span class="spouse-date" style="margin-left:4px;color:#8a7f6a;font-size:12px;">' + FT.escapeHtml(sp.marriageDate.substring(0, 4)) + '年婚</span>' : '';
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

        FT.showModal(
            '<h3 style="display:flex;align-items:center;gap:10px;">' +
            '<span style="display:inline-block;width:14px;height:14px;border-radius:50%;background:' + color + ';"></span>' +
            FT.escapeHtml(node.name) + ' <small style="color:#999;font-weight:normal;">第' + node.generation + '世' + FT.escapeHtml(genName) + '</small></h3>' +
            (isSenior ? FT.buildOfferingHtml() + FT.offeringPanelShell() : '') +
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
            '<button class="btn-cancel" data-close-modal>关闭</button></div>'
        , true);

        // 事件委托：从 data-* 读取参数（dataset 自动反转义），杜绝 inline onclick 注入
        document.querySelectorAll('.js-divorce-btn').forEach(function (btn) {
            btn.addEventListener('click', function () {
                window._showDivorceModal(parseInt(btn.dataset.relationId, 10),
                    btn.dataset.marriage, btn.dataset.divorce, btn.dataset.widowed, btn.dataset.name);
            });
        });
        var deathBtn = document.querySelector('.js-death-btn');
        if (deathBtn) {
            deathBtn.addEventListener('click', function () {
                window._showDeathModal(parseInt(deathBtn.dataset.nodeId, 10),
                    deathBtn.dataset.death, deathBtn.dataset.name);
            });
        }

        // 已故长辈：异步加载祭奠统计面板（上香烛 / 烧纸）
        if (isSenior) {
            FT.loadOfferingPanel(node.id);
        }
    }

    // 去世日期设置模态框
    window._showDeathModal = function (nodeId, currentDeathDate, nodeName) {
        FT.showModal(
            '<h3>设置去世日期 - ' + FT.escapeHtml(nodeName) + '</h3>' +
            '<div class="form-group"><label>去世日期</label>' +
            '<input type="date" id="death-date" value="' + FT.escapeAttr(currentDeathDate || '') + '"></div>' +
            '<div class="modal-actions">' +
            '<button class="btn-cancel" data-close-modal>取消</button>' +
            (currentDeathDate ? '<button class="btn-sm danger" id="death-clear">清除</button>' : '') +
            '<button class="btn-confirm" id="death-save">保存</button></div>'
        );

        document.getElementById('death-save').addEventListener('click', async function () {
            var deathDate = document.getElementById('death-date').value;
            var res = await FT.api('/api/node', {
                method: 'PUT',
                body: JSON.stringify({id: nodeId, deathDate: deathDate})
            });
            if (res.code === 200) {
                FT.closeModal();
                await FT.loadTree();
            } else {
                FT.toast(res.message || '保存失败');
            }
        });

        var clearBtn = document.getElementById('death-clear');
        if (clearBtn) {
            clearBtn.addEventListener('click', async function () {
                var res = await FT.api('/api/node', {
                    method: 'PUT',
                    body: JSON.stringify({id: nodeId, deathDate: ''})
                });
                if (res.code === 200) {
                    FT.closeModal();
                    await FT.loadTree();
                } else {
                    FT.toast(res.message || '操作失败');
                }
            });
        }
    };

    // 婚姻设置模态框（结婚日期、离异日期均为非必填；支持离异/丧偶状态切换）
    window._showDivorceModal = function (relationId, currentMarriageDate, currentDivorceDate, currentWidowed, spouseName) {
        FT.showModal(
            '<h3>婚姻设置 - ' + FT.escapeHtml(spouseName) + '</h3>' +
            '<div class="form-group"><label>结婚日期（选填）</label>' +
            '<input type="date" id="marriage-date" value="' + FT.escapeAttr(currentMarriageDate || '') + '"></div>' +
            '<div class="form-group"><label>离异日期（选填）</label>' +
            '<input type="date" id="divorce-date" value="' + FT.escapeAttr(currentDivorceDate || '') + '"></div>' +
            '<div class="modal-actions" style="position:relative;">' +
            '<button class="btn-cancel" data-close-modal>取消</button>' +
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
            var marriageDate = document.getElementById('marriage-date').value || null;
            var divorceDate = document.getElementById('divorce-date').value || null;
            var res = await FT.api('/api/relation', {
                method: 'PUT',
                body: JSON.stringify({id: relationId, marriageDate: marriageDate, divorceDate: divorceDate})
            });
            if (res.code === 200) {
                FT.closeModal();
                await FT.loadTree();
            } else {
                FT.toast(res.message || '保存失败');
            }
        });

        // 标记离异：日期非必填，同时清除丧偶状态
        document.getElementById('divorce-save').addEventListener('click', async function () {
            var marriageDate = document.getElementById('marriage-date').value || null;
            var divorceDate = document.getElementById('divorce-date').value || null;
            var res = await FT.api('/api/relation', {
                method: 'PUT',
                body: JSON.stringify({id: relationId, divorced: true, widowed: false, marriageDate: marriageDate, divorceDate: divorceDate})
            });
            if (res.code === 200) {
                FT.closeModal();
                await FT.loadTree();
            } else {
                FT.toast(res.message || '操作失败');
            }
        });

        // 标记丧偶：配偶一方去世，同时清除离异状态
        document.getElementById('widowed-save').addEventListener('click', async function () {
            var marriageDate = document.getElementById('marriage-date').value || null;
            var res = await FT.api('/api/relation', {
                method: 'PUT',
                body: JSON.stringify({id: relationId, widowed: true, divorced: false, marriageDate: marriageDate, divorceDate: null})
            });
            if (res.code === 200) {
                FT.closeModal();
                await FT.loadTree();
            } else {
                FT.toast(res.message || '操作失败');
            }
        });

        // 恢复在婚：清除离异和丧偶标记，保留结婚日期
        document.getElementById('divorce-clear').addEventListener('click', async function () {
            var marriageDate = document.getElementById('marriage-date').value || null;
            var res = await FT.api('/api/relation', {
                method: 'PUT',
                body: JSON.stringify({id: relationId, divorced: false, widowed: false, marriageDate: marriageDate, divorceDate: null})
            });
            if (res.code === 200) {
                FT.closeModal();
                await FT.loadTree();
            } else {
                FT.toast(res.message || '操作失败');
            }
        });
    };

    // ========== 颜色标注与删除 ==========
    function showColorModal(nodeId) {
        var opts = FT.buildColorOptions();

        FT.showModal(
            '<h3>修改颜色标注</h3>' +
            '<div class="form-group"><label>选择颜色</label><select id="modal-color">' + opts + '</select></div>' +
            '<div class="modal-actions">' +
            '<button class="btn-cancel" data-close-modal>取消</button>' +
            '<button class="btn-confirm" id="modal-submit">确定</button></div>'
        );

        document.getElementById('modal-submit').addEventListener('click', async function () {
            var colorLabel = document.getElementById('modal-color').value;
            var res = await FT.api('/api/node/color', {
                method: 'PUT',
                body: JSON.stringify({nodeIds: [nodeId], colorLabel: colorLabel})
            });
            if (res.code === 200) {
                FT.closeModal();
                await FT.loadTree();
            } else {
                FT.toast(res.message || '操作失败');
            }
        });
    }

    async function deleteNode(nodeId) {
        FT.confirm('确定删除该节点？关联的关系也会一并删除。', async function () {
            var res = await FT.api('/api/node/' + nodeId, {method: 'DELETE'});
            if (res.code === 200) {
                await FT.loadTree();
            } else {
                FT.toast(res.message || '删除失败');
            }
        });
    }

    // ========== 过继/收养 ==========
    async function showAdoptionModal(nodeId) {
        var nodeRes = await FT.api('/api/node/' + nodeId);
        if (nodeRes.code !== 200) { FT.toast(nodeRes.message || '节点不存在'); return; }
        var currentNode = nodeRes.data;

        var listRes = await FT.api('/api/node/list');
        if (listRes.code !== 200) { FT.toast(listRes.message || '加载成员列表失败'); return; }
        var allNodes = (listRes.data || []).filter(function (n) { return n.id !== currentNode.id; });

        var genText = function (g) {
            if (g == null) return '';
            var name = FT.state.generationNames[g];
            return '第' + g + '世' + (name ? '·' + FT.escapeHtml(name) : '');
        };

        function buildOptions(keyword) {
            var kw = (keyword || '').trim();
            var html = allNodes
                .filter(function (n) { return !kw || (n.name || '').indexOf(kw) >= 0; })
                .map(function (n) {
                    var meta = [genText(n.generation), n.gender === 1 ? '男' : (n.gender === 2 ? '女' : '')].filter(Boolean).join('·');
                    return '<div class="link-spouse-opt" data-id="' + n.id + '">' +
                        '<span class="opt-name">' + FT.escapeHtml(n.name) + '</span>' +
                        (meta ? '<span class="opt-meta">' + meta + '</span>' : '') +
                        '</div>';
                }).join('');
            return html || '<div class="link-spouse-empty">无匹配成员</div>';
        }

        FT.showModal(
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
            '<button class="btn-cancel" data-close-modal>取消</button>' +
            '<button class="btn-confirm" id="adoption-submit">确定</button></div>',
            true
        );

        var selectedId = null;

        // 切换收养方式
        document.getElementById('adoption-mode').addEventListener('change', function () {
            var isExisting = this.value === 'existing';
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
                    FT.closeModal();
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
                    FT.closeModal();
                    await FT.loadTree();
                } else {
                    FT.toast(relRes.message || '收养关系创建失败');
                }
            }
        });
    }

    FT.showNodeModal = showNodeModal;
    FT.showLinkSpouseModal = showLinkSpouseModal;
    FT.editNode = editNode;
    FT.showBirthOrderModal = showBirthOrderModal;
    FT.showGenerationModal = showGenerationModal;
    FT.showDetailModal = showDetailModal;
    FT.showColorModal = showColorModal;
    FT.deleteNode = deleteNode;
    FT.showAdoptionModal = showAdoptionModal;
})();
