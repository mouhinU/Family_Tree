/**
 * 族谱前端 - 核心模块
 * 定义全局命名空间 FT：共享常量、共享状态与纯工具函数。
 * 其余 ft 模块均须在本文件之后加载，运行时通过 FT.* 互相调用。
 *
 * @author Family-Tree
 * @date 2026-08-02
 */
(function () {
    'use strict';

    window.FT = window.FT || {};
    const FT = window.FT;

    // ========== 常量 ==========
    // 竖排窄高节点（古典牌位式）
    FT.NODE_WIDTH = 64;
    FT.NODE_HEIGHT = 132;
    FT.SPOUSE_GAP = 14;
    FT.H_GAP = 36;
    FT.V_GAP = 48;

    // 古典配色（作为节点绶带/边框的强调色）
    FT.COLOR_MAP = {
        'default': '#5b6b7c',       // 藏青
        'paternal': '#a63a2b',      // 朱砂
        'maternal': '#a0522d',      // 赭石
        'spouse_family': '#6b4f8a', // 紫
        'adopted': '#3a6b4f',       // 松绿
        'highlight': '#a63d5c'      // 胭脂
    };

    // 已故节点：墨灰（覆盖 colorLabel）
    FT.DECEASED_COLOR = '#9a968c';
    // 宣纸底色与墨色
    FT.PAPER_COLOR = '#faf5e6';
    FT.PAPER_DECEASED = '#ddd9cd';
    FT.INK_COLOR = '#2b2622';
    FT.INK_DECEASED = '#77736a';

    // 楷体字体栈：导出 SVG 为独立文档不携带页面 CSS，文本需内联 font-family
    FT.FONT_KAI = '"Kaiti SC", "STKaiti", "KaiTi", "BiauKai", "Noto Serif SC", "Songti SC", "SimSun", serif';

    // 配偶连线爱心（24x24 视图坐标）
    FT.HEART_PATH = 'M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 ' +
        '2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 ' +
        '19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z';
    // 离异：爱心上的锯齿裂痕
    FT.HEART_CRACK = 'M12 4.5 L10 9 L14 12 L9.5 15.5 L12 20';

    FT.COLOR_OPTIONS = [
        {code: 'default', label: '默认'},
        {code: 'paternal', label: '父系'},
        {code: 'maternal', label: '母系'},
        {code: 'spouse_family', label: '姻亲'},
        {code: 'adopted', label: '过继'},
        {code: 'highlight', label: '高亮'}
    ];

    // 祭奠动效持续时长（毫秒），与 CSS 动画时长保持一致
    FT.OFFERING_FX_DURATION = 2400;

    // ========== 状态 ==========
    // 全部可变状态集中于此，各模块经 FT.state 读写，避免跨文件闭包共享
    FT.state = {
        treeData: [],
        currentUser: null,
        contextNodeId: null,
        collapsedNodes: new Set(),
        // 世代号 → 辈分名（字辈）映射，loadTree 时刷新
        generationNames: {},
        // 当前被点击高亮的辈分（世代号），null 表示未高亮
        highlightedGeneration: null,
        // 只看健在（隐藏已故节点）开关
        hideDeceased: false,
        // 隐藏外嫁开关：女性（女儿）外嫁后配偶与子女归属夫家，隐藏其家支不显示
        hideMarryOut: false,
        // 布局方向：'tb' 上下（世代从上到下） | 'lr' 左→右（始祖居最左） | 'rl' 右→左（始祖居最右，传统阅读方向）
        layoutDirection: 'tb',
        // 后代计数记忆化缓存（每次渲染前清空）
        descendantMemo: new Map(),
        // D3 主绘制组（导出时临时指向导出组）
        gMain: null,
        // D3 缩放行为
        zoom: null
    };

    // 是否横向布局（世代沿水平方向展开，同代成员竖向堆叠）
    function isHorizontal() {
        return FT.state.layoutDirection !== 'tb';
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

    // 构建颜色标注下拉选项 HTML；selectedCode 可选，传入时对应选项标记 selected。
    function buildColorOptions(selectedCode) {
        return FT.COLOR_OPTIONS.map(function (c) {
            const sel = c.code === selectedCode ? ' selected' : '';
            return '<option value="' + c.code + '"' + sel + '>' + c.label + '</option>';
        }).join('');
    }

    function isDeceased(node) {
        return node.deathDate != null && node.deathDate !== '';
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

    // 排次转中文称谓（1=老大 2=老二 ... 10=老十，其余显示 第N）
    function birthOrderText(order) {
        const cn = ['', '老大', '老二', '老三', '老四', '老五', '老六', '老七', '老八', '老九', '老十'];
        return order >= 1 && order <= 10 ? cn[order] : '第' + order;
    }

    // 后代计数记忆化：layoutSubTree 对每个有子女的节点都会调用 countDescendants，
    // 朴素递归在整树渲染时退化为 O(n²)。按节点 id 记忆化（每次渲染前清空）降为 O(n)。
    function countDescendants(node) {
        const memo = FT.state.descendantMemo;
        const cached = memo.get(node.id);
        if (cached !== undefined) { return cached; }
        if (!node.children || node.children.length === 0) {
            memo.set(node.id, 0);
            return 0;
        }
        let count = node.children.length;
        node.children.forEach(function (child) {
            count += countDescendants(child);
        });
        memo.set(node.id, count);
        return count;
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

    // ========== 轻提示（Toast） ==========
    // 非阻塞式消息提示，替代 alert()。支持 success/error/warning/info 四种类型。
    // 用法：FT.toast('操作成功', 'success') 或 FT.toast('操作失败')（默认 error）
    (function () {
        var container = null;
        function ensureContainer() {
            if (container && document.body.contains(container)) { return container; }
            container = document.createElement('div');
            container.id = 'toast-container';
            document.body.appendChild(container);
            return container;
        }

        function toast(message, type) {
            type = type || 'error';
            var c = ensureContainer();
            var el = document.createElement('div');
            el.className = 'toast toast-' + type;
            el.textContent = message;
            c.appendChild(el);
            // 触发 reflow 后添加 show 类（CSS transition 需要）
            requestAnimationFrame(function () {
                el.classList.add('show');
            });
            // 3秒后自动消失
            setTimeout(function () {
                el.classList.remove('show');
                el.classList.add('fade-out');
                setTimeout(function () {
                    if (el.parentNode) { el.parentNode.removeChild(el); }
                }, 300);
            }, 3000);
        }

        FT.toast = toast;
    })();

    FT.isHorizontal = isHorizontal;
    FT.escapeHtml = escapeHtml;
    FT.escapeAttr = escapeAttr;
    FT.buildColorOptions = buildColorOptions;
    FT.isDeceased = isDeceased;
    FT.findNodeById = findNodeById;
    FT.findNodeIncludeSpouseById = findNodeIncludeSpouseById;
    FT.birthOrderText = birthOrderText;
    FT.countDescendants = countDescendants;
    FT.collectGenerationCounts = collectGenerationCounts;
})();
