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
    // 50 世，每 5 世一列（共 10 列）；列从右到左（第 1 列 1-5 世在最右），列内从上到下（第 1 世在顶部）。
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

        const totalGenerations = 50;
        const perColumn = 5;
        const currentUser = FT.state.currentUser;
        const myGeneration = currentUser && currentUser.generation ? currentUser.generation : null;

        for (let colStart = 1; colStart <= totalGenerations; colStart += perColumn) {
            const col = document.createElement('div');
            col.className = 'wm-col';
            for (let g = colStart; g < colStart + perColumn && g <= totalGenerations; g++) {
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
        setupSvg();

        // 先按「隐藏外嫁」剔除出嫁女家支，再按「只看健在」剔除已故节点，两开关可叠加
        let displayTree = FT.state.treeData || [];
        if (FT.state.hideMarryOut) {
            displayTree = FT.buildHideMarryOutTree(displayTree);
        }
        if (FT.state.hideDeceased) {
            displayTree = FT.buildLivingTree(displayTree);
        }

        // 清空后代计数记忆化缓存（树结构可能已变化）
        FT.state.descendantMemo = new Map();

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

    // 绘制族谱内容（连线 + 血亲配偶弧线 + 节点卡片），追加到全局 gMain。
    // interactive=true（实时视图）：绘制折叠按钮并绑定右键/点击事件；
    // interactive=false（静态导出）：省略折叠按钮与交互，文本/连线内联字体与描边，
    // 以便序列化为独立 SVG（不携带页面 CSS）时样式不丢失。
    function drawTreeContent(layoutNodes, links, startX, startY, horiz, interactive) {
        const gMain = FT.state.gMain;

        // 绘制连线
        links.forEach(function (link) {
            if (link.type === 'spouse') {
                const midX = (link.x1 + link.x2) / 2 + startX;
                const midY = (link.y1 + link.y2) / 2 + startY;
                const deceased = link.deceased || false;

                // 细连线（爱心下方垫底）：离异灰虚线；已故墨灰实线；在婚浅红
                gMain.append('line')
                    .attr('x1', link.x1 + startX)
                    .attr('y1', link.y1 + startY)
                    .attr('x2', link.x2 + startX)
                    .attr('y2', link.y2 + startY)
                    .attr('stroke', link.divorced ? '#c4c0b4' : (deceased ? '#a9a499' : '#d8b4ad'))
                    .attr('stroke-width', 1.2)
                    .attr('stroke-dasharray', link.divorced ? '3,3' : 'none')
                    .attr('stroke-opacity', deceased && !link.divorced ? 0.7 : 1);

                // 爱心（24x24 → 缩放到约 16px，居中于连线中点）
                drawHeartMarker(midX, midY, link.divorced, deceased);
            } else if (horiz) {
                // 横向布局：水平 S 曲线（控制点取中点 x）
                const midX = (link.x1 + link.x2) / 2;
                gMain.append('path')
                    .attr('class', 'link-parent')
                    .attr('fill', 'none')
                    .attr('stroke', '#a89877')
                    .attr('stroke-width', 1.6)
                    .attr('d', 'M' + (link.x1 + startX) + ',' + (link.y1 + startY) +
                        ' C' + (midX + startX) + ',' + (link.y1 + startY) +
                        ' ' + (midX + startX) + ',' + (link.y2 + startY) +
                        ' ' + (link.x2 + startX) + ',' + (link.y2 + startY));
            } else {
                // 纵向布局：垂直 S 曲线（控制点取中点 y）
                const midY = (link.y1 + link.y2) / 2;
                gMain.append('path')
                    .attr('class', 'link-parent')
                    .attr('fill', 'none')
                    .attr('stroke', '#a89877')
                    .attr('stroke-width', 1.6)
                    .attr('d', 'M' + (link.x1 + startX) + ',' + (link.y1 + startY) +
                        ' C' + (link.x1 + startX) + ',' + (midY + startY) +
                        ' ' + (link.x2 + startX) + ',' + (midY + startY) +
                        ' ' + (link.x2 + startX) + ',' + (link.y2 + startY));
            }
        });

        // 跨分支夫妻连线（血亲配偶，如表兄妹结婚）：
        // 双方各自保留在原生分支，仅在两卡片间画一条弧线 + 爱心，按 relationId 去重。
        // 预建 id→节点 索引，避免每个血亲配偶都线性扫描 layoutNodes。
        const layoutNodeById = new Map();
        layoutNodes.forEach(function (ln) { layoutNodeById.set(ln.data.id, ln); });

        const drawnBloodRelations = {};
        layoutNodes.forEach(function (n) {
            const bloodSpouses = (n.data && n.data.bloodSpouses) || [];
            bloodSpouses.forEach(function (bs) {
                if (bs.relationId != null) {
                    if (drawnBloodRelations[bs.relationId]) { return; }
                    drawnBloodRelations[bs.relationId] = true;
                }
                const other = layoutNodeById.get(bs.id);
                if (!other) { return; } // 对方未渲染（如「只看健在」已过滤），跳过
                drawBloodSpouseLink(n, other, bs.divorced || false, startX, startY);
            });
        });

        // 绘制节点（古典牌位式：竖排名字）
        layoutNodes.forEach(function (n) {
            const gx = n.x + startX;
            const gy = n.y + startY;
            const deceased = n.data.deathDate != null && n.data.deathDate !== '';
            const accent = deceased ? FT.DECEASED_COLOR : (FT.COLOR_MAP[n.data.colorLabel] || FT.COLOR_MAP['default']);
            const paper = deceased ? FT.PAPER_DECEASED : FT.PAPER_COLOR;
            const ink = deceased ? FT.INK_DECEASED : FT.INK_COLOR;

            const group = gMain.append('g')
                .attr('class', 'node-group')
                .attr('transform', 'translate(' + gx + ',' + gy + ')')
                .attr('data-id', n.data.id)
                .attr('data-generation', n.data.generation != null ? n.data.generation : '');

            // 外层卡片
            group.append('rect')
                .attr('class', 'node-rect')
                .attr('width', FT.NODE_WIDTH)
                .attr('height', FT.NODE_HEIGHT)
                .attr('rx', 3)
                .attr('fill', paper)
                .attr('stroke', accent)
                .attr('stroke-width', 1.6);

            // 内层线框（古典双线框）
            group.append('rect')
                .attr('x', 3.5).attr('y', 3.5)
                .attr('width', FT.NODE_WIDTH - 7)
                .attr('height', FT.NODE_HEIGHT - 7)
                .attr('rx', 2)
                .attr('fill', 'none')
                .attr('stroke', accent)
                .attr('stroke-width', 0.7)
                .attr('opacity', 0.55);

            // 顶部绶带（强调色）
            group.append('rect')
                .attr('x', 3.5).attr('y', 3.5)
                .attr('width', FT.NODE_WIDTH - 7)
                .attr('height', 6)
                .attr('rx', 1.5)
                .attr('fill', accent);

            // 辈分标签（绶带下方小字）：名字未必体现辈分，故单独展示。
            // 优先显示辈分管理里配置的辈分名，未配置则回退为"第X世"。
            if (n.data.generation != null) {
                const genLabel = FT.state.generationNames[n.data.generation] || ('第' + n.data.generation + '世');
                group.append('text')
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
                group.append('text')
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
                group.append('text')
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
                const badge = group.append('g').attr('class', 'birth-order-badge');
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
                const btnGroup = group.append('g')
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

                // 单击打开详情
                group.on('click', function () {
                    FT.showDetailModal(n.data);
                });
            }
        });
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

        FT.state.gMain.append('path')
            .attr('d', 'M' + x1 + ',' + y1 +
                ' C' + x1 + ',' + (y1 - lift) +
                ' ' + x2 + ',' + (y2 - lift) +
                ' ' + x2 + ',' + y2)
            .attr('fill', 'none')
            .attr('stroke', strokeColor)
            .attr('stroke-width', 1.5)
            .attr('stroke-dasharray', dash)
            .attr('stroke-opacity', deceased && !divorced ? 0.6 : 0.85);

        // 爱心位于弧线顶点（三次贝塞尔 t=0.5 处）
        const heartX = (x1 + x2) / 2;
        const heartY = (y1 + y2) / 2 - 0.75 * lift;
        drawHeartMarker(heartX, heartY, divorced, deceased);
    }

    FT.setupScrollOverlay = setupScrollOverlay;
    FT.buildGenerationWatermark = buildGenerationWatermark;
    FT.toggleGenerationHighlight = toggleGenerationHighlight;
    FT.applyGenerationHighlight = applyGenerationHighlight;
    FT.renderTree = renderTree;
    FT.drawTreeContent = drawTreeContent;
    FT.toggleCollapse = toggleCollapse;
})();
