/**
 * 族谱前端 - 布局模块
 * 方向无关的子树布局计算，以及「只看健在」「隐藏外嫁」两种显示树的构建。
 *
 * @author Family-Tree
 * @date 2026-08-02
 */
(function () {
    'use strict';

    const FT = window.FT;

    // 「只看健在」显示树：移除已故节点，保持族谱不断开。
    // 已故且有健在配偶 → 配偶顶替其位置锚定家支；
    // 已故且无健在配偶 → 子女上移挂到上一代；
    // 健在节点 → 剔除其已故配偶。生成新对象，不修改原始 treeData。
    function buildLivingTree(nodes) {
        const result = [];
        const livingBloodSpouses = function (node) {
            return (node.bloodSpouses || []).filter(function (sp) { return !FT.isDeceased(sp); });
        };
        nodes.forEach(function (node) {
            if (FT.isDeceased(node)) {
                const livingSpouse = (node.spouses || []).find(function (sp) { return !FT.isDeceased(sp); });
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
                    spouses: (node.spouses || []).filter(function (sp) { return !FT.isDeceased(sp); }),
                    bloodSpouses: livingBloodSpouses(node),
                    formerSpouses: (node.formerSpouses || []).filter(function (sp) { return !FT.isDeceased(sp); }),
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
                    formerSpouses: [],
                    children: []
                }));
            } else {
                result.push(Object.assign({}, node, {
                    spouses: node.spouses || [],
                    bloodSpouses: node.bloodSpouses || [],
                    formerSpouses: node.formerSpouses || [],
                    children: buildHideMarryOutTree(node.children || [])
                }));
            }
        });
        return result;
    }

    // 方向无关布局：
    //   主轴 = 世代展开方向（tb 为纵向 y，lr/rl 为横向 x）；
    //   交叉轴 = 同代成员排列方向（tb 为横向 x，lr/rl 为纵向 y）。
    // crossStart 为当前子树在交叉轴上的起点，返回该子树占用的交叉轴终点。
    function layoutSubTree(node, crossStart, depth, layoutNodes, links) {
        const horiz = FT.isHorizontal();
        const mainExtent = horiz ? FT.NODE_WIDTH : FT.NODE_HEIGHT;   // 主轴方向节点尺寸
        const crossExtent = horiz ? FT.NODE_HEIGHT : FT.NODE_WIDTH;  // 交叉轴方向节点尺寸
        const mainGap = horiz ? 60 : FT.V_GAP;                       // 世代间距
        const crossGap = FT.H_GAP;                                   // 同代（兄弟子树）间距

        const mainPos = depth * (mainExtent + mainGap);
        const isCollapsed = FT.state.collapsedNodes.has(node.id);
        const hasChildren = node.children && node.children.length > 0;

        const spouseCount = (node.spouses && node.spouses.length) || 0;
        const formerSpouseCount = (node.formerSpouses && node.formerSpouses.length) || 0;
        const totalSpouseCount = spouseCount + formerSpouseCount;
        const familyCross = crossExtent * (1 + totalSpouseCount) + FT.SPOUSE_GAP * totalSpouseCount;

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
            childCount: hasChildren ? FT.countDescendants(node) : 0
        };
        layoutNodes.push(selfLayoutNode);

        // 放置配偶（沿交叉轴）
        let spouseCross = nodeCross + crossExtent + FT.SPOUSE_GAP;
        if (node.spouses) {
            node.spouses.forEach(function (spouse) {
                const sx = horiz ? mainPos : spouseCross;
                const sy = horiz ? spouseCross : mainPos;
                layoutNodes.push({x: sx, y: sy, data: spouse, depth: depth, hasChildren: false, childCount: 0});
                if (horiz) {
                    // 横向布局：配偶在本节点正下方，竖线相连
                    links.push({
                        type: 'spouse',
                        x1: nx + FT.NODE_WIDTH / 2, y1: ny + FT.NODE_HEIGHT,
                        x2: sx + FT.NODE_WIDTH / 2, y2: sy,
                        divorced: spouse.divorced || false,
                        deceased: FT.isDeceased(node) || FT.isDeceased(spouse)
                    });
                } else {
                    // 纵向布局：配偶在本节点右侧，横线相连
                    links.push({
                        type: 'spouse',
                        x1: nx + FT.NODE_WIDTH, y1: ny + FT.NODE_HEIGHT / 2,
                        x2: sx, y2: sy + FT.NODE_HEIGHT / 2,
                        divorced: spouse.divorced || false,
                        deceased: FT.isDeceased(node) || FT.isDeceased(spouse)
                    });
                }
                spouseCross += crossExtent + FT.SPOUSE_GAP;
            });
        }

        // 放置前配偶（离异/丧偶后再婚），布局方式同配偶，连线标记 former
        if (node.formerSpouses) {
            node.formerSpouses.forEach(function (spouse) {
                const sx = horiz ? mainPos : spouseCross;
                const sy = horiz ? spouseCross : mainPos;
                layoutNodes.push({x: sx, y: sy, data: spouse, depth: depth, hasChildren: false, childCount: 0});
                if (horiz) {
                    links.push({
                        type: 'spouse',
                        x1: nx + FT.NODE_WIDTH / 2, y1: ny + FT.NODE_HEIGHT,
                        x2: sx + FT.NODE_WIDTH / 2, y2: sy,
                        divorced: spouse.divorced || false,
                        deceased: FT.isDeceased(node) || FT.isDeceased(spouse),
                        former: true
                    });
                } else {
                    links.push({
                        type: 'spouse',
                        x1: nx + FT.NODE_WIDTH, y1: ny + FT.NODE_HEIGHT / 2,
                        x2: sx, y2: sy + FT.NODE_HEIGHT / 2,
                        divorced: spouse.divorced || false,
                        deceased: FT.isDeceased(node) || FT.isDeceased(spouse),
                        former: true
                    });
                }
                spouseCross += crossExtent + FT.SPOUSE_GAP;
            });
        }

        // 亲子连线（折叠时不画）：直接使用布局阶段收集的子节点布局对象，避免线性查找
        if (hasChildren && !isCollapsed) {
            childLayoutNodes.forEach(function (childNode) {
                if (horiz) {
                    // 横向布局：父节点右缘 → 子节点左缘
                    links.push({
                        type: 'parent',
                        x1: nx + FT.NODE_WIDTH, y1: ny + FT.NODE_HEIGHT / 2,
                        x2: childNode.x, y2: childNode.y + FT.NODE_HEIGHT / 2
                    });
                } else {
                    // 纵向布局：父节点底缘 → 子节点顶缘
                    links.push({
                        type: 'parent',
                        x1: nx + FT.NODE_WIDTH / 2, y1: ny + FT.NODE_HEIGHT,
                        x2: childNode.x + FT.NODE_WIDTH / 2, y2: childNode.y
                    });
                }
            });
        }

        const usedCross = Math.max(familyCross, childTotalCross);
        return {cross: crossStart + usedCross, node: selfLayoutNode};
    }

    FT.buildLivingTree = buildLivingTree;
    FT.buildHideMarryOutTree = buildHideMarryOutTree;
    FT.layoutSubTree = layoutSubTree;
})();
