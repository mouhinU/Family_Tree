/**
 * 族谱前端 - 导出模块
 * 全量布局计算与 PDF 导出：宣纸背景 + 辈分水印 + 全量族谱 → 栅格化 → 单页 PDF。
 *
 * @author Family-Tree
 * @date 2026-08-02
 */
(function () {
    'use strict';

    const FT = window.FT;

    // 计算全量布局（忽略折叠与「只看健在」「隐藏外嫁」过滤），返回布局节点、连线及树的宽高。
    // 临时清空 collapsedNodes / hideDeceased / hideMarryOut，结束后恢复，不影响当前视图。
    function layoutWholeTree() {
        const savedCollapsed = FT.state.collapsedNodes;
        const savedHideDeceased = FT.state.hideDeceased;
        const savedHideMarryOut = FT.state.hideMarryOut;
        FT.state.collapsedNodes = new Set();
        FT.state.hideDeceased = false;
        FT.state.hideMarryOut = false;
        FT.state.descendantMemo = new Map();
        try {
            const displayTree = FT.state.treeData || [];
            const layoutNodes = [];
            const links = [];
            let crossOffset = 0;
            displayTree.forEach(function (root) {
                crossOffset = FT.layoutSubTree(root, crossOffset, 0, layoutNodes, links).cross;
                crossOffset += FT.H_GAP * 2;
            });
            const totalCross = crossOffset;
            const horiz = FT.isHorizontal();
            let maxDepth = 0;
            layoutNodes.forEach(function (n) { if (n.depth > maxDepth) { maxDepth = n.depth; } });
            const mainExtent = horiz ? FT.NODE_WIDTH : FT.NODE_HEIGHT;
            const mainGap = horiz ? 60 : FT.V_GAP;
            const totalMain = maxDepth * (mainExtent + mainGap) + mainExtent;
            if (FT.state.layoutDirection === 'rl') {
                layoutNodes.forEach(function (n) { n.x = totalMain - n.x - FT.NODE_WIDTH; });
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
            FT.state.collapsedNodes = savedCollapsed;
            FT.state.hideDeceased = savedHideDeceased;
            FT.state.hideMarryOut = savedHideMarryOut;
        }
    }

    // 导出用辈分水印：50 世按 10 列 × 5 行自右向左排布，每字下衬一团龙纹，
    // 登录人所属辈分以朱砂圈点高亮。全部使用内联属性（独立 SVG 无页面 CSS）。
    function drawExportWatermark(svgSel, width, height, dragonDataUrl) {
        const totalGenerations = 50;
        const perColumn = 5;
        const numCols = Math.ceil(totalGenerations / perColumn);
        const currentUser = FT.state.currentUser;
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
                .attr('font-family', FT.FONT_KAI)
                .attr('font-size', charSize)
                .attr('font-weight', isHl ? 700 : 400)
                .attr('fill', isHl ? '#a63a2b' : '#2b2622')
                .attr('opacity', isHl ? 0.65 : 0.13)
                .text(FT.state.generationNames[g] || '·');
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
        const savedGMain = FT.state.gMain;
        FT.state.gMain = exportGroup;
        try {
            FT.drawTreeContent(layout.layoutNodes, layout.links, padX, padTop, layout.horiz, false);
        } finally {
            FT.state.gMain = savedGMain;
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

    FT.exportToPdf = exportToPdf;
})();
