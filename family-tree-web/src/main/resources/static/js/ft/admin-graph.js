/**
 * 族谱前端 - 平台跨族治理图谱
 * 家族级只读关系图、异常筛选、单家族基础树与链接下钻。
 *
 * @author Family-Tree
 * @date 2026-11-26
 */
(function () {
    'use strict';

    const FT = window.FT;
    const GRAPH_HEIGHT = 560;
    const TYPE_CODES = {BLOOD: 1, MARRIAGE: 2};
    const RELATION_CODES = {PARENT_CHILD: 1, SPOUSE: 2, ADOPTION: 3};
    const TYPE_LABELS = {1: '血缘同人', 2: '姻亲指认'};
    const STATUS_LABELS = {NORMAL: '正常', FROZEN: '冻结', ARCHIVED: '已归档'};
    const LINK_COUNT_BINS = [
        {max: 0, label: '0', color: '#e1e7eb'},
        {max: 1, label: '1', color: '#b6c8d2'},
        {max: 3, label: '2–3', color: '#829fae'},
        {max: 6, label: '4–6', color: '#4f758b'},
        {max: 11, label: '7–11', color: '#28556d'},
        {max: Infinity, label: '12+', color: '#12384f'}
    ];

    const state = {
        initialized: false,
        loading: false,
        mode: 'GOVERNANCE',
        overview: null,
        network: null,
        detailRequest: 0,
        selectedFamilyId: null,
        filteredNodes: [],
        filteredEdges: [],
        simulation: null,
        svg: null,
        zoom: null,
        resizeTimer: null,
        searchTimer: null
    };

    function $(id) {
        return document.getElementById(id);
    }

    function num(value) {
        return value == null ? 0 : value;
    }

    function formatTime(value) {
        if (!value) {
            return '-';
        }
        return String(value).replace('T', ' ').substring(0, 19);
    }

    function init() {
        if (state.initialized) {
            return;
        }
        state.initialized = true;
        $('graph-mode').addEventListener('change', function () {
            switchMode($('graph-mode').value);
        });
        $('graph-family').addEventListener('change', function () {
            loadNetwork($('graph-family').value, true);
        });
        $('graph-search').addEventListener('input', function () {
            clearTimeout(state.searchTimer);
            state.searchTimer = setTimeout(renderGraph, 180);
        });
        $('graph-link-type').addEventListener('change', renderGraph);
        $('graph-issue-only').addEventListener('change', renderGraph);
        $('graph-refresh').addEventListener('click', function () {
            if (state.mode === 'NETWORK') {
                loadNetwork($('graph-family').value, true);
            } else {
                loadOverview(true);
            }
        });
        $('graph-reset-view').addEventListener('click', resetView);
        if (window.ResizeObserver) {
            const observer = new ResizeObserver(function () {
                clearTimeout(state.resizeTimer);
                state.resizeTimer = setTimeout(function () {
                    if ($('pane-graph').style.display !== 'none' && state.filteredNodes.length) {
                        drawGraph(state.filteredNodes, state.filteredEdges);
                    }
                }, 160);
            });
            observer.observe($('graph-stage'));
        }
    }

    function onEnter() {
        if (state.mode === 'NETWORK') {
            if (!state.network) {
                loadNetwork($('graph-family').value, false);
            } else {
                requestAnimationFrame(renderGraph);
            }
            return;
        }
        if (!state.overview) {
            loadOverview(false);
            return;
        }
        requestAnimationFrame(renderGraph);
    }

    function switchMode(mode) {
        state.detailRequest++;
        state.mode = mode === 'NETWORK' ? 'NETWORK' : 'GOVERNANCE';
        const networkMode = state.mode === 'NETWORK';
        $('graph-search').style.display = networkMode ? 'none' : '';
        $('graph-family').style.display = networkMode ? '' : 'none';
        $('graph-legend').innerHTML = networkMode
            ? '<span style="--legend-color:#5b6b7c">本族人物</span>'
                + '<span style="--legend-color:#c9b273">外族人物</span>'
                + '<span style="--legend-color:#a63a2b">血缘同人</span>'
                + '<span style="--legend-color:#6b4f8a">姻亲指认</span>'
                + '<span style="--legend-color:#8a7f6a">族内关系</span>'
            : governanceLegend()
                + '<span style="--legend-color:#a63a2b">血缘同人</span>'
                + '<span style="--legend-color:#6b4f8a">姻亲指认</span>';
        $('graph-detail').innerHTML = '<div class="detail-empty">'
            + (networkMode ? '选择家族后展示人物、族内关系与跨族链接' : '点击图谱中的家族查看跨族链接明细') + '</div>';
        if (!networkMode) {
            state.filteredNodes = [];
            state.filteredEdges = [];
            $('graph-stage').querySelectorAll('svg').forEach(function (svg) {
                svg.remove();
            });
            if (state.overview) {
                renderStats();
            }
            onEnter();
            return;
        }
        if (!state.overview) {
            loadOverview(false);
        } else {
            populateFamilySelector();
            loadNetwork($('graph-family').value, false);
        }
    }

    function populateFamilySelector() {
        const selector = $('graph-family');
        const previous = selector.value;
        const families = (state.overview && state.overview.families) || [];
        selector.innerHTML = families.map(function (family) {
            return '<option value="' + FT.escapeHtml(family.id) + '">' + FT.escapeHtml(family.name)
                + ' (#' + FT.escapeHtml(family.id) + ')</option>';
        }).join('');
        if (families.some(function (family) {
            return String(family.id) === String(previous);
        })) {
            selector.value = previous;
        }
        selector.disabled = !families.length;
        if (!families.length) {
            $('graph-empty').textContent = '暂无可展示的家族';
            $('graph-stats').innerHTML = '';
        }
    }

    async function loadNetwork(familyId, force) {
        if (state.loading || !familyId) {
            return;
        }
        state.loading = true;
        $('graph-empty').style.display = 'flex';
        $('graph-empty').textContent = '正在加载人物网络…';
        try {
            const res = await FT.api('/api/admin/graph/families/' + encodeURIComponent(familyId) + '/network');
            if (res.code !== 200 || !res.data) {
                $('graph-empty').textContent = '人物网络加载失败，请点击「刷新」重试';
                return;
            }
            state.network = res.data;
            state.selectedFamilyId = Number(familyId);
            renderNetworkStats();
            renderNetworkDetail();
            renderGraph();
        } catch (e) {
            $('graph-empty').textContent = '人物网络加载失败，请点击「刷新」重试';
        } finally {
            state.loading = false;
        }
    }

    function renderNetworkStats() {
        const data = state.network || {};
        const family = data.family || {};
        const crossLinks = data.crossFamilyLinks || [];
        const issues = crossLinks.filter(function (link) {
            return (link.issues || []).length > 0;
        }).length;
        const externalCount = (data.nodes || []).filter(function (node) {
            return node.familyId !== family.id;
        }).length;
        const cards = [
            {label: '本族人物', value: (data.nodes || []).length - externalCount, extra: family.name || ('家族 #' + family.id)},
            {label: '外族端点', value: externalCount, extra: '由跨族链接引用'},
            {label: '族内关系', value: (data.relations || []).length, extra: '亲子 / 夫妻 / 收养'},
            {label: '跨族链接', value: crossLinks.length, extra: issues > 0 ? issues + ' 条异常' : '未发现异常'}
        ];
        $('graph-stats').innerHTML = cards.map(function (card) {
            return '<div class="mini-stat"><b>' + FT.escapeHtml(card.value) + '</b><span>'
                + FT.escapeHtml(card.label) + '</span><div class="admin-hint">'
                + FT.escapeHtml(card.extra) + '</div></div>';
        }).join('');
        $('graph-summary').textContent = data.truncated ? '结果已截断，仅展示部分跨族链接' : '人物网络 · 只读视图';
    }

    async function loadOverview(force) {
        if (state.loading || (!force && state.overview)) {
            return;
        }
        state.loading = true;
        $('graph-empty').style.display = 'flex';
        $('graph-empty').textContent = '正在加载跨族图谱…';
        try {
            const res = await FT.api('/api/admin/graph');
            if (res.code !== 200 || !res.data) {
                $('graph-empty').textContent = '跨族图谱加载失败，请点击「刷新」重试';
                return;
            }
            state.overview = res.data;
            if (state.mode === 'NETWORK') {
                state.loading = false;
                populateFamilySelector();
                loadNetwork($('graph-family').value, false);
            } else {
                renderStats();
                renderGraph();
            }
        } catch (e) {
            $('graph-empty').textContent = '跨族图谱加载失败，请点击「刷新」重试';
        } finally {
            state.loading = false;
        }
    }

    function renderStats() {
        const data = state.overview || {};
        const cards = [
            {label: '涉及家族', value: num(data.linkedFamilyCount), extra: '全平台 ' + num(data.familyCount) + ' 个家族'},
            {label: '跨族链接', value: num(data.linkCount), extra: '血缘 ' + num(data.bloodLinkCount) + ' · 姻亲 ' + num(data.marriageLinkCount)},
            {label: '异常链接', value: num(data.issueLinkCount), extra: data.issueLinkCount > 0 ? '需人工核对' : '未发现异常'},
            {label: '家族聚合边', value: (data.edges || []).length, extra: data.truncated ? '数据已截断' : '按家族对聚合'}
        ];
        $('graph-stats').innerHTML = cards.map(function (card) {
            return '<div class="mini-stat"><b>' + FT.escapeHtml(card.value) + '</b><span>'
                + FT.escapeHtml(card.label) + '</span><div class="admin-hint">'
                + FT.escapeHtml(card.extra) + '</div></div>';
        }).join('');
        const generated = data.generatedTime ? ' · 更新于 ' + formatTime(data.generatedTime) : '';
        $('graph-summary').textContent = (data.truncated ? '结果已截断，仅展示部分数据' : '只读视图') + generated;
    }

    function renderGraph() {
        if (state.mode === 'NETWORK') {
            renderNetworkGraph();
            return;
        }
        if (!state.overview) {
            return;
        }
        const keyword = ($('graph-search').value || '').trim().toLowerCase();
        const linkType = $('graph-link-type').value;
        const issueOnly = $('graph-issue-only').checked;
        const families = (state.overview.families || []).filter(function (family) {
            if (keyword && String(family.name || '').toLowerCase().indexOf(keyword) < 0) {
                return false;
            }
            if (issueOnly && num(family.issueCount) === 0) {
                return false;
            }
            if (linkType === 'BLOOD' && num(family.bloodLinkCount) === 0) {
                return false;
            }
            if (linkType === 'MARRIAGE' && num(family.marriageLinkCount) === 0) {
                return false;
            }
            return true;
        });
        const familyIds = new Set(families.map(function (family) {
            return family.id;
        }));
        const edges = (state.overview.edges || []).filter(function (edge) {
            if (!familyIds.has(edge.srcFamilyId) || !familyIds.has(edge.dstFamilyId)) {
                return false;
            }
            if (issueOnly && num(edge.issueCount) === 0) {
                return false;
            }
            if (linkType === 'BLOOD' && num(edge.bloodLinkCount) === 0) {
                return false;
            }
            if (linkType === 'MARRIAGE' && num(edge.marriageLinkCount) === 0) {
                return false;
            }
            return true;
        });
        state.filteredNodes = families;
        state.filteredEdges = edges;
        drawGraph(families, edges);
    }

    function renderNetworkGraph() {
        const data = state.network;
        if (!data) {
            return;
        }
        const linkType = $('graph-link-type').value;
        const issueOnly = $('graph-issue-only').checked;
        const nodes = (data.nodes || []).map(function (node) {
            return Object.assign({kind: node.familyId === data.family.id ? 'INTERNAL' : 'EXTERNAL'}, node);
        });
        const nodeIds = new Set(nodes.map(function (node) {
            return node.id;
        }));
        const relations = (data.relations || []).filter(function (relation) {
            return nodeIds.has(relation.fromNodeId) && nodeIds.has(relation.toNodeId);
        }).map(function (relation) {
            return Object.assign({}, relation, {
                kind: 'RELATION',
                source: String(relation.fromNodeId),
                target: String(relation.toNodeId)
            });
        });
        const crossLinks = (data.crossFamilyLinks || []).filter(function (link) {
            if (!nodeIds.has(link.srcNodeId) || !nodeIds.has(link.dstNodeId)) {
                return false;
            }
            if (linkType !== 'ALL' && link.linkType !== TYPE_CODES[linkType]) {
                return false;
            }
            return !issueOnly || (link.issues || []).length > 0;
        }).map(function (link) {
            return Object.assign({}, link, {
                kind: 'CROSS_LINK',
                source: String(link.srcNodeId),
                target: String(link.dstNodeId)
            });
        });
        const edges = relations.concat(crossLinks);
        state.filteredNodes = nodes;
        state.filteredEdges = edges;
        drawGraph(nodes, edges);
    }

    function drawGraph(families, edges) {
        if (!window.d3) {
            $('graph-empty').style.display = 'flex';
            $('graph-empty').textContent = '图谱组件 D3 加载失败，请检查网络后刷新页面';
            return;
        }
        const stage = $('graph-stage');
        const width = Math.max(stage.clientWidth, 320);
        if (state.simulation) {
            state.simulation.stop();
        }
        stage.querySelectorAll('svg').forEach(function (svg) {
            svg.remove();
        });
        $('graph-empty').style.display = families.length ? 'none' : 'flex';
        if (!families.length) {
            $('graph-empty').textContent = '当前筛选条件下没有可展示的家族或跨族链接';
            return;
        }

        const nodes = families.map(function (family) {
            return Object.assign({}, family);
        });
        const links = edges.map(function (edge) {
            const mapped = Object.assign({}, edge);
            if (mapped.source == null && mapped.srcFamilyId != null) {
                mapped.source = String(mapped.srcFamilyId);
                mapped.target = String(mapped.dstFamilyId);
            }
            return mapped;
        });
        if (state.mode === 'GOVERNANCE') {
            const visibleLinkCounts = new Map();
            links.forEach(function (edge) {
                const count = num(edge.linkCount);
                visibleLinkCounts.set(String(edge.source), num(visibleLinkCounts.get(String(edge.source))) + count);
                visibleLinkCounts.set(String(edge.target), num(visibleLinkCounts.get(String(edge.target))) + count);
            });
            nodes.forEach(function (item) {
                item.visibleLinkCount = num(visibleLinkCounts.get(String(item.id)));
            });
        }
        const svg = d3.select(stage).append('svg')
            .attr('viewBox', [0, 0, width, GRAPH_HEIGHT])
            .attr('role', 'img')
            .attr('aria-label', '跨族治理关系图谱');
        const zoomLayer = svg.append('g');
        const zoom = d3.zoom().scaleExtent([0.35, 3]).on('zoom', function (event) {
            zoomLayer.attr('transform', event.transform);
        });
        svg.call(zoom);
        state.svg = svg;
        state.zoom = zoom;

        const link = zoomLayer.append('g')
            .attr('fill', 'none')
            .selectAll('line')
            .data(links)
            .join('line')
            .attr('stroke', edgeColor)
            .attr('stroke-width', function (edge) {
                if (edge.kind === 'RELATION') {
                    return 1.5;
                }
                return Math.min(7, 1.2 + Math.log2(num(edge.linkCount) + 1) * 1.2);
            })
            .attr('stroke-opacity', 0.68)
            .attr('stroke-dasharray', function (edge) {
                const hasIssue = edge.kind === 'CROSS_LINK'
                    ? (edge.issues || []).length > 0
                    : num(edge.issueCount) > 0;
                return hasIssue ? '5 4' : null;
            });

        const node = zoomLayer.append('g')
            .selectAll('g')
            .data(nodes)
            .join('g')
            .attr('data-family-id', function (item) {
                return item.id;
            })
            .style('cursor', 'pointer')
            .call(d3.drag()
                .on('start', function (event, item) {
                    if (!event.active) {
                        state.simulation.alphaTarget(0.25).restart();
                    }
                    item.fx = item.x;
                    item.fy = item.y;
                })
                .on('drag', function (event, item) {
                    item.fx = event.x;
                    item.fy = event.y;
                })
                .on('end', function (event, item) {
                    if (!event.active) {
                        state.simulation.alphaTarget(0);
                    }
                    item.fx = null;
                    item.fy = null;
                }));

        node.append('circle')
            .attr('r', function (item) {
                if (state.mode === 'NETWORK') {
                    return item.missing ? 14 : (item.kind === 'EXTERNAL' ? 13 : 17);
                }
                return 18 + Math.min(16, Math.log2(num(item.linkCount) + 1) * 3);
            })
            .attr('fill', nodeColor)
            .attr('stroke', '#fffdf7')
            .attr('stroke-width', function (item) {
                return item.id === state.selectedFamilyId ? 5 : 2;
            })
            .attr('filter', 'drop-shadow(0 2px 3px rgba(43,38,34,.18))');

        node.append('text')
            .attr('text-anchor', 'middle')
            .attr('dy', function (item) {
                if (state.mode === 'NETWORK') {
                    return item.kind === 'EXTERNAL' ? 26 : 31;
                }
                return 34 + Math.min(16, Math.log2(num(item.linkCount) + 1) * 3);
            })
            .attr('fill', '#2b2622')
            .attr('font-size', 12)
            .attr('font-weight', 600)
            .attr('paint-order', 'stroke')
            .attr('stroke', '#fbf8ef')
            .attr('stroke-width', 4)
            .attr('stroke-linejoin', 'round')
            .text(function (item) {
                return item.name || ('家族 #' + item.id);
            });

        node.append('title')
            .text(function (item) {
                if (state.mode === 'NETWORK') {
                    return (item.name || ('节点 #' + item.id)) + ' · '
                        + (item.familyName || '未知家族') + (item.missing ? '（节点缺失）' : '');
                }
                return (item.name || ('家族 #' + item.id)) + '：当前视图 '
                    + num(item.visibleLinkCount) + ' 条链接，总计 ' + num(item.linkCount)
                    + ' 条，' + num(item.issueCount) + ' 条异常';
            });

        node.on('mouseenter', function (event, item) {
            showTooltip(event, item);
        }).on('mousemove', function (event) {
            moveTooltip(event);
        }).on('mouseleave', function () {
            $('graph-tooltip').style.display = 'none';
        }).on('click', function (event, item) {
            event.stopPropagation();
            if (state.mode === 'NETWORK') {
                showNetworkNode(item);
            } else {
                loadFamilyDetail(item.id);
            }
        });

        svg.on('click', function () {
            $('graph-tooltip').style.display = 'none';
        });

        state.simulation = d3.forceSimulation(nodes)
            .force('link', d3.forceLink(links).id(function (item) {
                return String(item.id);
            }).distance(function (edge) {
                return 110 + Math.min(90, num(edge.linkCount) * 8);
            }).strength(0.35))
            .force('charge', d3.forceManyBody().strength(-520))
            .force('center', d3.forceCenter(width / 2, GRAPH_HEIGHT / 2))
            .force('collide', d3.forceCollide().radius(48))
            .on('tick', function () {
                link.attr('x1', function (edge) {
                    return edge.source.x;
                }).attr('y1', function (edge) {
                    return edge.source.y;
                }).attr('x2', function (edge) {
                    return edge.target.x;
                }).attr('y2', function (edge) {
                    return edge.target.y;
                });
                node.attr('transform', function (item) {
                    const x = Math.max(45, Math.min(width - 45, item.x));
                    const y = Math.max(45, Math.min(GRAPH_HEIGHT - 55, item.y));
                    return 'translate(' + x + ',' + y + ')';
                });
            });
        highlightSelection();
    }

    function governanceLegend() {
        return '<strong>节点色 · 链接数</strong>' + LINK_COUNT_BINS.map(function (bin) {
            return '<span style="--legend-color:' + bin.color + '">' + bin.label + '</span>';
        }).join('') + '<span style="--legend-color:#a63a2b">异常节点</span>';
    }

    function linkCountColor(count) {
        const bin = LINK_COUNT_BINS.find(function (item) {
            return num(count) <= item.max;
        });
        return bin.color;
    }

    function nodeColor(item) {
        if (state.mode === 'GOVERNANCE') {
            return num(item.issueCount) > 0 ? '#a63a2b' : linkCountColor(item.visibleLinkCount);
        }
        if (item.missing) {
            return '#fffdf7';
        }
        return item.kind === 'EXTERNAL' ? '#c9b273' : '#5b6b7c';
    }

    function showNetworkNode(node) {
        const relations = (state.network.relations || []).filter(function (relation) {
            return relation.fromNodeId === node.id || relation.toNodeId === node.id;
        });
        const links = (state.network.crossFamilyLinks || []).filter(function (link) {
            return link.srcNodeId === node.id || link.dstNodeId === node.id;
        });
        let html = '<div class="graph-detail-head"><h3>' + FT.escapeHtml(node.name || ('节点 #' + node.id))
            + '</h3><button class="ghost-btn" id="graph-clear-selection" type="button">查看全网</button></div>';
        html += '<div class="admin-hint">' + FT.escapeHtml(node.familyName || '未知家族')
            + ' · ' + FT.escapeHtml(node.genderLabel || '未知性别') + ' · 世代 '
            + FT.escapeHtml(node.generation == null ? '-' : node.generation) + '</div>';
        if (node.missing) {
            html += '<div class="insp-truncated">该端点节点已缺失，以下为链接记录中的引用信息。</div>';
        }
        html += '<div class="section-title">相关族内关系</div>';
        html += relations.length ? '<table class="graph-table"><thead><tr><th>关系</th><th>另一端</th></tr></thead><tbody>'
            + relations.map(function (relation) {
                const otherId = relation.fromNodeId === node.id ? relation.toNodeId : relation.fromNodeId;
                const other = (state.network.nodes || []).find(function (item) {
                    return item.id === otherId;
                });
                return '<tr><td>' + FT.escapeHtml(relation.relationTypeLabel || '-') + '</td><td>'
                    + FT.escapeHtml(other ? other.name : ('#' + otherId)) + '</td></tr>';
            }).join('') + '</tbody></table>' : '<div class="detail-empty">暂无相关族内关系</div>';
        html += '<div class="section-title">相关跨族链接</div>' + renderLinks(links);
        $('graph-detail').innerHTML = html;
        $('graph-clear-selection').addEventListener('click', renderNetworkDetail);
    }

    function edgeColor(edge) {
        if (edge.kind === 'RELATION') {
            return '#8a7f6a';
        }
        if (edge.kind === 'CROSS_LINK') {
            return edge.linkType === TYPE_CODES.MARRIAGE ? '#6b4f8a' : '#a63a2b';
        }
        if (num(edge.bloodLinkCount) > 0 && num(edge.marriageLinkCount) > 0) {
            return '#a0522d';
        }
        return num(edge.marriageLinkCount) > 0 ? '#6b4f8a' : '#a63a2b';
    }

    function showTooltip(event, family) {
        const tooltip = $('graph-tooltip');
        if (state.mode === 'NETWORK') {
            tooltip.innerHTML = '<b>' + FT.escapeHtml(family.name || ('节点 #' + family.id)) + '</b><br>'
                + FT.escapeHtml(family.familyName || '未知家族') + '<br>'
                + FT.escapeHtml(family.genderLabel || '未知性别') + ' · 世代 '
                + FT.escapeHtml(family.generation == null ? '-' : family.generation)
                + (family.missing ? '<br>节点缺失' : '');
        } else {
            tooltip.innerHTML = '<b>' + FT.escapeHtml(family.name || ('家族 #' + family.id)) + '</b><br>'
                + '跨族链接：' + num(family.linkCount) + '<br>'
                + '血缘：' + num(family.bloodLinkCount) + ' · 姻亲：' + num(family.marriageLinkCount) + '<br>'
                + '异常：' + num(family.issueCount);
        }
        tooltip.style.display = 'block';
        moveTooltip(event);
    }

    function moveTooltip(event) {
        const tooltip = $('graph-tooltip');
        const left = Math.min(window.innerWidth - tooltip.offsetWidth - 12, event.clientX + 14);
        const top = Math.min(window.innerHeight - tooltip.offsetHeight - 12, event.clientY + 14);
        tooltip.style.left = Math.max(8, left) + 'px';
        tooltip.style.top = Math.max(8, top) + 'px';
    }

    function resetView() {
        if (state.svg && state.zoom) {
            state.svg.transition().duration(250).call(state.zoom.transform, d3.zoomIdentity);
        }
    }

    function highlightSelection() {
        if (!state.svg) {
            return;
        }
        state.svg.selectAll('[data-family-id]')
            .select('circle')
            .attr('stroke-width', function (item) {
                return item.id === state.selectedFamilyId ? 5 : 2;
            });
    }

    async function loadFamilyDetail(familyId) {
        const request = ++state.detailRequest;
        state.selectedFamilyId = familyId;
        highlightSelection();
        const box = $('graph-detail');
        box.innerHTML = '<div class="detail-empty">正在加载家族基础树与跨族链接…</div>';
        try {
            const [detailRes, networkRes] = await Promise.all([
                FT.api('/api/admin/graph/families/' + encodeURIComponent(familyId)),
                FT.api('/api/admin/graph/families/' + encodeURIComponent(familyId) + '/network')
            ]);
            if (request !== state.detailRequest || state.mode !== 'GOVERNANCE') {
                return;
            }
            if (detailRes.code !== 200 || !detailRes.data) {
                box.innerHTML = '<div class="detail-empty">家族详情加载失败，请重新选择</div>';
                return;
            }
            if (networkRes.code !== 200 || !networkRes.data) {
                box.innerHTML = '<div class="detail-empty">家族基础树加载失败，请重新选择</div>';
                return;
            }
            renderFamilyDetail(detailRes.data, networkRes.data);
        } catch (e) {
            if (request === state.detailRequest && state.mode === 'GOVERNANCE') {
                box.innerHTML = '<div class="detail-empty">家族基础树加载失败，请重新选择</div>';
            }
        }
    }

    function renderFamilyDetail(detail, network) {
        const family = detail.family || {};
        let html = '<div class="graph-detail-head"><h3>' + FT.escapeHtml(family.name || ('家族 #' + family.id))
            + '</h3><button class="ghost-btn" id="graph-clear-selection" type="button">返回图谱</button></div>';
        html += '<div class="admin-hint">家族编号 ' + FT.escapeHtml(family.id) + ' · '
            + FT.escapeHtml(STATUS_LABELS[family.status] || family.status || '未知') + '</div>';
        html += '<div class="graph-detail-tabs" role="tablist" aria-label="家族详情">'
            + '<button class="graph-detail-tab active" id="graph-tab-tree" type="button" role="tab" '
            + 'aria-selected="true" aria-controls="graph-panel-tree">基础族谱</button>'
            + '<button class="graph-detail-tab" id="graph-tab-links" type="button" role="tab" '
            + 'aria-selected="false" aria-controls="graph-panel-links">跨族链接</button></div>';
        html += '<div id="graph-panel-tree" role="tabpanel" aria-labelledby="graph-tab-tree">'
            + renderFamilyTree(network) + '</div>';
        html += '<div id="graph-panel-links" role="tabpanel" aria-labelledby="graph-tab-links" hidden>'
            + '<div class="mini-stats">'
            + miniStat(family.linkCount, '链接')
            + miniStat(family.bloodLinkCount, '血缘')
            + miniStat(family.marriageLinkCount, '姻亲')
            + miniStat(family.issueCount, '异常')
            + '</div>';
        if (detail.truncated) {
            html += '<div class="insp-truncated">链接较多，仅展示前 5000 条。</div>';
        }
        html += '<div class="section-title">端点节点</div>' + renderNodes(detail.nodes || []);
        html += '<div class="section-title">链接明细</div>' + renderLinks(detail.links || []);
        html += '</div>';
        $('graph-detail').innerHTML = html;
        $('graph-clear-selection').addEventListener('click', clearFamilySelection);
        $('graph-tab-tree').addEventListener('click', function () {
            selectFamilyDetailTab('tree');
        });
        $('graph-tab-links').addEventListener('click', function () {
            selectFamilyDetailTab('links');
        });
    }

    function clearFamilySelection() {
        state.detailRequest++;
        state.selectedFamilyId = null;
        highlightSelection();
        $('graph-detail').innerHTML = '<div class="detail-empty">点击图谱中的家族查看基础族谱与跨族链接</div>';
    }

    function selectFamilyDetailTab(tab) {
        const tree = tab === 'tree';
        $('graph-panel-tree').hidden = !tree;
        $('graph-panel-links').hidden = tree;
        $('graph-tab-tree').classList.toggle('active', tree);
        $('graph-tab-links').classList.toggle('active', !tree);
        $('graph-tab-tree').setAttribute('aria-selected', String(tree));
        $('graph-tab-links').setAttribute('aria-selected', String(!tree));
    }

    function renderFamilyTree(network) {
        const familyId = network.family && network.family.id;
        const nodes = (network.nodes || []).filter(function (node) {
            return node.familyId === familyId;
        }).sort(function (left, right) {
            return num(left.generation) - num(right.generation)
                || String(left.name || '').localeCompare(String(right.name || ''), 'zh-CN')
                || num(left.id) - num(right.id);
        });
        if (!nodes.length) {
            return '<div class="detail-empty">该家族暂无族谱节点</div>';
        }
        const nodeMap = new Map(nodes.map(function (node) {
            return [node.id, node];
        }));
        const spouseMap = new Map();
        (network.relations || []).forEach(function (relation) {
            if (relation.relationType !== RELATION_CODES.SPOUSE
                || !nodeMap.has(relation.fromNodeId) || !nodeMap.has(relation.toNodeId)
                || relation.fromNodeId === relation.toNodeId) {
                return;
            }
            if (!spouseMap.has(relation.fromNodeId)) {
                spouseMap.set(relation.fromNodeId, []);
            }
            if (!spouseMap.get(relation.fromNodeId).includes(relation.toNodeId)) {
                spouseMap.get(relation.fromNodeId).push(relation.toNodeId);
            }
            if (!spouseMap.has(relation.toNodeId)) {
                spouseMap.set(relation.toNodeId, []);
            }
            if (!spouseMap.get(relation.toNodeId).includes(relation.fromNodeId)) {
                spouseMap.get(relation.toNodeId).push(relation.fromNodeId);
            }
        });
        const spouseAnchor = new Map();
        const spousePairs = new Set();
        spouseMap.forEach(function (spouses, nodeId) {
            spouses.forEach(function (spouseId) {
                const pairKey = [nodeId, spouseId].sort(num).join(':');
                if (spousePairs.has(pairKey)) {
                    return;
                }
                spousePairs.add(pairKey);
                const anchorId = num(nodeId) <= num(spouseId) ? nodeId : spouseId;
                spouseAnchor.set(anchorId === nodeId ? spouseId : nodeId, anchorId);
            });
        });
        const parents = new Map();
        const children = new Map();
        (network.relations || []).forEach(function (relation) {
            if ((relation.relationType !== RELATION_CODES.PARENT_CHILD
                && relation.relationType !== RELATION_CODES.ADOPTION)
                || !nodeMap.has(relation.fromNodeId) || !nodeMap.has(relation.toNodeId)
                || relation.fromNodeId === relation.toNodeId || parents.has(relation.toNodeId)) {
                return;
            }
            const parentId = spouseAnchor.get(relation.fromNodeId) || relation.fromNodeId;
            if (!nodeMap.has(parentId) || parentId === relation.toNodeId) {
                return;
            }
            parents.set(relation.toNodeId, parentId);
            if (!children.has(parentId)) {
                children.set(parentId, []);
            }
            children.get(parentId).push(relation.toNodeId);
        });
        children.forEach(function (childIds) {
            childIds.sort(function (leftId, rightId) {
                return compareTreeNodes(nodeMap.get(leftId), nodeMap.get(rightId));
            });
        });
        spouseMap.forEach(function (spouseIds) {
            spouseIds.sort(function (leftId, rightId) {
                return compareTreeNodes(nodeMap.get(leftId), nodeMap.get(rightId));
            });
        });
        const rendered = new Set();
        const roots = nodes.filter(function (node) {
            return !parents.has(node.id) && !spouseAnchor.has(node.id);
        }).map(function (node) {
            return node.id;
        });
        let html = '<div class="admin-hint family-tree-hint">按亲子 / 收养关系排列，夫妻并列；仅展示基础信息。</div>'
            + '<div class="admin-family-tree-scroll"><ul class="admin-family-tree" role="tree">';
        roots.forEach(function (rootId) {
            html += renderFamilyTreeNode(rootId, nodeMap, spouseMap, children, rendered);
        });
        nodes.forEach(function (node) {
            if (!rendered.has(node.id)) {
                html += renderFamilyTreeNode(node.id, nodeMap, spouseMap, children, rendered);
            }
        });
        return html + '</ul></div>';
    }

    function renderFamilyTreeNode(nodeId, nodeMap, spouseMap, children, rendered) {
        if (rendered.has(nodeId) || !nodeMap.has(nodeId)) {
            return '';
        }
        rendered.add(nodeId);
        const node = nodeMap.get(nodeId);
        const coupleIds = [nodeId].concat((spouseMap.get(nodeId) || []).filter(function (spouseId) {
            return !rendered.has(spouseId) && nodeMap.has(spouseId);
        }));
        coupleIds.forEach(function (id) {
            rendered.add(id);
        });
        let html = '<li role="treeitem" aria-expanded="' + String((children.get(nodeId) || []).length > 0) + '">'
            + '<div class="admin-family-tree-couple">' + coupleIds.map(function (id) {
                return renderFamilyTreeCard(nodeMap.get(id));
            }).join('<span class="admin-family-tree-spouse" role="img" aria-label="配偶">♥</span>') + '</div>';
        const childIds = (children.get(nodeId) || []).filter(function (childId) {
            return !rendered.has(childId);
        });
        if (childIds.length) {
            html += '<ul role="group">' + childIds.map(function (childId) {
                return renderFamilyTreeNode(childId, nodeMap, spouseMap, children, rendered);
            }).join('') + '</ul>';
        }
        return html + '</li>';
    }

    function renderFamilyTreeCard(node) {
        const name = node.name || ('节点 #' + node.id);
        const years = [node.birthYear == null ? '?' : node.birthYear,
            node.deathYear == null ? '?' : node.deathYear].join('–');
        const gender = node.gender === 1 ? '♂' : node.gender === 2 ? '♀' : '';
        const generation = node.generation == null ? '?' : node.generation;
        const charCount = Math.max(Array.from(name).length, 1);
        const charHeight = Math.min(26, Math.floor(84 / charCount));
        const fontSize = Math.max(9, Math.min(21, charHeight - 4));
        const deceased = node.deathYear != null;
        const cardClass = 'admin-family-tree-card'
            + (deceased ? ' deceased' : '') + (node.missing ? ' missing' : '');
        const label = name + '，' + (node.genderLabel || '未知性别') + '，生卒年 ' + years + '，第 ' + generation + ' 世';
        return '<div class="' + cardClass + '" title="' + FT.escapeAttr(label) + '">'
            + '<div class="admin-family-tree-generation">第 ' + FT.escapeHtml(generation) + ' 世</div>'
            + '<div class="admin-family-tree-name" style="font-size:' + fontSize + 'px">'
            + FT.escapeHtml(name) + '</div>'
            + '<div class="admin-family-tree-meta"><span class="admin-family-tree-gender">'
            + FT.escapeHtml(gender) + '</span><span class="admin-family-tree-years">'
            + FT.escapeHtml(years) + '</span></div></div>';
    }

    function compareTreeNodes(left, right) {
        return num(left.generation) - num(right.generation)
            || String(left.name || '').localeCompare(String(right.name || ''), 'zh-CN')
            || num(left.id) - num(right.id);
    }

    function renderNetworkDetail() {
        const data = state.network;
        if (!data) {
            return;
        }
        const family = data.family || {};
        let html = '<div class="graph-detail-head"><h3>' + FT.escapeHtml(family.name || ('家族 #' + family.id))
            + '</h3><span class="admin-hint">人物网络</span></div>';
        html += '<div class="mini-stats">' + miniStat((data.nodes || []).length, '人物')
            + miniStat((data.relations || []).length, '族内关系')
            + miniStat((data.crossFamilyLinks || []).length, '跨族链接') + '</div>';
        if (data.truncated) {
            html += '<div class="insp-truncated">跨族链接较多，仅展示前 5000 条。</div>';
        }
        html += '<div class="section-title">人物节点</div>' + renderNodes(data.nodes || []);
        html += '<div class="section-title">族内关系</div>' + renderRelations(data.relations || []);
        html += '<div class="section-title">跨族链接</div>' + renderLinks(data.crossFamilyLinks || []);
        $('graph-detail').innerHTML = html;
    }

    function renderRelations(relations) {
        if (!relations.length) {
            return '<div class="detail-empty">暂无族内关系</div>';
        }
        const nodes = (state.network.nodes || []);
        let html = '<table class="graph-table"><thead><tr><th>起点</th><th>关系</th><th>终点</th></tr></thead><tbody>';
        relations.forEach(function (relation) {
            const from = nodes.find(function (node) {
                return node.id === relation.fromNodeId;
            });
            const to = nodes.find(function (node) {
                return node.id === relation.toNodeId;
            });
            html += '<tr><td>' + FT.escapeHtml(from ? from.name : ('#' + relation.fromNodeId)) + '</td><td>'
                + FT.escapeHtml(relation.relationTypeLabel || '-') + '</td><td>'
                + FT.escapeHtml(to ? to.name : ('#' + relation.toNodeId)) + '</td></tr>';
        });
        return html + '</tbody></table>';
    }

    function renderNodes(nodes) {
        if (!nodes.length) {
            return '<div class="detail-empty">暂无端点节点</div>';
        }
        let html = '<table class="graph-table"><thead><tr>'
            + '<th>节点</th><th>所属家族</th><th>性别</th><th>生卒年</th><th>世代</th></tr></thead><tbody>';
        nodes.forEach(function (node) {
            const years = [node.birthYear == null ? '?' : node.birthYear,
                node.deathYear == null ? '?' : node.deathYear].join(' - ');
            const name = FT.escapeHtml(node.name || ('节点 #' + node.id));
            html += '<tr><td>' + name + (node.missing ? '<span class="graph-issue">节点缺失</span>' : '')
                + '<div class="admin-hint">#' + FT.escapeHtml(node.id) + '</div></td>'
                + '<td>' + FT.escapeHtml(node.familyName || '-') + '</td>'
                + '<td>' + FT.escapeHtml(node.genderLabel || '-') + '</td>'
                + '<td>' + FT.escapeHtml(years) + '</td>'
                + '<td>' + FT.escapeHtml(node.generation == null ? '-' : node.generation) + '</td></tr>';
        });
        return html + '</tbody></table>';
    }

    function renderLinks(links) {
        if (!links.length) {
            return '<div class="detail-empty">该家族暂无跨族链接</div>';
        }
        let html = '<table class="graph-table"><thead><tr>'
            + '<th>两端节点</th><th>类型</th><th>来源</th><th>确认人</th><th>成链时间</th><th>异常</th>'
            + '</tr></thead><tbody>';
        links.forEach(function (link) {
            const endpoints = FT.escapeHtml(link.srcNodeName || ('#' + link.srcNodeId)) + '<div class="admin-hint">'
                + FT.escapeHtml(link.dstNodeName || ('#' + link.dstNodeId)) + '</div>';
            const issues = (link.issues || []).map(function (issue) {
                return '<span class="graph-issue">' + FT.escapeHtml(issue.description || issue.code) + '</span>';
            }).join('');
            html += '<tr><td>' + endpoints + '</td>'
                + '<td>' + FT.escapeHtml(link.linkTypeLabel || TYPE_LABELS[link.linkType] || '-') + '</td>'
                + '<td>' + FT.escapeHtml(link.sourceLabel || '-') + '</td>'
                + '<td>' + FT.escapeHtml(link.confirmedBy == null ? '系统自动' : link.confirmedBy) + '</td>'
                + '<td>' + FT.escapeHtml(formatTime(link.createTime)) + '</td>'
                + '<td>' + (issues || '<span class="admin-hint">正常</span>') + '</td></tr>';
        });
        return html + '</tbody></table>';
    }

    function miniStat(value, label) {
        return '<div class="mini-stat"><b>' + FT.escapeHtml(num(value)) + '</b><span>'
            + FT.escapeHtml(label) + '</span></div>';
    }

    FT.AdminGraph = {
        init: init,
        onEnter: onEnter
    };
})();
