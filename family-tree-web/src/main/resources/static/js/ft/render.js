/**
 * 族谱前端 - 渲染模块
 * SVG 画布与缩放、整树渲染、节点/连线/爱心绘制、辈分水印与圈点高亮、入场卷轴蒙层。
 *
 * @author Family-Tree
 * @date 2026-08-02
 */
(function () {
    'use strict';

    const FT = window.FT;

    const svg = d3.select('#tree-svg');
    const container = document.getElementById('tree-container');

    // ========== 初始化 ==========
    // 入场卷轴蒙层：点击「开启卷轴」后左右两扇向两侧展开，动画结束后移除蒙层
    function setupScrollOverlay() {
        const overlay = document.getElementById('scroll-overlay');
        if (!overlay) {
            return;
        }
        const btn = document.getElementById('btn-open-scroll');
        if (!btn) {
            return;
        }
        btn.addEventListener('click', function () {
            if (overlay.classList.contains('opened')) {
                return;
            }
            overlay.classList.add('opened');
            // 与 CSS 过渡时长（2.5s）保持一致，动画结束后移除蒙层
            setTimeout(function () {
                if (overlay.parentNode) {
                    overlay.parentNode.removeChild(overlay);
                }
            }, 2650);
        });
    }

    // ========== 辈分（字辈）水印层 ==========
    // 辈分水印层：行列布局与辈分管理弹框保持一致
    // 列从右到左（传统阅读方向），列内从上到下（第 1 世在顶部）。
    // 登录人所属辈分（currentUser.generation）对应字符高亮（传统圈点）。
    function buildGenerationWatermark() {
        const wrap = document.getElementById('generation-watermark');
        if (!wrap) {
            return;
        }
        wrap.innerHTML = '';

        // 卷轴轴头（上下端帽）
        ['cap-left-top', 'cap-left-bottom', 'cap-right-top', 'cap-right-bottom'].forEach(function (cls) {
            const cap = document.createElement('div');
            cap.className = 'wm-rod-cap ' + cls;
            wrap.appendChild(cap);
        });

        const modalCols = FT.state.generationCols || 5;
        const modalRows = FT.state.generationRows || 5;
        const totalGenerations = modalCols * modalRows;
        // 水印转置：辈分管理 N行×M列 → 水印 M行×N列
        // 水印列数 = 弹框行数(modalRows)，每列世代数 = 弹框列数(modalCols)
        const wmCols = modalRows;
        const perWmCol = modalCols;
        const currentUser = FT.state.currentUser;
        const myGeneration = currentUser && currentUser.generation ? currentUser.generation : null;

        // CSS flex-direction: row-reverse 使 DOM 第1列显示在最右侧
        for (let c = 0; c < wmCols; c++) {
            const col = document.createElement('div');
            col.className = 'wm-col';
            for (let r = 0; r < perWmCol; r++) {
                const g = c * perWmCol + r + 1;
                if (g > totalGenerations) break;
                const span = document.createElement('span');
                span.className = 'wm-char';
                span.textContent = FT.state.generationNames[g] || '·';
                span.title = '第' + g + '世';
                if (myGeneration === g) {
                    span.classList.add('wm-highlight');
                    span.title = '第' + g + '世 · 点击高亮该辈分节点';
                    span.addEventListener('click', function () {
                        toggleGenerationHighlight(g, span);
                    });
                }
                col.appendChild(span);
            }
            wrap.appendChild(col);
        }
    }

    // 点击辈分圈点字符：切换该辈分节点的高亮
    function toggleGenerationHighlight(generation, spanEl) {
        if (FT.state.highlightedGeneration === generation) {
            FT.state.highlightedGeneration = null;
            if (spanEl) {
                spanEl.classList.remove('wm-active');
            }
        } else {
            FT.state.highlightedGeneration = generation;
            if (spanEl) {
                spanEl.classList.add('wm-active');
            }
        }
        applyGenerationHighlight();
    }

    // 根据 highlightedGeneration 给匹配辈分的节点切换高亮 class
    function applyGenerationHighlight() {
        d3.selectAll('.node-group').each(function () {
            const gen = this.getAttribute('data-generation');
            const match = FT.state.highlightedGeneration != null && gen !== '' && gen !== null
                && parseInt(gen, 10) === FT.state.highlightedGeneration;
            d3.select(this).classed('gen-highlighted', match);
        });
    }

    // ========== D3 渲染 ==========
    function setupSvg() {
        svg.selectAll('*').remove();
        FT.state.gMain = svg.append('g').attr('class', 'main-group');

        FT.state.zoom = d3.zoom()
            .scaleExtent([0.2, 3])
            .on('zoom', function (event) {
                FT.state.gMain.attr('transform', event.transform);
            });
        svg.call(FT.state.zoom);
        svg.on('dblclick.zoom', null);
    }

    function renderTree() {
        stopLineAnimation();
        setupSvg();

        // 「隐藏外嫁」剔除出嫁女家支；「只看健在」不再剔除节点（保留完整树结构维持辈分层级位置），
        // 改为在绘制阶段隐藏已故节点卡片。两开关可叠加。
        let displayTree = FT.state.treeData || [];
        if (FT.state.hideMarryOut) {
            displayTree = FT.buildHideMarryOutTree(displayTree);
        }
        // 「只看健在」子女重挂：已故父/母的子女挂到健在的另一方（配偶或前配偶）
        if (FT.state.hideDeceased) {
            displayTree = FT.buildHideDeceasedReassignTree(displayTree);
        }

        // 清空后代计数记忆化缓存（树结构可能已变化）
        FT.state.descendantMemo = new Map();

        // 为每个根节点包装"未知始祖"虚拟节点，表示上游祖先信息不可考
        var wrappedTree = [];
        displayTree.forEach(function (root, idx) {
            var unknownGen = (root.generation != null && root.generation > 1) ? root.generation - 1 : null;
            var unknownNode = {
                id: '__unknown_' + idx,
                name: '未知始祖',
                isUnknown: true,
                generation: unknownGen,
                children: [root],
                spouses: [],
                bloodSpouses: [],
                formerSpouses: []
            };
            wrappedTree.push(unknownNode);
        });
        displayTree = wrappedTree;

        const gMain = FT.state.gMain;
        if (displayTree.length === 0) {
            gMain.append('text')
                .attr('x', container.clientWidth / 2)
                .attr('y', container.clientHeight / 2)
                .attr('text-anchor', 'middle')
                .attr('fill', '#9a968c')
                .attr('font-size', '16px')
                .text(FT.state.hideDeceased ? '当前没有健在的成员可显示' : '暂无族谱数据，点击右上角「添加始祖」开始');
            return;
        }

        const layoutNodes = [];
        const links = [];
        let crossOffset = 0;

        displayTree.forEach(function (root) {
            crossOffset = FT.layoutSubTree(root, crossOffset, 0, layoutNodes, links).cross;
            crossOffset += FT.H_GAP * 2;
        });

        // 交叉轴总跨度（tb=总宽度，lr/rl=总高度）
        const totalCross = crossOffset;

        // 主轴总跨度（tb=总高度，lr/rl=总宽度）：由最深世代推得
        const horiz = FT.isHorizontal();
        let maxDepth = 0;
        layoutNodes.forEach(function (n) {
            if (n.depth > maxDepth) { maxDepth = n.depth; }
        });
        const mainExtent = horiz ? FT.NODE_WIDTH : FT.NODE_HEIGHT;
        const mainGap = horiz ? 60 : FT.V_GAP;
        const totalMain = maxDepth * (mainExtent + mainGap) + mainExtent;

        // 'rl'：布局按 'lr' 计算（主轴向右展开），再沿主轴镜像 x 坐标，使始祖居最右
        if (FT.state.layoutDirection === 'rl') {
            layoutNodes.forEach(function (n) {
                n.x = totalMain - n.x - FT.NODE_WIDTH;
            });
            links.forEach(function (link) {
                link.x1 = totalMain - link.x1;
                link.x2 = totalMain - link.x2;
            });
        }

        // 屏幕起点：先按交叉轴宽度(totalCross)粗略定位，绘制后再按实际包围盒精确居中
        const treeWidth = horiz ? totalMain : totalCross;
        const startX = (container.clientWidth - treeWidth) / 2;
        const startY = 40;

        drawTreeContent(layoutNodes, links, startX, startY, horiz, true);

        // 初始适配缩放：按屏幕宽度适配（树过宽时保持节点可读尺寸，由用户平移查看）。
        // 缩放会向原点(0,0)收缩，若仍用 translate(0,0) 整棵树会左移，
        // 故按 gMain 实际包围盒计算平移：水平始终居中；垂直放得下则居中，放不下则顶部留白。
        const scale = Math.min(1, container.clientWidth / (treeWidth + 100));
        const fitted = Math.max(scale, 0.5);
        const bbox = gMain.node().getBBox();
        const tx = (container.clientWidth - bbox.width * fitted) / 2 - bbox.x * fitted;
        const scaledHeight = bbox.height * fitted;
        const ty = scaledHeight <= container.clientHeight
            ? (container.clientHeight - scaledHeight) / 2 - bbox.y * fitted
            : 24 - bbox.y * fitted;
        svg.call(FT.state.zoom.transform, d3.zoomIdentity.translate(tx, ty).scale(fitted));

        // 重绘后恢复辈分高亮状态
        applyGenerationHighlight();
    }

    // 判断节点在「只看健在」模式下是否可见
    function isNodeVisible(nodeData) {
        if (!FT.state.hideDeceased) { return true; }
        return !FT.isDeceased(nodeData);
    }

    // 绘制族谱内容（连线 + 血亲配偶弧线 + 节点卡片），追加到全局 gMain。
    // interactive=true（实时视图）：绘制折叠按钮并绑定右键/点击事件；
    // interactive=false（静态导出）：省略折叠按钮与交互，文本/连线内联字体与描边，
    // 以便序列化为独立 SVG（不携带页面 CSS）时样式不丢失。
    function drawTreeContent(layoutNodes, links, startX, startY, horiz, interactive) {
        const gMain = FT.state.gMain;

        // 「只看健在」模式下，已故父节点到子节点的连线不显示
        var processedLinks;
        if (FT.state.hideDeceased) {
            var nodeById = new Map();
            layoutNodes.forEach(function (ln) {
                nodeById.set(ln.data.id, ln.data);
            });
            processedLinks = links.filter(function (link) {
                if (link.type !== 'spouse' && link.fromNodeId != null) {
                    var parentData = nodeById.get(link.fromNodeId);
                    if (parentData && !isNodeVisible(parentData)) {
                        return false;
                    }
                }
                return true;
            });
        } else {
            processedLinks = links.slice();
        }

        // 绘制连线（使用处理后的连线列表）
        processedLinks.forEach(function (link) {
            if (link.type === 'spouse') {
                const midX = (link.x1 + link.x2) / 2 + startX;
                const midY = (link.y1 + link.y2) / 2 + startY;
                const deceased = link.deceased || false;
                const former = link.former || false;

                // 细连线（爱心下方垫底）：前配偶/离异灰虚线；已故墨灰实线；在婚浅红
                var strokeColor = (link.divorced || former) ? '#c4c0b4' : (deceased ? '#a9a499' : '#d8b4ad');
                var dashArray = (link.divorced || former) ? '3,3' : 'none';
                const spouseLine = gMain.append('line')
                    .attr('x1', link.x1 + startX)
                    .attr('y1', link.y1 + startY)
                    .attr('x2', link.x2 + startX)
                    .attr('y2', link.y2 + startY)
                    .attr('stroke', strokeColor)
                    .attr('stroke-width', 1.2)
                    .attr('stroke-dasharray', dashArray)
                    .attr('stroke-opacity', (deceased && !link.divorced && !former) ? 0.7 : 1);

                // 隐形加宽点击区域
                gMain.append('line')
                    .attr('class', 'link-hit-area')
                    .attr('x1', link.x1 + startX)
                    .attr('y1', link.y1 + startY)
                    .attr('x2', link.x2 + startX)
                    .attr('y2', link.y2 + startY)
                    .attr('stroke', 'transparent')
                    .attr('stroke-width', 12)
                    .style('cursor', 'pointer')
                    .on('mouseover', function () {
                        spouseLine.attr('stroke-width', 2.5).attr('stroke-opacity', 1);
                    })
                    .on('mouseout', function () {
                        spouseLine.attr('stroke-width', 1.2).attr('stroke-opacity', (deceased && !link.divorced && !former) ? 0.7 : 1);
                    })
                    .on('click', function (event) {
                        event.stopPropagation();
                        animateSpouseLine(this);
                    });

                // 爱心（24x24 → 缩放到约 16px，居中于连线中点）
                drawHeartMarker(midX, midY, link.divorced || former, deceased);
            } else if (horiz) {
                // 横向布局：水平 S 曲线（控制点取中点 x）
                const midX = (link.x1 + link.x2) / 2;
                const pathD = 'M' + (link.x1 + startX) + ',' + (link.y1 + startY) +
                    ' C' + (midX + startX) + ',' + (link.y1 + startY) +
                    ' ' + (midX + startX) + ',' + (link.y2 + startY) +
                    ' ' + (link.x2 + startX) + ',' + (link.y2 + startY);
                const horizPath = gMain.append('path')
                    .attr('class', 'link-parent')
                    .attr('fill', 'none')
                    .attr('stroke', '#a89877')
                    .attr('stroke-width', 1.6)
                    .attr('d', pathD);
                // 隐形加宽点击区域
                gMain.append('path')
                    .attr('class', 'link-hit-area')
                    .attr('fill', 'none')
                    .attr('stroke', 'transparent')
                    .attr('stroke-width', 12)
                    .attr('d', pathD)
                    .style('cursor', 'pointer')
                    .on('mouseover', function () {
                        horizPath.attr('stroke', '#c2703d').attr('stroke-width', 2.4);
                    })
                    .on('mouseout', function () {
                        horizPath.attr('stroke', '#a89877').attr('stroke-width', 1.6);
                    })
                    .on('click', function (event) {
                        event.stopPropagation();
                        animateParentChildPath(this);
                    });
            } else {
                // 纵向布局：垂直 S 曲线（控制点取中点 y）
                const midY = (link.y1 + link.y2) / 2;
                const pathD = 'M' + (link.x1 + startX) + ',' + (link.y1 + startY) +
                    ' C' + (link.x1 + startX) + ',' + (midY + startY) +
                    ' ' + (link.x2 + startX) + ',' + (midY + startY) +
                    ' ' + (link.x2 + startX) + ',' + (link.y2 + startY);
                const vertPath = gMain.append('path')
                    .attr('class', 'link-parent')
                    .attr('fill', 'none')
                    .attr('stroke', '#a89877')
                    .attr('stroke-width', 1.6)
                    .attr('d', pathD);
                // 隐形加宽点击区域
                gMain.append('path')
                    .attr('class', 'link-hit-area')
                    .attr('fill', 'none')
                    .attr('stroke', 'transparent')
                    .attr('stroke-width', 12)
                    .attr('d', pathD)
                    .style('cursor', 'pointer')
                    .on('mouseover', function () {
                        vertPath.attr('stroke', '#c2703d').attr('stroke-width', 2.4);
                    })
                    .on('mouseout', function () {
                        vertPath.attr('stroke', '#a89877').attr('stroke-width', 1.6);
                    })
                    .on('click', function (event) {
                        event.stopPropagation();
                        animateParentChildPath(this);
                    });
            }
        });

        // 跨分支夫妻连线（血亲配偶，如表兄妹结婚）：
        // 双方各自保留在原生分支，仅在两卡片间画一条弧线 + 爱心，按 relationId 去重。
        // 预建 id→节点 索引，避免每个血亲配偶都线性扫描 layoutNodes。
        const layoutNodeById = new Map();
        layoutNodes.forEach(function (ln) {
            layoutNodeById.set(ln.data.id, ln);
        });

        const drawnBloodRelations = {};
        layoutNodes.forEach(function (n) {
            const bloodSpouses = (n.data && n.data.bloodSpouses) || [];
            bloodSpouses.forEach(function (bs) {
                if (bs.relationId != null) {
                    if (drawnBloodRelations[bs.relationId]) { return; }
                    drawnBloodRelations[bs.relationId] = true;
                }
                const other = layoutNodeById.get(bs.id);
                if (!other) { return; }
                drawBloodSpouseLink(n, other, bs.divorced || false, startX, startY);
            });
        });

        // 绘制节点（古典牌位式：竖排名字）
        layoutNodes.forEach(function (n) {
            const gx = n.x + startX;
            const gy = n.y + startY;
            const deceased = n.data.deathDate != null && n.data.deathDate !== '';
            const visible = isNodeVisible(n.data);

            // 「只看健在」模式下已故节点仅保留布局占位，不绘制卡片与交互元素
            if (!visible) { return; }

            const accent = deceased ? FT.DECEASED_COLOR : (FT.COLOR_MAP[n.data.colorLabel] || FT.COLOR_MAP['default']);
            const paper = deceased ? FT.PAPER_DECEASED : FT.PAPER_COLOR;
            const ink = deceased ? FT.INK_DECEASED : FT.INK_COLOR;

            const group = gMain.append('g')
                .attr('class', 'node-group')
                .attr('transform', 'translate(' + gx + ',' + gy + ')')
                .attr('data-id', n.data.id)
                .attr('data-generation', n.data.generation != null ? n.data.generation : '');

            // 标记当前登录用户所在节点
            const isSelf = FT.state.currentUser && FT.state.currentUser.nodeId === n.data.id;
            if (isSelf) {
                group.classed('node-self', true);
            }

            // 内层视觉容器
            const inner = group.append('g').attr('class', 'node-inner');

            // ===== 未知始祖虚拟节点：虚线卡片 =====
            if (n.data.isUnknown) {
                group.classed('node-unknown', true);

                // 虚线外框
                inner.append('rect')
                    .attr('class', 'node-rect')
                    .attr('width', FT.NODE_WIDTH)
                    .attr('height', FT.NODE_HEIGHT)
                    .attr('rx', 3)
                    .attr('fill', '#f0ebe0')
                    .attr('stroke', '#b8a88a')
                    .attr('stroke-width', 1.5)
                    .attr('stroke-dasharray', '6 3');

                // 虚线内框
                inner.append('rect')
                    .attr('x', 3.5).attr('y', 3.5)
                    .attr('width', FT.NODE_WIDTH - 7)
                    .attr('height', FT.NODE_HEIGHT - 7)
                    .attr('rx', 2)
                    .attr('fill', 'none')
                    .attr('stroke', '#b8a88a')
                    .attr('stroke-width', 0.5)
                    .attr('stroke-dasharray', '4 2')
                    .attr('opacity', 0.5);

                // 辈分标签
                if (n.data.generation != null) {
                    var genLabel = FT.state.generationNames[n.data.generation] || ('第' + n.data.generation + '世');
                    inner.append('text')
                        .attr('x', FT.NODE_WIDTH / 2)
                        .attr('y', 17.5)
                        .attr('text-anchor', 'middle')
                        .attr('font-family', FT.FONT_KAI)
                        .attr('font-size', '9px')
                        .attr('fill', '#a09880')
                        .text(genLabel);
                }

                // 问号图标
                inner.append('text')
                    .attr('x', FT.NODE_WIDTH / 2)
                    .attr('y', FT.NODE_HEIGHT / 2 + 2)
                    .attr('text-anchor', 'middle')
                    .attr('dominant-baseline', 'central')
                    .attr('font-family', FT.FONT_KAI)
                    .attr('font-size', '30px')
                    .attr('fill', '#b8a88a')
                    .attr('opacity', 0.55)
                    .text('?');

                // "未知始祖"文字
                inner.append('text')
                    .attr('x', FT.NODE_WIDTH / 2)
                    .attr('y', FT.NODE_HEIGHT - 24)
                    .attr('text-anchor', 'middle')
                    .attr('font-family', FT.FONT_KAI)
                    .attr('font-size', '10px')
                    .attr('fill', '#a09880')
                    .text('未知始祖');

                // "点击补充"提示
                if (interactive) {
                    inner.append('text')
                        .attr('x', FT.NODE_WIDTH / 2)
                        .attr('y', FT.NODE_HEIGHT - 10)
                        .attr('text-anchor', 'middle')
                        .attr('font-family', FT.FONT_KAI)
                        .attr('font-size', '8px')
                        .attr('fill', '#c4b898')
                        .text('点击补充');
                }

                // 未知节点交互：点击提示
                if (interactive) {
                    group.style('cursor', 'pointer');
                    group.on('click', function (event) {
                        event.stopPropagation();
                        FT.toast('如已知该始祖信息，可在族谱中直接添加其父/母节点', 'info');
                    });
                }
                return; // 跳过后续正常节点绘制
            }

            // 外层卡片
            inner.append('rect')
                .attr('class', 'node-rect')
                .attr('width', FT.NODE_WIDTH)
                .attr('height', FT.NODE_HEIGHT)
                .attr('rx', 3)
                .attr('fill', paper)
                .attr('stroke', accent)
                .attr('stroke-width', 1.6);

            // 内层线框（古典双线框）
            inner.append('rect')
                .attr('x', 3.5).attr('y', 3.5)
                .attr('width', FT.NODE_WIDTH - 7)
                .attr('height', FT.NODE_HEIGHT - 7)
                .attr('rx', 2)
                .attr('fill', 'none')
                .attr('stroke', accent)
                .attr('stroke-width', 0.7)
                .attr('opacity', 0.55);

            // 顶部绶带（强调色）
            inner.append('rect')
                .attr('x', 3.5).attr('y', 3.5)
                .attr('width', FT.NODE_WIDTH - 7)
                .attr('height', 6)
                .attr('rx', 1.5)
                .attr('fill', accent);

            // 辈分标签（绶带下方小字）：名字未必体现辈分，故单独展示。
            // 优先显示辈分管理里配置的辈分名，未配置则回退为"第X世"。
            if (n.data.generation != null) {
                const genLabel = FT.state.generationNames[n.data.generation] || ('第' + n.data.generation + '世');
                inner.append('text')
                    .attr('class', 'node-gen')
                    .attr('x', FT.NODE_WIDTH / 2)
                    .attr('y', 17.5)
                    .attr('text-anchor', 'middle')
                    .attr('font-family', FT.FONT_KAI)
                    .attr('font-size', '9px')
                    .attr('fill', deceased ? '#a39d8f' : '#8a6d3b')
                    .text(genLabel);
            }

            // 名字逐字竖排（居中）
            const chars = Array.from(n.data.name || '');
            const nameAreaTop = 24;
            const nameAreaH = FT.NODE_HEIGHT - 48; // 顶部辈分区 + 底部信息区
            const charH = Math.min(26, Math.floor(nameAreaH / Math.max(chars.length, 1)));
            const fontSize = Math.min(21, charH - 4);
            const nameBlockH = chars.length * charH;
            const nameStartY = nameAreaTop + (nameAreaH - nameBlockH) / 2 + charH / 2;
            chars.forEach(function (ch, i) {
                inner.append('text')
                    .attr('class', 'node-name')
                    .attr('x', FT.NODE_WIDTH / 2)
                    .attr('y', nameStartY + i * charH)
                    .attr('text-anchor', 'middle')
                    .attr('dominant-baseline', 'central')
                    .attr('font-family', FT.FONT_KAI)
                    .attr('font-weight', '600')
                    .attr('font-size', fontSize)
                    .attr('fill', ink)
                    .text(ch);
            });

            // 性别 + 出生年份（底部小字）
            const info = ((n.data.gender === 1 ? '♂' : n.data.gender === 2 ? '♀' : '') +
                (n.data.birthDate ? ' ' + n.data.birthDate.substring(0, 4) : '')).trim();
            if (info) {
                inner.append('text')
                    .attr('class', 'node-info')
                    .attr('x', FT.NODE_WIDTH / 2)
                    .attr('y', FT.NODE_HEIGHT - 11)
                    .attr('text-anchor', 'middle')
                    .attr('font-family', FT.FONT_KAI)
                    .attr('font-size', '10px')
                    .attr('fill', deceased ? '#948f83' : '#8a7f6a')
                    .text(info);
            }

            // 同胞排次徽标（左上角）
            if (n.data.birthOrder != null) {
                const badge = inner.append('g').attr('class', 'birth-order-badge');
                badge.append('circle')
                    .attr('r', 9)
                    .attr('fill', '#c2703d')
                    .attr('stroke', paper)
                    .attr('stroke-width', 1.5);
                badge.append('text')
                    .attr('text-anchor', 'middle')
                    .attr('dy', '3.5px')
                    .attr('font-size', '11px')
                    .attr('font-weight', 'bold')
                    .attr('fill', '#fff')
                    .text(n.data.birthOrder);
            }

            // "我"标记徽章（右上角）：标识当前登录用户在族谱中的位置
            if (isSelf) {
                const selfBadge = inner.append('g').attr('class', 'self-badge');
                selfBadge.append('circle')
                    .attr('cx', FT.NODE_WIDTH - 2)
                    .attr('cy', 2)
                    .attr('r', 10)
                    .attr('fill', '#a63a2b')
                    .attr('stroke', paper)
                    .attr('stroke-width', 1.5);
                selfBadge.append('text')
                    .attr('x', FT.NODE_WIDTH - 2)
                    .attr('y', 2)
                    .attr('text-anchor', 'middle')
                    .attr('dy', '3.5px')
                    .attr('font-size', '10px')
                    .attr('font-weight', 'bold')
                    .attr('fill', '#fff')
                    .text('我');
            }

            // 折叠/展开按钮与交互事件仅实时视图需要，静态导出省略
            if (interactive && n.hasChildren) {
                const isCollapsed = FT.state.collapsedNodes.has(n.data.id);
                let btnTransform;
                if (FT.state.layoutDirection === 'lr') {
                    btnTransform = 'translate(' + FT.NODE_WIDTH + ',' + (FT.NODE_HEIGHT / 2) + ')';
                } else if (FT.state.layoutDirection === 'rl') {
                    btnTransform = 'translate(0,' + (FT.NODE_HEIGHT / 2) + ')';
                } else {
                    btnTransform = 'translate(' + (FT.NODE_WIDTH / 2) + ',' + FT.NODE_HEIGHT + ')';
                }
                const btnGroup = inner.append('g')
                    .attr('class', 'collapse-btn')
                    .attr('transform', btnTransform)
                    .style('cursor', 'pointer');

                btnGroup.append('circle')
                    .attr('r', 9)
                    .attr('fill', '#fff')
                    .attr('stroke', '#999')
                    .attr('stroke-width', 1.5);

                btnGroup.append('text')
                    .attr('text-anchor', 'middle')
                    .attr('dy', '4px')
                    .attr('font-size', '12px')
                    .attr('font-weight', 'bold')
                    .attr('fill', '#666')
                    .text(isCollapsed ? '+' : '−');

                if (isCollapsed && n.childCount > 0) {
                    const countText = btnGroup.append('text')
                        .attr('text-anchor', 'middle')
                        .attr('font-size', '10px')
                        .attr('fill', '#999')
                        .text(n.childCount + '人');
                    if (FT.state.layoutDirection === 'lr') {
                        countText.attr('dx', '18px').attr('dy', '4px');
                    } else if (FT.state.layoutDirection === 'rl') {
                        countText.attr('dx', '-18px').attr('dy', '4px');
                    } else {
                        countText.attr('dy', '22px');
                    }
                }

                btnGroup.on('click', function (event) {
                    event.stopPropagation();
                    FT.toggleCollapse(n.data.id);
                });
            }

            if (interactive) {
                // 右键菜单
                group.on('contextmenu', function (event) {
                    event.preventDefault();
                    event.stopPropagation();
                    FT.state.contextNodeId = n.data.id;
                    FT.showContextMenu(event.clientX, event.clientY);
                });

                // 单击选中（高亮自身 + 配偶），双击打开详情
                var clickTimer = null;
                group.on('click', function (event) {
                    event.stopPropagation();
                    if (clickTimer) {
                        // 第二次点击 → 双击，取消单击定时器，打开详情
                        clearTimeout(clickTimer);
                        clickTimer = null;
                        FT.showDetailModal(n.data);
                    } else {
                        // 第一次点击，等待判断是否有第二次
                        clickTimer = setTimeout(function () {
                            clickTimer = null;
                            selectNode(n.data);
                        }, 250);
                    }
                });
            }
        });
    }

    // 选中节点：高亮自身 + 配偶节点
    function selectNode(nodeData) {
        // 清除之前的选中/配偶高亮
        d3.selectAll('.node-selected').classed('node-selected', false);
        d3.selectAll('.spouse-highlight').classed('spouse-highlight', false);

        // 高亮被点击的节点自身
        var selfEl = document.querySelector('g[data-id="' + nodeData.id + '"]');
        if (selfEl) {
            selfEl.classList.add('node-selected');
        }

        // 收集所有配偶 ID 并高亮
        var spouseIds = [];
        (nodeData.spouses || []).forEach(function (s) { if (s.id != null) spouseIds.push(s.id); });
        (nodeData.formerSpouses || []).forEach(function (s) { if (s.id != null) spouseIds.push(s.id); });
        (nodeData.bloodSpouses || []).forEach(function (s) { if (s.id != null) spouseIds.push(s.id); });

        spouseIds.forEach(function (sid) {
            var el = document.querySelector('g[data-id="' + sid + '"]');
            if (el) {
                el.classList.add('spouse-highlight');
            }
        });
    }

    // ========== 连线点击动画 ==========
    var BASE_DOT_COUNT = 5;     // 基础光点数量
    var DOT_PER_10PX = 1;       // 每 10px 额外增加的光点数
    var STAGGER = 300;          // 光点间隔(ms)
    var PC_DURATION = 800;      // 父子单点流动时长(ms)
    var SP_DURATION = 600;      // 配偶单点流动时长(ms)

    // 当前活跃的连线动画状态：再次点击同一条线取消，点击不同线则先取消再启动
    var activeLineAnim = null;  // { hitEl, cancelled, elements[] }

    // 停止当前活跃的连线动画（移除所有光点元素）
    function stopLineAnimation() {
        if (!activeLineAnim) return;
        activeLineAnim.cancelled = true;
        activeLineAnim.elements.forEach(function (el) { el.remove(); });
        activeLineAnim = null;
    }

    // 根据线段长度计算光点数量：基础5个 + 每10px增加1个
    function calcDotCount(lineLength) {
        return BASE_DOT_COUNT + Math.floor(lineLength / 10) * DOT_PER_10PX;
    }

    // 父子连线点击：光点依次从父端沿贝塞尔路径流向子端（走马灯）
    function animateParentChildPath(hitPathEl) {
        // 再次点击同一条线 → 取消动画
        if (activeLineAnim && activeLineAnim.hitEl === hitPathEl) {
            stopLineAnimation();
            return;
        }
        // 点击不同线 → 先取消上一条线的动画
        stopLineAnimation();

        var gMain = FT.state.gMain;
        var pathNode = hitPathEl.previousSibling;
        if (!pathNode || !pathNode.getTotalLength) return;

        var totalLength = pathNode.getTotalLength();
        var dotCount = calcDotCount(totalLength);
        var startTime = performance.now();
        var elements = [];
        var animState = { hitEl: hitPathEl, cancelled: false, elements: elements };
        activeLineAnim = animState;

        // 预创建光点 + 拖尾光晕
        var dots = [], glows = [];
        for (var i = 0; i < dotCount; i++) {
            var d = gMain.append('circle')
                .attr('r', 3.5).attr('fill', '#c2703d')
                .attr('stroke', '#fff').attr('stroke-width', 1).attr('opacity', 0);
            var g = gMain.append('circle')
                .attr('r', 6).attr('fill', '#c2703d').attr('opacity', 0);
            dots.push(d);
            glows.push(g);
            elements.push(d, g);
        }

        function step(now) {
            if (animState.cancelled) return;
            var elapsed = now - startTime;
            var allDone = true;
            for (var i = 0; i < dotCount; i++) {
                var dotElapsed = elapsed - i * STAGGER;
                if (dotElapsed < 0) { allDone = false; continue; }
                var t = Math.min(dotElapsed / PC_DURATION, 1);
                if (t < 1) { allDone = false; }
                var ease = t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
                var pt = pathNode.getPointAtLength(ease * totalLength);
                var fade = t > 0.85 ? (1 - t) / 0.15 : 1;
                dots[i].attr('cx', pt.x).attr('cy', pt.y).attr('opacity', fade);
                glows[i].attr('cx', pt.x).attr('cy', pt.y).attr('opacity', 0.25 * fade);
            }
            if (!allDone) {
                requestAnimationFrame(step);
            } else {
                elements.forEach(function (el) { el.remove(); });
                if (activeLineAnim === animState) { activeLineAnim = null; }
            }
        }
        requestAnimationFrame(step);
    }

    // 配偶连线点击：光点从中点沿线向两侧依次跑出（走马灯）
    function animateSpouseLine(hitEl) {
        // 再次点击同一条线 → 取消动画
        if (activeLineAnim && activeLineAnim.hitEl === hitEl) {
            stopLineAnimation();
            return;
        }
        // 点击不同线 → 先取消上一条线的动画
        stopLineAnimation();

        var gMain = FT.state.gMain;
        var lineNode = hitEl.previousSibling;
        if (!lineNode || !lineNode.getTotalLength) return;

        var totalLength = lineNode.getTotalLength();
        var midLen = totalLength / 2;
        var dotCount = calcDotCount(totalLength);
        var startTime = performance.now();
        var elements = [];
        var animState = { hitEl: hitEl, cancelled: false, elements: elements };
        activeLineAnim = animState;

        // 中点坐标（起始光晕用）
        var midPoint = lineNode.getPointAtLength(midLen);
        var centerGlow = gMain.append('circle')
            .attr('cx', midPoint.x).attr('cy', midPoint.y)
            .attr('r', 4).attr('fill', '#d8b4ad').attr('opacity', 0.8);
        elements.push(centerGlow);

        // 预创建向左 + 向右的光点 + 拖尾光晕
        var dotsL = [], glowsL = [], dotsR = [], glowsR = [];
        for (var i = 0; i < dotCount; i++) {
            var dl = gMain.append('circle')
                .attr('r', 3.5).attr('fill', '#c2703d')
                .attr('stroke', '#fff').attr('stroke-width', 0.8).attr('opacity', 0);
            var gl = gMain.append('circle')
                .attr('r', 5).attr('fill', '#c2703d').attr('opacity', 0);
            var dr = gMain.append('circle')
                .attr('r', 3.5).attr('fill', '#c2703d')
                .attr('stroke', '#fff').attr('stroke-width', 0.8).attr('opacity', 0);
            var gr = gMain.append('circle')
                .attr('r', 5).attr('fill', '#c2703d').attr('opacity', 0);
            dotsL.push(dl); glowsL.push(gl);
            dotsR.push(dr); glowsR.push(gr);
            elements.push(dl, gl, dr, gr);
        }

        function step(now) {
            if (animState.cancelled) return;
            var elapsed = now - startTime;
            var tCenter = Math.min(elapsed / 300, 1);
            centerGlow.attr('opacity', 0.8 * (1 - tCenter)).attr('r', 4 + 8 * tCenter);

            var allDone = true;
            for (var i = 0; i < dotCount; i++) {
                var dotElapsed = elapsed - i * STAGGER;
                if (dotElapsed < 0) { allDone = false; continue; }
                var t = Math.min(dotElapsed / SP_DURATION, 1);
                if (t < 1) { allDone = false; }
                var ease = 1 - Math.pow(1 - t, 3); // easeOutCubic
                var fade = t > 0.85 ? (1 - t) / 0.15 : 1;

                // 向左/上端：从 midLen 向 0
                var leftLen = midLen * (1 - ease);
                var leftPt = lineNode.getPointAtLength(leftLen);
                dotsL[i].attr('cx', leftPt.x).attr('cy', leftPt.y).attr('opacity', fade);
                glowsL[i].attr('cx', leftPt.x).attr('cy', leftPt.y).attr('opacity', 0.2 * fade);

                // 向右/下端：从 midLen 向 totalLength
                var rightLen = midLen + midLen * ease;
                var rightPt = lineNode.getPointAtLength(Math.min(rightLen, totalLength));
                dotsR[i].attr('cx', rightPt.x).attr('cy', rightPt.y).attr('opacity', fade);
                glowsR[i].attr('cx', rightPt.x).attr('cy', rightPt.y).attr('opacity', 0.2 * fade);
            }
            if (!allDone) {
                requestAnimationFrame(step);
            } else {
                elements.forEach(function (el) { el.remove(); });
                if (activeLineAnim === animState) { activeLineAnim = null; }
            }
        }
        requestAnimationFrame(step);
    }

    function toggleCollapse(nodeId) {
        if (FT.state.collapsedNodes.has(nodeId)) {
            FT.state.collapsedNodes.delete(nodeId);
        } else {
            FT.state.collapsedNodes.add(nodeId);
        }
        renderTree();
    }

    // 在 (cx, cy) 绘制爱心标记：
    //   离异 → 灰色空心 + 锯齿裂痕 + 「离异」标注；
    //   已故（任一配偶已逝、未离异）→ 墨灰空心爱心（与已故牌位呼应，不加裂痕）；
    //   其余（双方在婚）→ 朱砂红实心爱心。
    function drawHeartMarker(cx, cy, divorced, deceased) {
        const gMain = FT.state.gMain;
        const heart = gMain.append('g')
            .attr('transform', 'translate(' + cx + ',' + cy + ') scale(0.67) translate(-12,-12)');
        if (divorced) {
            heart.append('path')
                .attr('d', FT.HEART_PATH)
                .attr('fill', '#efece4')
                .attr('stroke', '#9a968c')
                .attr('stroke-width', 1.6);
            heart.append('path')
                .attr('d', FT.HEART_CRACK)
                .attr('fill', 'none')
                .attr('stroke', '#9a968c')
                .attr('stroke-width', 1.4)
                .attr('stroke-linejoin', 'round');
            gMain.append('text')
                .attr('x', cx)
                .attr('y', cy - 12)
                .attr('text-anchor', 'middle')
                .attr('font-family', FT.FONT_KAI)
                .attr('font-size', '10px')
                .attr('fill', '#9a968c')
                .text('离异');
        } else if (deceased) {
            heart.append('path')
                .attr('d', FT.HEART_PATH)
                .attr('fill', FT.PAPER_DECEASED)
                .attr('stroke', '#9a968c')
                .attr('stroke-width', 1.4);
        } else {
            heart.append('path')
                .attr('d', FT.HEART_PATH)
                .attr('fill', '#a63a2b')
                .attr('stroke', '#8a2f23')
                .attr('stroke-width', 0.8);
        }
    }

    // 跨分支夫妻连线：连接两张卡片顶部中点，向上拱起一条弧线，弧线顶点放爱心。
    // n、other 为 layoutNode（含 x/y/data）。样式：离异灰虚线+碎心；任一已故墨灰实线+灰空心；
    // 双方在婚朱砂红虚线+红心。
    function drawBloodSpouseLink(n, other, divorced, startX, startY) {
        const deceased = FT.isDeceased(n.data) || FT.isDeceased(other.data);
        const x1 = n.x + FT.NODE_WIDTH / 2 + startX;
        const y1 = n.y + startY;
        const x2 = other.x + FT.NODE_WIDTH / 2 + startX;
        const y2 = other.y + startY;

        const dist = Math.abs(x2 - x1);
        const lift = Math.min(50 + dist * 0.22, 170); // 弧线抬升高度

        const strokeColor = divorced ? '#9a968c' : (deceased ? '#a9a499' : '#a63a2b');
        const dash = divorced ? '4,4' : (deceased ? 'none' : '6,4');

        const arcD = 'M' + x1 + ',' + y1 +
            ' C' + x1 + ',' + (y1 - lift) +
            ' ' + x2 + ',' + (y2 - lift) +
            ' ' + x2 + ',' + y2;

        const bloodPath = FT.state.gMain.append('path')
            .attr('d', arcD)
            .attr('fill', 'none')
            .attr('stroke', strokeColor)
            .attr('stroke-width', 1.5)
            .attr('stroke-dasharray', dash)
            .attr('stroke-opacity', deceased && !divorced ? 0.6 : 0.85);

        // 隐形加宽点击区域
        FT.state.gMain.append('path')
            .attr('class', 'link-hit-area')
            .attr('fill', 'none')
            .attr('stroke', 'transparent')
            .attr('stroke-width', 12)
            .attr('d', arcD)
            .style('cursor', 'pointer')
            .on('mouseover', function () {
                bloodPath.attr('stroke-width', 2.8).attr('stroke-opacity', 1);
            })
            .on('mouseout', function () {
                bloodPath.attr('stroke-width', 1.5).attr('stroke-opacity', deceased && !divorced ? 0.6 : 0.85);
            })
            .on('click', function (event) {
                event.stopPropagation();
                animateSpouseLine(this);
            });

        // 爱心位于弧线顶点（三次贝塞尔 t=0.5 处）
        const heartX = (x1 + x2) / 2;
        const heartY = (y1 + y2) / 2 - 0.75 * lift;
        drawHeartMarker(heartX, heartY, divorced, deceased);
    }

    /**
     * 定位到指定节点：居中显示并高亮闪烁
     * 使用 getCTM() 获取节点在 SVG 视口中的绝对坐标，再反推其在 gMain 坐标系中的位置，
     * 避免 getBBox() 仅返回局部坐标导致的定位偏移。
     */
    function focusNode(nodeId) {
        var nodeEl = document.querySelector('g[data-id="' + nodeId + '"]');
        if (!nodeEl) return;

        var transform = d3.zoomTransform(svg.node());
        var k = transform.k;

        // getCTM() 返回从节点局部坐标到 SVG 视口的完整变换矩阵，
        // 包含了 gMain 的 zoom transform 和节点自身的 translate。
        // 由于 node 直接挂在 gMain 下，CTM = zoomTransform ∘ nodeTranslate，
        // 因此 e/k、f/k 即为节点原点在 gMain 坐标系中的 (x, y)。
        var ctm = nodeEl.getCTM();
        if (!ctm) return;

        var gx = ctm.e / k;
        var gy = ctm.f / k;
        var cx = gx + FT.NODE_WIDTH / 2;
        var cy = gy + FT.NODE_HEIGHT / 2;

        // 保持当前缩放级别，仅平移居中
        var newTransform = d3.zoomIdentity
            .translate(container.clientWidth / 2 - cx * k, container.clientHeight / 2 - cy * k)
            .scale(k);

        svg.transition().duration(600)
            .call(FT.state.zoom.transform, newTransform);

        // 平移动画结束后触发脉冲放大特效（600ms 与 transition.duration 一致）
        var innerEl = nodeEl.querySelector('.node-inner');
        if (innerEl) {
            setTimeout(function () {
                innerEl.classList.add('search-focus');
                setTimeout(function () { innerEl.classList.remove('search-focus'); }, 1500);
            }, 620);
        }
    }

    FT.setupScrollOverlay = setupScrollOverlay;
    FT.buildGenerationWatermark = buildGenerationWatermark;
    FT.toggleGenerationHighlight = toggleGenerationHighlight;
    FT.applyGenerationHighlight = applyGenerationHighlight;
    FT.renderTree = renderTree;
    FT.drawTreeContent = drawTreeContent;
    FT.toggleCollapse = toggleCollapse;
    FT.focusNode = focusNode;
})();
