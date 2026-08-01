/**
 * 族谱管理系统 - 前端主逻辑
 * D3.js 族谱可视化 + 节点交互
 * 功能：折叠/展开、离异标注、节点详情、新增父节点
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
(function () {
    'use strict';

    // ========== 常量 ==========
    // 竖排窄高节点（古典牌位式）
    const NODE_WIDTH = 64;
    const NODE_HEIGHT = 132;
    const SPOUSE_GAP = 14;
    const H_GAP = 36;
    const V_GAP = 70;

    // 古典配色（作为节点绶带/边框的强调色）
    const COLOR_MAP = {
        'default': '#5b6b7c',       // 藏青
        'paternal': '#a63a2b',      // 朱砂
        'maternal': '#a0522d',      // 赭石
        'spouse_family': '#6b4f8a', // 紫
        'adopted': '#3a6b4f',       // 松绿
        'highlight': '#a63d5c'      // 胭脂
    };

    // 已故节点：墨灰（覆盖 colorLabel）
    const DECEASED_COLOR = '#9a968c';
    // 宣纸底色与墨色
    const PAPER_COLOR = '#faf5e6';
    const PAPER_DECEASED = '#ddd9cd';
    const INK_COLOR = '#2b2622';
    const INK_DECEASED = '#77736a';

    // 楷体字体栈：导出 SVG 为独立文档不携带页面 CSS，文本需内联 font-family
    const FONT_KAI = '"Kaiti SC", "STKaiti", "KaiTi", "BiauKai", "Noto Serif SC", "Songti SC", "SimSun", serif';

    // 配偶连线爱心（24x24 视图坐标）
    const HEART_PATH = 'M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 ' +
        '2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 ' +
        '19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z';
    // 离异：爱心上的锯齿裂痕
    const HEART_CRACK = 'M12 4.5 L10 9 L14 12 L9.5 15.5 L12 20';

    const COLOR_OPTIONS = [
        {code: 'default', label: '默认'},
        {code: 'paternal', label: '父系'},
        {code: 'maternal', label: '母系'},
        {code: 'spouse_family', label: '姻亲'},
        {code: 'adopted', label: '过继'},
        {code: 'highlight', label: '高亮'}
    ];

    // 构建颜色标注下拉选项 HTML；selectedCode 可选，传入时对应选项标记 selected。
    function buildColorOptions(selectedCode) {
        return COLOR_OPTIONS.map(function (c) {
            const sel = c.code === selectedCode ? ' selected' : '';
            return '<option value="' + c.code + '"' + sel + '>' + c.label + '</option>';
        }).join('');
    }

    // ========== 状态 ==========
    let treeData = [];
    let currentUser = null;
    let contextNodeId = null;
    let collapsedNodes = new Set();
    // 世代号 → 辈分名（字辈）映射，loadTree 时刷新
    let generationNames = {};
    // 当前被点击高亮的辈分（世代号），null 表示未高亮
    let highlightedGeneration = null;
    // 只看健在（隐藏已故节点）开关
    let hideDeceased = false;
    // 隐藏外嫁开关：女性（女儿）外嫁后配偶与子女归属夫家，隐藏其家支不显示
    let hideMarryOut = false;
    // 布局方向：'tb' 上下（世代从上到下） | 'lr' 左→右（始祖居最左） | 'rl' 右→左（始祖居最右，传统阅读方向）
    let layoutDirection = 'tb';

    // 是否横向布局（世代沿水平方向展开，同代成员竖向堆叠）
    function isHorizontal() {
        return layoutDirection !== 'tb';
    }

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

    async function init() {
        setupScrollOverlay();
        try {
            const res = await api('/api/auth/me');
            if (res.code !== 200) {
                window.location.href = '/login.html';
                return;
            }
            currentUser = res.data;
            document.getElementById('user-nickname').textContent = currentUser.nickname || '';
        } catch (e) {
            window.location.href = '/login.html';
            return;
        }
        bindEvents();
        await loadTree();
    }

    // ========== API 封装 ==========
    async function api(url, options) {
        const res = await fetch(url, Object.assign({
            headers: {'Content-Type': 'application/json'}
        }, options));
        if (res.status === 401) {
            window.location.href = '/login.html';
            throw new Error('unauthorized');
        }
        return res.json();
    }

    // ========== XSS 防护：HTML 转义 ==========
    // 将用户数据插入 HTML 文本节点前必须经 escapeHtml；插入属性值前必须经 escapeAttr。
    function escapeHtml(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function escapeAttr(value) {
        return escapeHtml(value);
    }

    // ========== 数据加载 ==========
    async function loadTree() {
        const res = await api('/api/tree/full');
        if (res.code === 200) {
            treeData = res.data || [];
            await loadGenerationNames();
            buildGenerationWatermark();
            renderTree();
        }
    }

    // 加载辈分名（世代名称）映射
    async function loadGenerationNames() {
        generationNames = {};
        const res = await api('/api/generation');
        if (res.code === 200 && res.data) {
            res.data.forEach(function (g) {
                generationNames[g.generation] = g.name;
            });
        }
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
        const myGeneration = currentUser && currentUser.generation ? currentUser.generation : null;

        for (let colStart = 1; colStart <= totalGenerations; colStart += perColumn) {
            const col = document.createElement('div');
            col.className = 'wm-col';
            for (let g = colStart; g < colStart + perColumn && g <= totalGenerations; g++) {
                const span = document.createElement('span');
                span.className = 'wm-char';
                span.textContent = generationNames[g] || '·';
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
        if (highlightedGeneration === generation) {
            highlightedGeneration = null;
            if (spanEl) {
                spanEl.classList.remove('wm-active');
            }
        } else {
            highlightedGeneration = generation;
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
            const match = highlightedGeneration != null && gen !== '' && gen !== null
                && parseInt(gen, 10) === highlightedGeneration;
            d3.select(this).classed('gen-highlighted', match);
        });
    }

    // ========== D3 渲染 ==========
    const svg = d3.select('#tree-svg');
    const container = document.getElementById('tree-container');
    let gMain = null;
    let zoom = null;

    function setupSvg() {
        svg.selectAll('*').remove();
        gMain = svg.append('g').attr('class', 'main-group');

        zoom = d3.zoom()
            .scaleExtent([0.2, 3])
            .on('zoom', function (event) {
                gMain.attr('transform', event.transform);
            });
        svg.call(zoom);
        svg.on('dblclick.zoom', null);
    }

    function isDeceased(node) {
        return node.deathDate != null && node.deathDate !== '';
    }

    // 「只看健在」显示树：移除已故节点，保持族谱不断开。
    // 已故且有健在配偶 → 配偶顶替其位置锚定家支；
    // 已故且无健在配偶 → 子女上移挂到上一代；
    // 健在节点 → 剔除其已故配偶。生成新对象，不修改原始 treeData。
    function buildLivingTree(nodes) {
        const result = [];
        const livingBloodSpouses = function (node) {
            return (node.bloodSpouses || []).filter(function (sp) { return !isDeceased(sp); });
        };
        nodes.forEach(function (node) {
            if (isDeceased(node)) {
                const livingSpouse = (node.spouses || []).find(function (sp) { return !isDeceased(sp); });
                const filteredChildren = buildLivingTree(node.children || []);
                if (livingSpouse) {
                    result.push(Object.assign({}, livingSpouse, {
                        spouses: [],
                        bloodSpouses: livingBloodSpouses(livingSpouse),
                        children: filteredChildren
                    }));
                } else {
                    filteredChildren.forEach(function (c) { result.push(c); });
                }
            } else {
                result.push(Object.assign({}, node, {
                    spouses: (node.spouses || []).filter(function (sp) { return !isDeceased(sp); }),
                    bloodSpouses: livingBloodSpouses(node),
                    children: buildLivingTree(node.children || [])
                }));
            }
        });
        return result;
    }

    // 「隐藏外嫁」显示树：女性（女儿）外嫁后，其配偶与子女归属夫家，
    // 保留本人、清空其配偶与子女（整棵子树）。生成新对象，不修改原始 treeData。
    function buildHideMarryOutTree(nodes) {
        const result = [];
        nodes.forEach(function (node) {
            if (node.gender === 2) {
                // 外嫁女：本人仍为本支血脉予以保留；配偶与子女（含其后代）随夫家，不显示
                result.push(Object.assign({}, node, {
                    spouses: [],
                    bloodSpouses: [],
                    children: []
                }));
            } else {
                result.push(Object.assign({}, node, {
                    spouses: node.spouses || [],
                    bloodSpouses: node.bloodSpouses || [],
                    children: buildHideMarryOutTree(node.children || [])
                }));
            }
        });
        return result;
    }

    function renderTree() {
        setupSvg();

        // 先按「隐藏外嫁」剔除出嫁女家支，再按「只看健在」剔除已故节点，两开关可叠加
        let displayTree = treeData || [];
        if (hideMarryOut) {
            displayTree = buildHideMarryOutTree(displayTree);
        }
        if (hideDeceased) {
            displayTree = buildLivingTree(displayTree);
        }

        // 清空后代计数记忆化缓存（树结构可能已变化）
        descendantMemo = new Map();

        if (displayTree.length === 0) {
            gMain.append('text')
                .attr('x', container.clientWidth / 2)
                .attr('y', container.clientHeight / 2)
                .attr('text-anchor', 'middle')
                .attr('fill', '#9a968c')
                .attr('font-size', '16px')
                .text(hideDeceased ? '当前没有健在的成员可显示' : '暂无族谱数据，点击右上角「添加始祖」开始');
            return;
        }

        const layoutNodes = [];
        const links = [];
        let crossOffset = 0;

        displayTree.forEach(function (root) {
            crossOffset = layoutSubTree(root, crossOffset, 0, layoutNodes, links).cross;
            crossOffset += H_GAP * 2;
        });

        // 交叉轴总跨度（tb=总宽度，lr/rl=总高度）
        const totalCross = crossOffset;

        // 主轴总跨度（tb=总高度，lr/rl=总宽度）：由最深世代推得
        const horiz = isHorizontal();
        let maxDepth = 0;
        layoutNodes.forEach(function (n) {
            if (n.depth > maxDepth) { maxDepth = n.depth; }
        });
        const mainExtent = horiz ? NODE_WIDTH : NODE_HEIGHT;
        const mainGap = horiz ? 60 : V_GAP;
        const totalMain = maxDepth * (mainExtent + mainGap) + mainExtent;

        // 'rl'：布局按 'lr' 计算（主轴向右展开），再沿主轴镜像 x 坐标，使始祖居最右
        if (layoutDirection === 'rl') {
            layoutNodes.forEach(function (n) {
                n.x = totalMain - n.x - NODE_WIDTH;
            });
            links.forEach(function (link) {
                link.x1 = totalMain - link.x1;
                link.x2 = totalMain - link.x2;
            });
        }

        // 屏幕起点：横向按主轴宽度(totalMain)居中，纵向按交叉轴宽度(totalCross)居中
        const treeWidth = horiz ? totalMain : totalCross;
        const startX = (container.clientWidth - treeWidth) / 2;
        const startY = 40;

        drawTreeContent(layoutNodes, links, startX, startY, horiz, true);

        // 初始适配缩放：统一仅按屏幕宽度适配（treeWidth 已按方向取对应跨度）；
        // 树过高时保持节点可读尺寸，由用户纵向平移查看
        const scale = Math.min(1, container.clientWidth / (treeWidth + 100));
        svg.call(zoom.transform, d3.zoomIdentity.translate(0, 0).scale(Math.max(scale, 0.5)));

        // 重绘后恢复辈分高亮状态
        applyGenerationHighlight();
    }

    // 绘制族谱内容（连线 + 血亲配偶弧线 + 节点卡片），追加到全局 gMain。
    // interactive=true（实时视图）：绘制折叠按钮并绑定右键/点击事件；
    // interactive=false（静态导出）：省略折叠按钮与交互，文本/连线内联字体与描边，
    // 以便序列化为独立 SVG（不携带页面 CSS）时样式不丢失。
    function drawTreeContent(layoutNodes, links, startX, startY, horiz, interactive) {
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
            const accent = deceased ? DECEASED_COLOR : (COLOR_MAP[n.data.colorLabel] || COLOR_MAP['default']);
            const paper = deceased ? PAPER_DECEASED : PAPER_COLOR;
            const ink = deceased ? INK_DECEASED : INK_COLOR;

            const group = gMain.append('g')
                .attr('class', 'node-group')
                .attr('transform', 'translate(' + gx + ',' + gy + ')')
                .attr('data-id', n.data.id)
                .attr('data-generation', n.data.generation != null ? n.data.generation : '');

            // 外层卡片
            group.append('rect')
                .attr('class', 'node-rect')
                .attr('width', NODE_WIDTH)
                .attr('height', NODE_HEIGHT)
                .attr('rx', 3)
                .attr('fill', paper)
                .attr('stroke', accent)
                .attr('stroke-width', 1.6);

            // 内层线框（古典双线框）
            group.append('rect')
                .attr('x', 3.5).attr('y', 3.5)
                .attr('width', NODE_WIDTH - 7)
                .attr('height', NODE_HEIGHT - 7)
                .attr('rx', 2)
                .attr('fill', 'none')
                .attr('stroke', accent)
                .attr('stroke-width', 0.7)
                .attr('opacity', 0.55);

            // 顶部绶带（强调色）
            group.append('rect')
                .attr('x', 3.5).attr('y', 3.5)
                .attr('width', NODE_WIDTH - 7)
                .attr('height', 6)
                .attr('rx', 1.5)
                .attr('fill', accent);

            // 辈分标签（绶带下方小字）：名字未必体现辈分，故单独展示。
            // 优先显示辈分管理里配置的辈分名，未配置则回退为"第X世"。
            if (n.data.generation != null) {
                const genLabel = generationNames[n.data.generation] || ('第' + n.data.generation + '世');
                group.append('text')
                    .attr('class', 'node-gen')
                    .attr('x', NODE_WIDTH / 2)
                    .attr('y', 17.5)
                    .attr('text-anchor', 'middle')
                    .attr('font-family', FONT_KAI)
                    .attr('font-size', '9px')
                    .attr('fill', deceased ? '#a39d8f' : '#8a6d3b')
                    .text(genLabel);
            }

            // 名字逐字竖排（居中）
            const chars = Array.from(n.data.name || '');
            const nameAreaTop = 24;
            const nameAreaH = NODE_HEIGHT - 48; // 顶部辈分区 + 底部信息区
            const charH = Math.min(26, Math.floor(nameAreaH / Math.max(chars.length, 1)));
            const fontSize = Math.min(21, charH - 4);
            const nameBlockH = chars.length * charH;
            const nameStartY = nameAreaTop + (nameAreaH - nameBlockH) / 2 + charH / 2;
            chars.forEach(function (ch, i) {
                group.append('text')
                    .attr('class', 'node-name')
                    .attr('x', NODE_WIDTH / 2)
                    .attr('y', nameStartY + i * charH)
                    .attr('text-anchor', 'middle')
                    .attr('dominant-baseline', 'central')
                    .attr('font-family', FONT_KAI)
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
                    .attr('x', NODE_WIDTH / 2)
                    .attr('y', NODE_HEIGHT - 11)
                    .attr('text-anchor', 'middle')
                    .attr('font-family', FONT_KAI)
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
                const isCollapsed = collapsedNodes.has(n.data.id);
                let btnTransform;
                if (layoutDirection === 'lr') {
                    btnTransform = 'translate(' + NODE_WIDTH + ',' + (NODE_HEIGHT / 2) + ')';
                } else if (layoutDirection === 'rl') {
                    btnTransform = 'translate(0,' + (NODE_HEIGHT / 2) + ')';
                } else {
                    btnTransform = 'translate(' + (NODE_WIDTH / 2) + ',' + NODE_HEIGHT + ')';
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
                    if (layoutDirection === 'lr') {
                        countText.attr('dx', '18px').attr('dy', '4px');
                    } else if (layoutDirection === 'rl') {
                        countText.attr('dx', '-18px').attr('dy', '4px');
                    } else {
                        countText.attr('dy', '22px');
                    }
                }

                btnGroup.on('click', function (event) {
                    event.stopPropagation();
                    toggleCollapse(n.data.id);
                });
            }

            if (interactive) {
                // 右键菜单
                group.on('contextmenu', function (event) {
                    event.preventDefault();
                    event.stopPropagation();
                    contextNodeId = n.data.id;
                    showContextMenu(event.clientX, event.clientY);
                });

                // 单击打开详情
                group.on('click', function () {
                    showDetailModal(n.data);
                });
            }
        });
    }

    // ========== 导出族谱 PDF ==========

    // 计算全量布局（忽略折叠与「只看健在」「隐藏外嫁」过滤），返回布局节点、连线及树的宽高。
    // 临时清空 collapsedNodes / hideDeceased / hideMarryOut，结束后恢复，不影响当前视图。
    function layoutWholeTree() {
        const savedCollapsed = collapsedNodes;
        const savedHideDeceased = hideDeceased;
        const savedHideMarryOut = hideMarryOut;
        collapsedNodes = new Set();
        hideDeceased = false;
        hideMarryOut = false;
        descendantMemo = new Map();
        try {
            const displayTree = treeData || [];
            const layoutNodes = [];
            const links = [];
            let crossOffset = 0;
            displayTree.forEach(function (root) {
                crossOffset = layoutSubTree(root, crossOffset, 0, layoutNodes, links).cross;
                crossOffset += H_GAP * 2;
            });
            const totalCross = crossOffset;
            const horiz = isHorizontal();
            let maxDepth = 0;
            layoutNodes.forEach(function (n) { if (n.depth > maxDepth) { maxDepth = n.depth; } });
            const mainExtent = horiz ? NODE_WIDTH : NODE_HEIGHT;
            const mainGap = horiz ? 60 : V_GAP;
            const totalMain = maxDepth * (mainExtent + mainGap) + mainExtent;
            if (layoutDirection === 'rl') {
                layoutNodes.forEach(function (n) { n.x = totalMain - n.x - NODE_WIDTH; });
                links.forEach(function (link) { link.x1 = totalMain - link.x1; link.x2 = totalMain - link.x2; });
            }
            return {
                layoutNodes: layoutNodes,
                links: links,
                treeWidth: horiz ? totalMain : totalCross,
                treeHeight: horiz ? totalCross : totalMain,
                horiz: horiz
            };
        } finally {
            collapsedNodes = savedCollapsed;
            hideDeceased = savedHideDeceased;
            hideMarryOut = savedHideMarryOut;
        }
    }

    // 导出用辈分水印：50 世按 10 列 × 5 行自右向左排布，每字下衬一团龙纹，
    // 登录人所属辈分以朱砂圈点高亮。全部使用内联属性（独立 SVG 无页面 CSS）。
    function drawExportWatermark(svgSel, width, height, dragonDataUrl) {
        const totalGenerations = 50;
        const perColumn = 5;
        const numCols = Math.ceil(totalGenerations / perColumn);
        const myGeneration = currentUser && currentUser.generation ? currentUser.generation : null;
        const colW = width / numCols;
        const charSize = Math.max(36, Math.min(72, Math.round(height / perColumn * 0.35)));
        const dragonSize = charSize * 8 / 3;

        for (let g = 1; g <= totalGenerations; g++) {
            const colFromRight = Math.floor((g - 1) / perColumn);
            const rowInCol = (g - 1) % perColumn;
            const cx = width - (colFromRight + 0.5) * colW;
            const cy = height * (rowInCol + 0.5) / perColumn;
            const isHl = myGeneration === g;

            svgSel.append('image')
                .attr('href', dragonDataUrl)
                .attr('x', cx - dragonSize / 2)
                .attr('y', cy - dragonSize / 2)
                .attr('width', dragonSize)
                .attr('height', dragonSize)
                .attr('opacity', isHl ? 0.39 : 0.08)
                .attr('preserveAspectRatio', 'xMidYMid meet');

            if (isHl) {
                svgSel.append('circle')
                    .attr('cx', cx).attr('cy', cy)
                    .attr('r', charSize * 0.85)
                    .attr('fill', 'none')
                    .attr('stroke', '#a63a2b')
                    .attr('stroke-width', 2)
                    .attr('opacity', 0.65);
            }

            svgSel.append('text')
                .attr('x', cx).attr('y', cy)
                .attr('text-anchor', 'middle')
                .attr('dominant-baseline', 'central')
                .attr('font-family', FONT_KAI)
                .attr('font-size', charSize)
                .attr('font-weight', isHl ? 700 : 400)
                .attr('fill', isHl ? '#a63a2b' : '#2b2622')
                .attr('opacity', isHl ? 0.65 : 0.13)
                .text(generationNames[g] || '·');
        }
    }

    // 构建导出用独立 SVG：宣纸背景 + 辈分水印 + 全量族谱，返回 SVG 元素及画布宽高。
    function buildExportSvg(dragonDataUrl) {
        const layout = layoutWholeTree();
        const padX = 80;
        const padTop = 180;   // 预留血亲配偶弧线向上抬升的空间（最大约 170）
        const padBottom = 80;
        const width = layout.treeWidth + padX * 2;
        const height = layout.treeHeight + padTop + padBottom;

        const svgNS = 'http://www.w3.org/2000/svg';
        const svgEl = document.createElementNS(svgNS, 'svg');
        svgEl.setAttribute('xmlns', svgNS);
        svgEl.setAttribute('width', width);
        svgEl.setAttribute('height', height);
        svgEl.setAttribute('viewBox', '0 0 ' + width + ' ' + height);
        const svgSel = d3.select(svgEl);

        // 宣纸底色
        svgSel.append('rect').attr('width', width).attr('height', height).attr('fill', '#f0e8d2');

        // 辈分水印（垫于最底层）
        drawExportWatermark(svgSel, width, height, dragonDataUrl);

        // 全量族谱内容：临时把 gMain 指向导出组，复用 drawTreeContent
        const exportGroup = svgSel.append('g');
        const savedGMain = gMain;
        gMain = exportGroup;
        try {
            drawTreeContent(layout.layoutNodes, layout.links, padX, padTop, layout.horiz, false);
        } finally {
            gMain = savedGMain;
        }

        return { svgEl: svgEl, width: width, height: height };
    }

    function fetchAsDataUrl(url) {
        return fetch(url).then(function (r) { return r.blob(); }).then(function (blob) {
            return new Promise(function (resolve, reject) {
                const reader = new FileReader();
                reader.onloadend = function () { resolve(reader.result); };
                reader.onerror = reject;
                reader.readAsDataURL(blob);
            });
        });
    }

    function loadImage(src) {
        return new Promise(function (resolve, reject) {
            const img = new Image();
            img.onload = function () { resolve(img); };
            img.onerror = reject;
            img.src = src;
        });
    }

    // 导出族谱 PDF：全量渲染 → 栅格化 → 单页自定义尺寸 PDF 下载。
    async function exportToPdf() {
        const btn = document.getElementById('btn-export');
        const originalText = btn.textContent;
        btn.disabled = true;
        btn.textContent = '导出中…';
        try {
            // 龙纹内联为 base64（SVG 作为图片加载时不允许外部资源）
            const dragonDataUrl = await fetchAsDataUrl('/img/longwen.png');
            const built = buildExportSvg(dragonDataUrl);

            const svgStr = new XMLSerializer().serializeToString(built.svgEl);
            const svgUrl = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svgStr);
            const img = await loadImage(svgUrl);

            // 栅格化：放大以提升清晰度，同时限制画布总尺寸避免超出浏览器上限
            const scale = Math.min(2, 8000 / Math.max(built.width, built.height));
            const canvas = document.createElement('canvas');
            canvas.width = Math.round(built.width * scale);
            canvas.height = Math.round(built.height * scale);
            const ctx = canvas.getContext('2d');
            ctx.fillStyle = '#f0e8d2';
            ctx.fillRect(0, 0, canvas.width, canvas.height);
            ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
            const pngUrl = canvas.toDataURL('image/png');

            // 单页 PDF，页面尺寸按族谱实际比例（px → pt）
            const wPt = built.width * 0.75;
            const hPt = built.height * 0.75;
            const pdf = new window.jspdf.jsPDF({
                orientation: wPt >= hPt ? 'landscape' : 'portrait',
                unit: 'pt',
                format: [wPt, hPt],
                compress: true
            });
            pdf.addImage(pngUrl, 'PNG', 0, 0, wPt, hPt);
            const d = new Date();
            const stamp = d.getFullYear() + ('0' + (d.getMonth() + 1)).slice(-2) + ('0' + d.getDate()).slice(-2);
            pdf.save('族谱_' + stamp + '.pdf');
        } catch (e) {
            alert('导出失败：' + (e && e.message ? e.message : e));
        } finally {
            btn.disabled = false;
            btn.textContent = originalText;
        }
    }

    function toggleCollapse(nodeId) {
        if (collapsedNodes.has(nodeId)) {
            collapsedNodes.delete(nodeId);
        } else {
            collapsedNodes.add(nodeId);
        }
        renderTree();
    }

    // 后代计数记忆化：layoutSubTree 对每个有子女的节点都会调用 countDescendants，
    // 朴素递归在整树渲染时退化为 O(n²)。按节点 id 记忆化（每次渲染前清空）降为 O(n)。
    let descendantMemo = new Map();

    function countDescendants(node) {
        const cached = descendantMemo.get(node.id);
        if (cached !== undefined) { return cached; }
        if (!node.children || node.children.length === 0) {
            descendantMemo.set(node.id, 0);
            return 0;
        }
        let count = node.children.length;
        node.children.forEach(function (child) {
            count += countDescendants(child);
        });
        descendantMemo.set(node.id, count);
        return count;
    }

    // 方向无关布局：
    //   主轴 = 世代展开方向（tb 为纵向 y，lr/rl 为横向 x）；
    //   交叉轴 = 同代成员排列方向（tb 为横向 x，lr/rl 为纵向 y）。
    // crossStart 为当前子树在交叉轴上的起点，返回该子树占用的交叉轴终点。
    function layoutSubTree(node, crossStart, depth, layoutNodes, links) {
        const horiz = isHorizontal();
        const mainExtent = horiz ? NODE_WIDTH : NODE_HEIGHT;   // 主轴方向节点尺寸
        const crossExtent = horiz ? NODE_HEIGHT : NODE_WIDTH;  // 交叉轴方向节点尺寸
        const mainGap = horiz ? 60 : V_GAP;                    // 世代间距
        const crossGap = H_GAP;                                // 同代（兄弟子树）间距

        const mainPos = depth * (mainExtent + mainGap);
        const isCollapsed = collapsedNodes.has(node.id);
        const hasChildren = node.children && node.children.length > 0;

        const spouseCount = (node.spouses && node.spouses.length) || 0;
        const familyCross = crossExtent * (1 + spouseCount) + SPOUSE_GAP * spouseCount;

        let childTotalCross = 0;
        const childLayoutNodes = [];
        if (hasChildren && !isCollapsed) {
            let childCross = crossStart;
            node.children.forEach(function (child) {
                const childResult = layoutSubTree(child, childCross, depth + 1, layoutNodes, links);
                childLayoutNodes.push(childResult.node);
                childCross = childResult.cross + crossGap;
            });
            childTotalCross = childCross - crossGap - crossStart;
        }

        const nodeCross = childTotalCross > familyCross
            ? crossStart + (childTotalCross - familyCross) / 2
            : crossStart;

        // 映射到屏幕坐标：主轴 → tb 为 y、lr/rl 为 x；交叉轴 → tb 为 x、lr/rl 为 y
        const nx = horiz ? mainPos : nodeCross;
        const ny = horiz ? nodeCross : mainPos;

        const selfLayoutNode = {
            x: nx, y: ny, data: node, depth: depth,
            hasChildren: hasChildren,
            childCount: hasChildren ? countDescendants(node) : 0
        };
        layoutNodes.push(selfLayoutNode);

        // 放置配偶（沿交叉轴）
        let spouseCross = nodeCross + crossExtent + SPOUSE_GAP;
        if (node.spouses) {
            node.spouses.forEach(function (spouse) {
                const sx = horiz ? mainPos : spouseCross;
                const sy = horiz ? spouseCross : mainPos;
                layoutNodes.push({x: sx, y: sy, data: spouse, depth: depth, hasChildren: false, childCount: 0});
                if (horiz) {
                    // 横向布局：配偶在本节点正下方，竖线相连
                    links.push({
                        type: 'spouse',
                        x1: nx + NODE_WIDTH / 2, y1: ny + NODE_HEIGHT,
                        x2: sx + NODE_WIDTH / 2, y2: sy,
                        divorced: spouse.divorced || false,
                        deceased: isDeceased(node) || isDeceased(spouse)
                    });
                } else {
                    // 纵向布局：配偶在本节点右侧，横线相连
                    links.push({
                        type: 'spouse',
                        x1: nx + NODE_WIDTH, y1: ny + NODE_HEIGHT / 2,
                        x2: sx, y2: sy + NODE_HEIGHT / 2,
                        divorced: spouse.divorced || false,
                        deceased: isDeceased(node) || isDeceased(spouse)
                    });
                }
                spouseCross += crossExtent + SPOUSE_GAP;
            });
        }

        // 亲子连线（折叠时不画）：直接使用布局阶段收集的子节点布局对象，避免线性查找
        if (hasChildren && !isCollapsed) {
            childLayoutNodes.forEach(function (childNode) {
                if (horiz) {
                    // 横向布局：父节点右缘 → 子节点左缘
                    links.push({
                        type: 'parent',
                        x1: nx + NODE_WIDTH, y1: ny + NODE_HEIGHT / 2,
                        x2: childNode.x, y2: childNode.y + NODE_HEIGHT / 2
                    });
                } else {
                    // 纵向布局：父节点底缘 → 子节点顶缘
                    links.push({
                        type: 'parent',
                        x1: nx + NODE_WIDTH / 2, y1: ny + NODE_HEIGHT,
                        x2: childNode.x + NODE_WIDTH / 2, y2: childNode.y
                    });
                }
            });
        }

        const usedCross = Math.max(familyCross, childTotalCross);
        return {cross: crossStart + usedCross, node: selfLayoutNode};
    }

    // 在 (cx, cy) 绘制爱心标记：
    //   离异 → 灰色空心 + 锯齿裂痕 + 「离异」标注；
    //   已故（任一配偶已逝、未离异）→ 墨灰空心爱心（与已故牌位呼应，不加裂痕）；
    //   其余（双方在婚）→ 朱砂红实心爱心。
    function drawHeartMarker(cx, cy, divorced, deceased) {
        const heart = gMain.append('g')
            .attr('transform', 'translate(' + cx + ',' + cy + ') scale(0.67) translate(-12,-12)');
        if (divorced) {
            heart.append('path')
                .attr('d', HEART_PATH)
                .attr('fill', '#efece4')
                .attr('stroke', '#9a968c')
                .attr('stroke-width', 1.6);
            heart.append('path')
                .attr('d', HEART_CRACK)
                .attr('fill', 'none')
                .attr('stroke', '#9a968c')
                .attr('stroke-width', 1.4)
                .attr('stroke-linejoin', 'round');
            gMain.append('text')
                .attr('x', cx)
                .attr('y', cy - 12)
                .attr('text-anchor', 'middle')
                .attr('font-family', FONT_KAI)
                .attr('font-size', '10px')
                .attr('fill', '#9a968c')
                .text('离异');
        } else if (deceased) {
            heart.append('path')
                .attr('d', HEART_PATH)
                .attr('fill', PAPER_DECEASED)
                .attr('stroke', '#9a968c')
                .attr('stroke-width', 1.4);
        } else {
            heart.append('path')
                .attr('d', HEART_PATH)
                .attr('fill', '#a63a2b')
                .attr('stroke', '#8a2f23')
                .attr('stroke-width', 0.8);
        }
    }

    // 跨分支夫妻连线：连接两张卡片顶部中点，向上拱起一条弧线，弧线顶点放爱心。
    // n、other 为 layoutNode（含 x/y/data）。样式：离异灰虚线+碎心；任一已故墨灰实线+灰空心；
    // 双方在婚朱砂红虚线+红心。
    function drawBloodSpouseLink(n, other, divorced, startX, startY) {
        const deceased = isDeceased(n.data) || isDeceased(other.data);
        const x1 = n.x + NODE_WIDTH / 2 + startX;
        const y1 = n.y + startY;
        const x2 = other.x + NODE_WIDTH / 2 + startX;
        const y2 = other.y + startY;

        const dist = Math.abs(x2 - x1);
        const lift = Math.min(50 + dist * 0.22, 170); // 弧线抬升高度

        const strokeColor = divorced ? '#9a968c' : (deceased ? '#a9a499' : '#a63a2b');
        const dash = divorced ? '4,4' : (deceased ? 'none' : '6,4');

        gMain.append('path')
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

    // ========== 右键菜单 ==========
    const contextMenu = document.getElementById('context-menu');

    function showContextMenu(x, y) {
        contextMenu.style.display = 'block';
        contextMenu.style.left = x + 'px';
        contextMenu.style.top = y + 'px';
    }

    function hideContextMenu() {
        contextMenu.style.display = 'none';
        contextNodeId = null;
    }

    document.addEventListener('click', hideContextMenu);
    document.addEventListener('contextmenu', function (e) {
        if (!e.target.closest('.node-group')) {
            hideContextMenu();
        }
    });

    contextMenu.querySelectorAll('.menu-item').forEach(function (item) {
        item.addEventListener('click', function () {
            const action = this.getAttribute('data-action');
            const nodeId = contextNodeId;
            hideContextMenu();
            if (!nodeId) return;

            switch (action) {
                case 'add-child': showNodeModal('添加子女', {parentNodeId: nodeId}); break;
                case 'add-spouse': showNodeModal('添加配偶', {spouseNodeId: nodeId}); break;
                case 'link-spouse': showLinkSpouseModal(nodeId); break;
                case 'add-parent': showNodeModal('添加父母', {childNodeId: nodeId}); break;
                case 'birth-order': showBirthOrderModal(nodeId); break;
                case 'edit': editNode(nodeId); break;
                case 'color': showColorModal(nodeId); break;
                case 'delete': deleteNode(nodeId); break;
            }
        });
    });

    // ========== 模态框 ==========
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

    // 新增节点表单（不含去世日期）
    function showNodeModal(title, relationOpts) {
        const colorOpts = buildColorOptions();

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
            const targetNode = findNodeIncludeSpouseById(treeData, relationOpts.spouseNodeId);
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

            const res = await api('/api/node', {method: 'POST', body: JSON.stringify(body)});
            if (res.code === 200) {
                closeModal();
                await loadTree();
            } else {
                alert(res.message || '操作失败');
            }
        });
    }

    // 关联现有成员为配偶（适用于表兄妹等近亲结婚：双方均已在族谱中有各自分支）
    async function showLinkSpouseModal(nodeId) {
        const nodeRes = await api('/api/node/' + nodeId);
        if (nodeRes.code !== 200) { alert(nodeRes.message || '节点不存在'); return; }
        const currentNode = nodeRes.data;

        const listRes = await api('/api/node/list');
        if (listRes.code !== 200) { alert(listRes.message || '加载成员列表失败'); return; }
        const allNodes = (listRes.data || []).filter(function (n) { return n.id !== currentNode.id; });

        const genderText = function (g) { return g === 1 ? '男' : (g === 2 ? '女' : ''); };
        const genText = function (g) {
            if (g == null) { return ''; }
            const name = generationNames[g];
            return '第' + g + '世' + (name ? '·' + escapeHtml(name) : '');
        };

        function buildOptions(keyword) {
            const kw = (keyword || '').trim();
            const html = allNodes
                .filter(function (n) { return !kw || (n.name || '').indexOf(kw) >= 0; })
                .map(function (n) {
                    const meta = [genText(n.generation), genderText(n.gender)].filter(Boolean).join('·');
                    return '<div class="link-spouse-opt" data-id="' + n.id + '">' +
                        '<span class="opt-name">' + escapeHtml(n.name) + '</span>' +
                        (meta ? '<span class="opt-meta">' + meta + '</span>' : '') +
                        '</div>';
                }).join('');
            return html || '<div class="link-spouse-empty">无匹配成员</div>';
        }

        showModal(
            '<h3>关联现有配偶</h3>' +
            '<p class="link-spouse-tip">为「' + escapeHtml(currentNode.name) + '」关联族谱中已有成员为配偶' +
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
            const res = await api('/api/relation', {
                method: 'POST',
                body: JSON.stringify({fromNodeId: fromId, toNodeId: toId, relationType: 2})
            });
            if (res.code === 200) {
                closeModal();
                await loadTree();
            } else {
                alert(res.message || '关联失败');
            }
        });
    }

    // 编辑节点基本信息（不含去世日期）
    async function editNode(nodeId) {
        const res = await api('/api/node/' + nodeId);
        if (res.code !== 200) { alert(res.message); return; }
        const node = res.data;

        const colorOpts = buildColorOptions(node.colorLabel);

        const genderOpts = [0, 1, 2].map(function (g) {
            const labels = ['未知', '男', '女'];
            const sel = g === node.gender ? ' selected' : '';
            return '<option value="' + g + '"' + sel + '>' + labels[g] + '</option>';
        }).join('');

        // 辈分下拉选项：从辈分管理列表（generationNames）获取，按世代升序排列。
        // 选项文案为"第X世（辈分名）"，未配置辈分名时标注"未配置辈分名"。
        // 若节点当前辈分不在列表中，补充为选项，避免丢失当前值。
        const genKeys = Object.keys(generationNames).map(Number).sort(function (a, b) { return a - b; });
        if (node.generation != null && genKeys.indexOf(node.generation) === -1) {
            genKeys.push(node.generation);
            genKeys.sort(function (a, b) { return a - b; });
        }
        const genOpts = genKeys.map(function (g) {
            const sel = g === node.generation ? ' selected' : '';
            const label = generationNames[g] ? '第' + g + '世（' + escapeHtml(generationNames[g]) + '）' : '第' + g + '世（未配置辈分名）';
            return '<option value="' + g + '"' + sel + '>' + label + '</option>';
        }).join('');

        showModal(
            '<h3>编辑基本信息</h3>' +
            '<div class="form-group"><label>姓名</label><input type="text" id="modal-name" value="' + escapeAttr(node.name || '') + '"></div>' +
            '<div class="form-group"><label>性别</label><select id="modal-gender">' + genderOpts + '</select></div>' +
            '<div class="form-group"><label>出生日期</label><input type="date" id="modal-birth" value="' + escapeAttr(node.birthDate || '') + '"></div>' +
            '<div class="form-group"><label>排次</label><input type="number" id="modal-order" min="1" value="' + (node.birthOrder != null ? node.birthOrder : '') + '" placeholder="未设置"></div>' +
            '<div class="form-group"><label>辈分（第几世）</label><select id="modal-generation">' + genOpts + '</select></div>' +
            '<div class="form-group"><label>颜色标注</label><select id="modal-color">' + colorOpts + '</select></div>' +
            '<div class="form-group"><label>备注</label><input type="text" id="modal-remark" value="' + escapeAttr(node.remark || '') + '"></div>' +
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

            const res = await api('/api/node', {method: 'PUT', body: JSON.stringify(body)});
            if (res.code === 200) {
                closeModal();
                await loadTree();
            } else {
                alert(res.message || '保存失败');
            }
        });
    }

    // 在树中按 ID 查找节点
    function findNodeById(nodes, id) {
        for (let i = 0; i < nodes.length; i++) {
            if (nodes[i].id === id) { return nodes[i]; }
            const found = findNodeById(nodes[i].children || [], id);
            if (found) { return found; }
        }
        return null;
    }

    // 按 id 查找节点，查找范围覆盖主节点、其配偶（spouses）及递归子女。
    // 用于"添加配偶"等场景：右键目标可能是主节点，也可能是配偶节点。
    function findNodeIncludeSpouseById(nodes, id) {
        for (let i = 0; i < nodes.length; i++) {
            const node = nodes[i];
            if (node.id === id) { return node; }
            const spouses = node.spouses || [];
            for (let j = 0; j < spouses.length; j++) {
                if (spouses[j].id === id) { return spouses[j]; }
            }
            const found = findNodeIncludeSpouseById(node.children || [], id);
            if (found) { return found; }
        }
        return null;
    }

    // 批量管理子女排次
    function showBirthOrderModal(nodeId) {
        const node = findNodeById(treeData, nodeId);
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
                '<span style="flex:1;font-size:14px;">' + escapeHtml(c.name) + '</span>' +
                (birthYear ? '<span style="color:#999;font-size:12px;">' + birthYear + '年生</span>' : '') +
                '</div>';
        });

        showModal(
            '<h3>排次管理 - ' + escapeHtml(node.name) + ' 的子女（共' + children.length + '人）</h3>' +
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
                const res = await api('/api/node', {method: 'PUT', body: JSON.stringify(updates[i])});
                if (res.code !== 200) {
                    alert(res.message || '保存失败');
                    return;
                }
            }
            closeModal();
            await loadTree();
        });
    }

    // 排次转中文称谓（1=老大 2=老二 ... 10=老十，其余显示 第N）
    function birthOrderText(order) {
        const cn = ['', '老大', '老二', '老三', '老四', '老五', '老六', '老七', '老八', '老九', '老十'];
        return order >= 1 && order <= 10 ? cn[order] : '第' + order;
    }

    // 递归统计各世代人数（含配偶），返回 {世代号: 人数}
    function collectGenerationCounts(nodes, acc) {
        nodes.forEach(function (n) {
            if (n.generation != null) {
                acc[n.generation] = (acc[n.generation] || 0) + 1;
            }
            (n.spouses || []).forEach(function (sp) {
                if (sp.generation != null) {
                    acc[sp.generation] = (acc[sp.generation] || 0) + 1;
                }
            });
            collectGenerationCounts(n.children || [], acc);
        });
        return acc;
    }

    // 辈分管理弹窗：10 行 × 5 列网格（共 50 世），可预先规划字辈
    function showGenerationModal() {
        const counts = collectGenerationCounts(treeData, {});
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
                'value="' + escapeAttr(generationNames[g] || '') + '" placeholder="辈分名">' +
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
            const res = await api('/api/generation', {
                method: 'PUT',
                body: JSON.stringify(payload)
            });
            if (res.code === 200) {
                closeModal();
                await loadTree();
            } else {
                alert(res.message || '保存失败');
            }
        });
    }

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

    // 节点详情（含去世日期编辑 + 配偶离异管理）
    function showDetailModal(node) {
        const genderText = node.gender === 1 ? '男' : node.gender === 2 ? '女' : '未知';
        const deceased = node.deathDate != null && node.deathDate !== '';
        // 香烛拜台仅敬献给「长辈」：节点已故且辈分高于登录人（世代数更小，更靠近始祖）。
        // 登录人或节点辈分缺失时无法论资排辈，不予展示。
        const myGeneration = currentUser && currentUser.generation;
        const isSenior = deceased && myGeneration != null && node.generation != null && node.generation < myGeneration;
        const color = deceased ? DECEASED_COLOR : (COLOR_MAP[node.colorLabel] || COLOR_MAP['default']);
        const genName = generationNames[node.generation] ? '（' + generationNames[node.generation] + '）' : '';

        let spouseHtml = '';
        const hasSpouses = (node.spouses && node.spouses.length > 0) || (node.bloodSpouses && node.bloodSpouses.length > 0);
        if (hasSpouses) {
            spouseHtml = '<div class="detail-section" style="margin-top:14px;"><label style="font-weight:600;display:block;margin-bottom:6px;">配偶关系</label>';
            const renderSpouseRow = function (sp, isBlood) {
                // 已故节点不展示婚姻状态（在婚/已离异），配偶名后留空；
                // 在婚（含再婚）与离异配偶用不同底色卡片 + 徽章区分，一眼可辨。
                let rowCls = 'spouse-row';
                let badgeHtml = '';
                if (!deceased) {
                    if (sp.divorced) {
                        rowCls += ' spouse-row--divorced';
                        badgeHtml = '<span class="spouse-badge spouse-badge--divorced">已离异</span>' +
                            (sp.divorceDate ? '<span class="spouse-date">（' + escapeHtml(sp.divorceDate) + '）</span>' : '');
                    } else {
                        rowCls += ' spouse-row--current';
                        badgeHtml = '<span class="spouse-badge spouse-badge--current">在婚</span>';
                    }
                }
                const bloodTag = isBlood ? '<span class="spouse-blood-tag">（血亲·各留本支）</span>' : '';
                // 参数经 data-* 属性传递（escapeAttr 转义），避免 inline onclick 拼接姓名导致 JS 注入
                return '<div class="' + rowCls + '">' +
                    '<span class="spouse-info">' +
                    '<span class="spouse-name">' + escapeHtml(sp.name) + '</span>' +
                    bloodTag + badgeHtml +
                    '</span>' +
                    '<button class="btn-sm js-divorce-btn" data-relation-id="' + escapeAttr(sp.relationId) +
                    '" data-marriage="' + escapeAttr(sp.marriageDate || '') +
                    '" data-divorce="' + escapeAttr(sp.divorceDate || '') +
                    '" data-name="' + escapeAttr(sp.name) + '">婚姻设置</button>' +
                    '</div>';
            };
            (node.spouses || []).forEach(function (sp) { spouseHtml += renderSpouseRow(sp, false); });
            (node.bloodSpouses || []).forEach(function (sp) { spouseHtml += renderSpouseRow(sp, true); });
            spouseHtml += '</div>';
        }

        showModal(
            '<h3 style="display:flex;align-items:center;gap:10px;">' +
            '<span style="display:inline-block;width:14px;height:14px;border-radius:50%;background:' + color + ';"></span>' +
            escapeHtml(node.name) + ' <small style="color:#999;font-weight:normal;">第' + node.generation + '世' + escapeHtml(genName) + '</small></h3>' +
            (isSenior ? buildOfferingHtml() : '') +
            '<div style="margin:12px 0;">' +
            '<p style="margin:4px 0;color:#666;font-size:14px;">性别：' + genderText + '</p>' +
            '<p style="margin:4px 0;color:#666;font-size:14px;">出生：' + escapeHtml(node.birthDate || '未知') + '</p>' +
            (deceased ? '<p style="margin:4px 0;color:#666;font-size:14px;">去世：' + escapeHtml(node.deathDate) + '</p>' : '') +
            (node.birthOrder != null ? '<p style="margin:4px 0;color:#666;font-size:14px;">排次：' + birthOrderText(node.birthOrder) + '</p>' : '') +
            (node.remark ? '<p style="margin:4px 0;color:#666;font-size:14px;">备注：' + escapeHtml(node.remark) + '</p>' : '') +
            '</div>' +
            spouseHtml +
            '<div style="margin-top:16px;padding-top:14px;border-top:1px solid #eee;display:flex;gap:10px;">' +
            '<button class="btn-sm js-death-btn" data-node-id="' + escapeAttr(node.id) +
            '" data-death="' + escapeAttr(node.deathDate || '') +
            '" data-name="' + escapeAttr(node.name) + '">设置去世日期</button>' +
            '</div>' +
            '<div class="modal-actions" style="margin-top:20px;">' +
            '<button class="btn-cancel" onclick="window._closeModal()">关闭</button></div>'
        );

        // 事件委托：从 data-* 读取参数（dataset 自动反转义），杜绝 inline onclick 注入
        document.querySelectorAll('.js-divorce-btn').forEach(function (btn) {
            btn.addEventListener('click', function () {
                window._showDivorceModal(parseInt(btn.dataset.relationId, 10),
                    btn.dataset.marriage, btn.dataset.divorce, btn.dataset.name);
            });
        });
        const deathBtn = document.querySelector('.js-death-btn');
        if (deathBtn) {
            deathBtn.addEventListener('click', function () {
                window._showDeathModal(parseInt(deathBtn.dataset.nodeId, 10),
                    deathBtn.dataset.death, deathBtn.dataset.name);
            });
        }
    }

    // 去世日期设置模态框
    window._showDeathModal = function (nodeId, currentDeathDate, nodeName) {
        showModal(
            '<h3>设置去世日期 - ' + escapeHtml(nodeName) + '</h3>' +
            '<div class="form-group"><label>去世日期</label>' +
            '<input type="date" id="death-date" value="' + escapeAttr(currentDeathDate || '') + '"></div>' +
            '<div class="modal-actions">' +
            '<button class="btn-cancel" onclick="window._closeModal()">取消</button>' +
            (currentDeathDate ? '<button class="btn-sm danger" id="death-clear">清除</button>' : '') +
            '<button class="btn-confirm" id="death-save">保存</button></div>'
        );

        document.getElementById('death-save').addEventListener('click', async function () {
            const deathDate = document.getElementById('death-date').value;
            const res = await api('/api/node', {
                method: 'PUT',
                body: JSON.stringify({id: nodeId, deathDate: deathDate})
            });
            if (res.code === 200) {
                closeModal();
                await loadTree();
            } else {
                alert(res.message || '保存失败');
            }
        });

        const clearBtn = document.getElementById('death-clear');
        if (clearBtn) {
            clearBtn.addEventListener('click', async function () {
                const res = await api('/api/node', {
                    method: 'PUT',
                    body: JSON.stringify({id: nodeId, deathDate: ''})
                });
                if (res.code === 200) {
                    closeModal();
                    await loadTree();
                } else {
                    alert(res.message || '操作失败');
                }
            });
        }
    };

    // 婚姻设置模态框（结婚日期、离异日期均为非必填）
    window._showDivorceModal = function (relationId, currentMarriageDate, currentDivorceDate, spouseName) {
        showModal(
            '<h3>婚姻设置 - ' + escapeHtml(spouseName) + '</h3>' +
            '<div class="form-group"><label>结婚日期（选填）</label>' +
            '<input type="date" id="marriage-date" value="' + escapeAttr(currentMarriageDate || '') + '"></div>' +
            '<div class="form-group"><label>离异日期（选填）</label>' +
            '<input type="date" id="divorce-date" value="' + escapeAttr(currentDivorceDate || '') + '"></div>' +
            '<p style="font-size:13px;color:#999;margin-bottom:12px;">两个日期均为选填。「标记离异」可不填日期直接标记；「恢复在婚」将清除离异状态。</p>' +
            '<div class="modal-actions">' +
            '<button class="btn-cancel" onclick="window._closeModal()">取消</button>' +
            '<button class="btn-sm danger" id="divorce-clear">恢复在婚</button>' +
            '<button class="btn-confirm" id="divorce-save">标记离异</button></div>'
        );

        // 标记离异：日期非必填
        document.getElementById('divorce-save').addEventListener('click', async function () {
            const marriageDate = document.getElementById('marriage-date').value || null;
            const divorceDate = document.getElementById('divorce-date').value || null;
            const res = await api('/api/relation', {
                method: 'PUT',
                body: JSON.stringify({id: relationId, divorced: true, marriageDate: marriageDate, divorceDate: divorceDate})
            });
            if (res.code === 200) {
                closeModal();
                await loadTree();
            } else {
                alert(res.message || '操作失败');
            }
        });

        // 恢复在婚：清除离异标记和离异日期，保留结婚日期
        document.getElementById('divorce-clear').addEventListener('click', async function () {
            const marriageDate = document.getElementById('marriage-date').value || null;
            const res = await api('/api/relation', {
                method: 'PUT',
                body: JSON.stringify({id: relationId, divorced: false, marriageDate: marriageDate, divorceDate: null})
            });
            if (res.code === 200) {
                closeModal();
                await loadTree();
            } else {
                alert(res.message || '操作失败');
            }
        });
    };

    function showColorModal(nodeId) {
        const opts = buildColorOptions();

        showModal(
            '<h3>修改颜色标注</h3>' +
            '<div class="form-group"><label>选择颜色</label><select id="modal-color">' + opts + '</select></div>' +
            '<div class="modal-actions">' +
            '<button class="btn-cancel" onclick="window._closeModal()">取消</button>' +
            '<button class="btn-confirm" id="modal-submit">确定</button></div>'
        );

        document.getElementById('modal-submit').addEventListener('click', async function () {
            const colorLabel = document.getElementById('modal-color').value;
            const res = await api('/api/node/color', {
                method: 'PUT',
                body: JSON.stringify({nodeIds: [nodeId], colorLabel: colorLabel})
            });
            if (res.code === 200) {
                closeModal();
                await loadTree();
            } else {
                alert(res.message || '操作失败');
            }
        });
    }

    async function deleteNode(nodeId) {
        if (!confirm('确定删除该节点？关联的关系也会一并删除。')) return;
        const res = await api('/api/node/' + nodeId, {method: 'DELETE'});
        if (res.code === 200) {
            await loadTree();
        } else {
            alert(res.message || '删除失败');
        }
    }

    // ========== 事件绑定 ==========
    function bindEvents() {
        document.getElementById('btn-logout').addEventListener('click', async function () {
            await api('/api/auth/logout', {method: 'POST'});
            window.location.href = '/login.html';
        });

        document.getElementById('btn-add-root').addEventListener('click', function () {
            showNodeModal('添加始祖', {});
        });

        document.getElementById('btn-generation').addEventListener('click', function () {
            showGenerationModal();
        });

        // 导出族谱：将整棵树（含宣纸背景与辈分水印）导出为单页 PDF
        document.getElementById('btn-export').addEventListener('click', function () {
            exportToPdf();
        });

        document.getElementById('chk-hide-deceased').addEventListener('change', function () {
            hideDeceased = this.checked;
            renderTree();
        });

        document.getElementById('chk-hide-marryout').addEventListener('change', function () {
            hideMarryOut = this.checked;
            renderTree();
        });

        document.getElementById('sel-layout').addEventListener('change', function () {
            layoutDirection = this.value;
            renderTree();
        });

        // 颜色图例折叠开关：默认隐藏，点击展开 / 收起
        document.getElementById('legend-toggle').addEventListener('click', function () {
            document.getElementById('legend').classList.toggle('open');
        });

        // 工具栏整体下拉隐藏：点击把手收起 / 展开，画布随之上移填补
        document.getElementById('toolbar-handle').addEventListener('click', function () {
            const collapsed = document.getElementById('toolbar').classList.toggle('collapsed');
            document.body.classList.toggle('toolbar-hidden', collapsed);
            this.title = collapsed ? '展开工具栏' : '收起工具栏';
        });
    }

    // 暴露关闭模态框方法
    window._closeModal = closeModal;

    // 启动
    init();
})();
