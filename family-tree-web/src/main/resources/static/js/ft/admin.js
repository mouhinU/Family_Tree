/**
 * 族谱前端 - 平台管理模块
 * 平台（系统）管理员页：跨家族总览、家族概况分页列表、家族治理（改名 / 冻结 / 归档 / 转让族长）、
 * 家族级配置读写、平台级配置读写、数据巡检与跨家族操作日志检索。
 *
 * 独立于 index.html 的模态体系，仅依赖 core.js / api.js / modals.js（后者提供 FT.confirm）。
 *
 * @author Family-Tree
 * @date 2026-11-15
 */
(function () {
    'use strict';

    const FT = window.FT;

    /** 家族列表默认每页条数 */
    const PAGE_SIZE = 20;
    /** 日志家族下拉一次取回的家族条数，与后端 MAX_PAGE_SIZE 一致 */
    const LOG_FILTER_OPTION_SIZE = 100;
    /** 搜索关键词最大长度，与后端 AdminFamilyController 保持一致 */
    const MAX_KEYWORD_LENGTH = 50;
    /** 布尔配置项的展示文案 */
    const BOOL_TRUE_LABEL = '开启';
    const BOOL_FALSE_LABEL = '关闭';
    /** 用户列表账号类型文案 */
    const ACCOUNT_TYPE_LABELS = {AUTO: '系统代开', REAL: '真人注册'};
    /** 页签标识 */
    const TAB_FAMILY = 'family';
    const TAB_USER = 'user';
    const TAB_INSPECTION = 'inspection';
    const TAB_SETTING = 'setting';
    const TAB_LOG = 'log';
    /** 日志「对象」列的取值文案：target_type 已由后端白名单产出，未知值原样展示 */
    const LOG_TARGET_LABELS = {node: '节点', relation: '关系', family: '家族', user: '账号'};
    /** 巡检严重级别到样式类名的白名单，避免把服务端取值直接拼进 class */
    const SEVERITY_TAG_CLASSES = {HIGH: 'sev-high', MEDIUM: 'sev-medium', LOW: 'sev-low'};
    /** 家族状态白名单：编码 → 展示名与样式类名 */
    const FAMILY_STATUS = {
        NORMAL: {label: '正常', tagClass: 'st-normal'},
        FROZEN: {label: '冻结', tagClass: 'st-frozen'},
        ARCHIVED: {label: '已归档', tagClass: 'st-archived'}
    };
    /** 家族状态操作按钮文案 */
    const GOV_ACTION_LABELS = {FROZEN: '冻结家族', ARCHIVED: '归档家族', NORMAL: '恢复为正常'};
    /** 家族状态操作的确认提示 */
    const GOV_HINTS = {
        FROZEN: '冻结后成员只能浏览，所有新增与修改都会被拒绝。',
        ARCHIVED: '归档后该家族对普通成员整体下线，仅平台管理员可查看。',
        NORMAL: '恢复后该家族读写正常。'
    };
    /** 家族名称最大长度，与后端 FamilyTreeConsts.MAX_FAMILY_NAME_LENGTH 一致 */
    const MAX_FAMILY_NAME_LENGTH = 100;

    var state = {
        tab: TAB_FAMILY,
        keyword: '',
        page: 1,
        total: 0,
        totalPages: 1,
        selectedId: null,
        settings: [],
        originalValues: {},
        detail: null,
        userKeyword: '',
        userAccountType: 'ALL',
        userPage: 1,
        userTotal: 0,
        userTotalPages: 1,
        userSelectedId: null,
        userLoaded: false,
        userDetail: null,
        // 一次性口令只在重置成功的那一次返回，暂存于内存用于渲染，切换账号即丢弃
        resetResult: null,
        // 巡检报告为只读快照，切页签复用上一次结果，点「重新巡检」才重跑
        inspection: null,
        logFamilyId: '',
        logType: '',
        logKeyword: '',
        logStartDate: '',
        logEndDate: '',
        logPage: 1,
        logTotal: 0,
        logTotalPages: 1,
        // 筛选候选项（家族与类型）取自真实数据，首次进入日志页签时拉一次
        logFilterLoaded: false,
        // 平台配置为全站生效链的最高默认层，进入页签时拉取，保存后重新拉取
        platformSettings: [],
        platformOriginalValues: {}
    };

    function $(id) {
        return document.getElementById(id);
    }

    // LocalDateTime 序列化为 ISO 字符串，统一裁成 'YYYY-MM-DD HH:mm:ss'
    function formatTime(value) {
        if (!value) {
            return '-';
        }
        return String(value).replace('T', ' ').substring(0, 19);
    }

    function num(value) {
        return value == null ? 0 : value;
    }

    // ========== 初始化 ==========
    async function boot() {
        var savedToken = sessionStorage.getItem('csrfToken');
        if (savedToken) {
            FT.initCsrfToken(savedToken);
        }
        var res;
        try {
            res = await FT.api('/api/auth/me');
        } catch (e) {
            return;
        }
        if (res.code !== 200) {
            window.location.href = '/login.html';
            return;
        }
        if (res.data.csrfToken) {
            FT.initCsrfToken(res.data.csrfToken);
            sessionStorage.setItem('csrfToken', res.data.csrfToken);
        }
        // 白名单之外的账号仅看到提示：真正的权限裁决在服务端切面，前端隐藏只是体验
        $('admin-user').textContent = res.data.nickname || '';
        if (!res.data.isSystemAdmin) {
            $('admin-denied').style.display = 'block';
            return;
        }
        $('admin-content').style.display = 'block';
        bindEvents();
        await Promise.all([loadOverview(), loadFamilies(1)]);
    }

    function bindEvents() {
        $('tab-families').addEventListener('click', function () {
            switchTab(TAB_FAMILY);
        });
        $('tab-users').addEventListener('click', function () {
            switchTab(TAB_USER);
        });
        $('tab-inspection').addEventListener('click', function () {
            switchTab(TAB_INSPECTION);
        });
        $('tab-settings').addEventListener('click', function () {
            switchTab(TAB_SETTING);
        });
        $('tab-logs').addEventListener('click', function () {
            switchTab(TAB_LOG);
        });
        $('btn-platform-reload').addEventListener('click', function () {
            loadPlatformSettings(true);
        });
        $('btn-platform-save').addEventListener('click', function () {
            var changed = collectPlatformChanges();
            if (changed.length === 0) {
                return;
            }
            FT.confirm(describePlatformChanges(changed), function () {
                savePlatformSettings(changed);
            }, {title: '确认修改平台配置'});
        });
        $('btn-log-search').addEventListener('click', function () {
            searchLogsNow();
        });
        $('btn-log-reset').addEventListener('click', function () {
            $('log-keyword').value = '';
            $('log-start').value = '';
            $('log-end').value = '';
            $('log-family').value = '';
            $('log-type').value = '';
            applyLogFilters();
            loadLogs(1);
        });
        $('log-keyword').addEventListener('keydown', function (evt) {
            if (evt.key === 'Enter') {
                searchLogsNow();
            }
        });
        $('log-family').addEventListener('change', function () {
            applyLogFilters();
            loadLogs(1);
        });
        $('log-type').addEventListener('change', function () {
            applyLogFilters();
            loadLogs(1);
        });
        $('btn-inspection').addEventListener('click', function () {
            loadInspection(true);
        });
        $('btn-gen-repair').addEventListener('click', function () {
            FT.confirm('将扫描全平台所有家族：未维护辈分的家族按树结构从根节点逐级重算「第几世」并直接落库，'
                + '已登记辈分的家族以现有辈分为准不受影响。修复结果会写入平台操作日志，确定执行？', function () {
                runGenerationRepair();
            });
        });
        $('btn-search').addEventListener('click', function () {
            searchNow();
        });
        $('btn-reset').addEventListener('click', function () {
            $('family-search').value = '';
            searchNow();
        });
        $('family-search').addEventListener('keydown', function (evt) {
            if (evt.key === 'Enter') {
                searchNow();
            }
        });
        $('btn-user-search').addEventListener('click', function () {
            searchUsersNow();
        });
        $('btn-user-reset').addEventListener('click', function () {
            $('user-search').value = '';
            $('user-account-type').value = 'ALL';
            state.userAccountType = 'ALL';
            searchUsersNow();
        });
        $('user-search').addEventListener('keydown', function (evt) {
            if (evt.key === 'Enter') {
                searchUsersNow();
            }
        });
        $('user-account-type').addEventListener('change', function () {
            state.userAccountType = this.value;
            loadUsers(1);
        });
        $('btn-admin-logout').addEventListener('click', async function () {
            try {
                await FT.api('/api/auth/logout', {method: 'POST'});
            } catch (e) {
                // 登出失败仍需离开本页，避免管理员停留在已失效会话上
            }
            window.location.href = '/login.html';
        });
    }

    function switchTab(tab) {
        state.tab = tab;
        var isFamily = tab === TAB_FAMILY;
        var isUser = tab === TAB_USER;
        var isInspection = tab === TAB_INSPECTION;
        var isSetting = tab === TAB_SETTING;
        var isLog = tab === TAB_LOG;
        if (!isUser) {
            // 离开用户页签即丢弃一次性口令，不让口令长期驻留在页面上
            state.resetResult = null;
        }
        $('pane-families').style.display = isFamily ? '' : 'none';
        $('pane-users').style.display = isUser ? '' : 'none';
        $('pane-inspection').style.display = isInspection ? '' : 'none';
        $('pane-settings').style.display = isSetting ? '' : 'none';
        $('pane-logs').style.display = isLog ? '' : 'none';
        $('tab-families').classList.toggle('on', isFamily);
        $('tab-users').classList.toggle('on', isUser);
        $('tab-inspection').classList.toggle('on', isInspection);
        $('tab-settings').classList.toggle('on', isSetting);
        $('tab-logs').classList.toggle('on', isLog);
        // 巡检报告、平台配置与日志检索按项铺开，不需要右侧详情栏
        var solo = isInspection || isSetting || isLog;
        $('admin-layout').classList.toggle('solo', solo);
        $('detail-pane').style.display = solo ? 'none' : '';
        if (isFamily) {
            renderFamilyPlaceholder();
            return;
        }
        if (isUser) {
            if (!state.userLoaded) {
                loadUsers(1);
            }
            renderUserPlaceholder();
            return;
        }
        if (isLog) {
            loadLogs(1);
            return;
        }
        if (isSetting) {
            loadPlatformSettings(true);
            return;
        }
        loadInspection(false);
    }

    function renderFamilyPlaceholder() {
        if (state.selectedId == null || !state.detail) {
            $('detail-pane').innerHTML = '<h2>家族详情</h2>'
                + '<div class="detail-empty">点击左侧任一家族查看概况与配置</div>';
            return;
        }
        renderDetail();
    }

    function renderUserPlaceholder() {
        if (state.userSelectedId == null || !state.userDetail) {
            $('detail-pane').innerHTML = '<h2>账号详情</h2>'
                + '<div class="detail-empty">点击左侧任一账号查看概况</div>';
            return;
        }
        renderUserDetail();
    }

    function searchNow() {
        var raw = ($('family-search').value || '').trim();
        if (raw.length > MAX_KEYWORD_LENGTH) {
            FT.toast('搜索关键词不能超过 ' + MAX_KEYWORD_LENGTH + ' 个字符', 'warning');
            return;
        }
        state.keyword = raw;
        loadFamilies(1);
    }

    function searchUsersNow() {
        var raw = ($('user-search').value || '').trim();
        if (raw.length > MAX_KEYWORD_LENGTH) {
            FT.toast('搜索关键词不能超过 ' + MAX_KEYWORD_LENGTH + ' 个字符', 'warning');
            return;
        }
        state.userKeyword = raw;
        loadUsers(1);
    }

    // ========== 总览 ==========
    async function loadOverview() {
        var res = await FT.api('/api/admin/overview');
        if (res.code !== 200 || !res.data) {
            return;
        }
        var d = res.data;
        var cards = [
            {label: '家族总数', value: num(d.familyCount), extra: '近 ' + num(d.recentWindowDays) + ' 天新增 ' + num(d.recentFamilyCount)},
            {label: '注册用户', value: num(d.userCount), extra: '其中系统代开账号 ' + num(d.autoAccountCount)},
            {label: '族谱节点', value: num(d.nodeCount), extra: '全平台累计'},
            {label: '亲属关系', value: num(d.relationCount), extra: '全平台累计'}
        ];
        var html = '';
        cards.forEach(function (card) {
            html += '<div class="stat-card">' +
                '<div class="stat-label">' + FT.escapeHtml(card.label) + '</div>' +
                '<div class="stat-value">' + FT.escapeHtml(card.value) + '</div>' +
                '<div class="stat-extra">' + FT.escapeHtml(card.extra) + '</div>' +
                '</div>';
        });
        $('overview-cards').innerHTML = html;
    }

    // ========== 家族列表 ==========
    async function loadFamilies(page) {
        var url = '/api/admin/families?page=' + page + '&size=' + PAGE_SIZE;
        if (state.keyword) {
            url += '&keyword=' + encodeURIComponent(state.keyword);
        }
        var res = await FT.api(url);
        if (res.code !== 200) {
            return;
        }
        var data = res.data || {};
        state.page = num(data.page) || page;
        state.total = num(data.total);
        state.totalPages = num(data.totalPages) || 1;

        $('list-count').textContent = '共 ' + state.total + ' 个家族';
        $('family-tbody').innerHTML = renderRows(data.records || []);
        renderPager();
    }

    function renderRows(rows) {
        if (rows.length === 0) {
            return '<tr><td colspan="8" style="text-align:center;color:#999;">没有匹配的家族</td></tr>';
        }
        var html = '';
        rows.forEach(function (row) {
            var creator = row.creatorNickname || '-';
            if (row.creatorPhone) {
                creator += '（' + row.creatorPhone + '）';
            }
            var alive = num(row.nodeCount) - num(row.deceasedNodeCount);
            html += '<tr data-family-id="' + row.id + '"' + (row.id === state.selectedId ? ' class="active"' : '') + '>' +
                '<td>' + FT.escapeHtml(row.name || '-') + statusTag(row.status) + '</td>' +
                '<td>' + FT.escapeHtml(row.hallName || '-') + '</td>' +
                '<td>' + FT.escapeHtml(row.inviteCode || '-') + '</td>' +
                '<td>' + FT.escapeHtml(creator) + '</td>' +
                '<td class="num">' + FT.escapeHtml(num(row.memberCount)) + '</td>' +
                '<td class="num">' + FT.escapeHtml(num(row.nodeCount)) + '<span class="admin-hint"> / 存 ' + alive + '</span></td>' +
                '<td class="num">' + FT.escapeHtml(num(row.relationCount)) + '</td>' +
                '<td>' + FT.escapeHtml(formatTime(row.createTime)) + '</td>' +
                '</tr>';
        });
        return html;
    }

    function renderPager() {
        var el = $('family-pager');
        el.innerHTML = '<button type="button" id="pager-prev"' + (state.page <= 1 ? ' disabled' : '') + '>上一页</button>' +
            '<span>第 ' + state.page + ' / ' + state.totalPages + ' 页</span>' +
            '<button type="button" id="pager-next"' + (state.page >= state.totalPages ? ' disabled' : '') + '>下一页</button>';
        $('pager-prev').addEventListener('click', function () {
            loadFamilies(state.page - 1);
        });
        $('pager-next').addEventListener('click', function () {
            loadFamilies(state.page + 1);
        });
    }

    // 家族状态徽标：非正常状态才占位，正常家族保持列表清爽
    function statusTag(code) {
        var status = FAMILY_STATUS[code];
        if (!status || code === 'NORMAL') {
            return '';
        }
        return '<span class="status-tag ' + status.tagClass + '">' + FT.escapeHtml(status.label) + '</span>';
    }

    // 列表点击采用事件委托，分页重绘后无需重新绑定
    document.addEventListener('click', function (evt) {
        var tr = evt.target.closest ? evt.target.closest('#family-tbody tr[data-family-id]') : null;
        if (tr) {
            var familyId = parseInt(tr.getAttribute('data-family-id'), 10);
            if (!isNaN(familyId)) {
                openDetail(familyId);
            }
            return;
        }
        var userTr = evt.target.closest ? evt.target.closest('#user-tbody tr[data-user-id]') : null;
        if (userTr) {
            var userId = parseInt(userTr.getAttribute('data-user-id'), 10);
            if (!isNaN(userId)) {
                openUserDetail(userId);
            }
        }
    });

    // ========== 用户列表 ==========
    async function loadUsers(page) {
        var url = '/api/admin/users?page=' + page + '&size=' + PAGE_SIZE
            + '&accountType=' + encodeURIComponent(state.userAccountType);
        if (state.userKeyword) {
            url += '&keyword=' + encodeURIComponent(state.userKeyword);
        }
        var res = await FT.api(url);
        if (res.code !== 200) {
            return;
        }
        var data = res.data || {};
        state.userLoaded = true;
        state.userPage = num(data.page) || page;
        state.userTotal = num(data.total);
        state.userTotalPages = num(data.totalPages) || 1;

        $('user-list-count').textContent = '共 ' + state.userTotal + ' 个账号';
        $('user-tbody').innerHTML = renderUserRows(data.records || []);
        renderUserPager();
    }

    function renderUserRows(rows) {
        if (rows.length === 0) {
            return '<tr><td colspan="7" style="text-align:center;color:#999;">没有匹配的账号</td></tr>';
        }
        var html = '';
        rows.forEach(function (row) {
            var typeLabel = row.autoAccount ? ACCOUNT_TYPE_LABELS.AUTO : ACCOUNT_TYPE_LABELS.REAL;
            var type = FT.escapeHtml(typeLabel);
            if (row.mustChangePassword) {
                type += '<span class="admin-hint"> 待改密</span>';
            }
            html += '<tr data-user-id="' + row.id + '"' + (row.id === state.userSelectedId ? ' class="active"' : '') + '>' +
                '<td>' + FT.escapeHtml(row.username || '-') + '</td>' +
                '<td>' + FT.escapeHtml(row.nickname || '-') + '</td>' +
                '<td>' + type + '</td>' +
                '<td class="num">' + FT.escapeHtml(num(row.familyCount)) + '</td>' +
                '<td>' + FT.escapeHtml(row.currentFamilyName || '-') + '</td>' +
                '<td>' + FT.escapeHtml(row.phone || row.email || '-') + '</td>' +
                '<td>' + FT.escapeHtml(formatTime(row.createTime)) + '</td>' +
                '</tr>';
        });
        return html;
    }

    function renderUserPager() {
        var el = $('user-pager');
        el.innerHTML = '<button type="button" id="user-pager-prev"' + (state.userPage <= 1 ? ' disabled' : '') + '>上一页</button>' +
            '<span>第 ' + state.userPage + ' / ' + state.userTotalPages + ' 页</span>' +
            '<button type="button" id="user-pager-next"' + (state.userPage >= state.userTotalPages ? ' disabled' : '') + '>下一页</button>';
        $('user-pager-prev').addEventListener('click', function () {
            loadUsers(state.userPage - 1);
        });
        $('user-pager-next').addEventListener('click', function () {
            loadUsers(state.userPage + 1);
        });
    }

    // ========== 操作日志 ==========
    async function loadLogs(page) {
        await loadLogFilterOptions();
        var url = '/api/admin/logs?page=' + page + '&size=' + PAGE_SIZE;
        if (state.logFamilyId) {
            url += '&familyId=' + encodeURIComponent(state.logFamilyId);
        }
        if (state.logType) {
            url += '&operationType=' + encodeURIComponent(state.logType);
        }
        if (state.logKeyword) {
            url += '&keyword=' + encodeURIComponent(state.logKeyword);
        }
        if (state.logStartDate) {
            url += '&startDate=' + encodeURIComponent(state.logStartDate);
        }
        if (state.logEndDate) {
            url += '&endDate=' + encodeURIComponent(state.logEndDate);
        }
        var res = await FT.api(url);
        if (res.code !== 200) {
            return;
        }
        var data = res.data || {};
        state.logPage = num(data.page) || page;
        state.logTotal = num(data.total);
        state.logTotalPages = num(data.totalPages) || 1;

        $('log-list-count').textContent = '共 ' + state.logTotal + ' 条日志';
        $('log-tbody').innerHTML = renderLogRows(data.records || []);
        renderLogPager();
    }

    // 家族与类型候选项都取自真实数据：类型是日志表去重结果，家族按后端单页上限一次取满
    async function loadLogFilterOptions() {
        if (state.logFilterLoaded) {
            return;
        }
        var typeRes = await FT.api('/api/admin/log-types');
        if (typeRes.code === 200) {
            var typeOptions = '<option value="">全部类型</option>';
            (typeRes.data || []).forEach(function (code) {
                var label = FT.escapeHtml(code || '');
                typeOptions += '<option value="' + label + '">' + label + '</option>';
            });
            $('log-type').innerHTML = typeOptions;
        }
        var familyRes = await FT.api('/api/admin/families?page=1&size=' + LOG_FILTER_OPTION_SIZE);
        if (familyRes.code === 200) {
            var records = (familyRes.data && familyRes.data.records) || [];
            var familyOptions = '<option value="">全部家族</option>';
            records.forEach(function (family) {
                var idLabel = FT.escapeHtml(String(family.id));
                familyOptions += '<option value="' + idLabel + '">'
                    + FT.escapeHtml(family.name || ('编号 ' + idLabel)) + '</option>';
            });
            $('log-family').innerHTML = familyOptions;
        }
        // 候选项重绘后原选中值可能已不存在，回读一次保证下拉与 state 一致
        $('log-type').value = state.logType;
        $('log-family').value = state.logFamilyId;
        state.logFilterLoaded = true;
    }

    function applyLogFilters() {
        state.logFamilyId = $('log-family').value || '';
        state.logType = $('log-type').value || '';
        state.logKeyword = ($('log-keyword').value || '').trim();
        state.logStartDate = $('log-start').value || '';
        state.logEndDate = $('log-end').value || '';
    }

    function searchLogsNow() {
        var raw = ($('log-keyword').value || '').trim();
        if (raw.length > MAX_KEYWORD_LENGTH) {
            FT.toast('搜索关键词不能超过 ' + MAX_KEYWORD_LENGTH + ' 个字符', 'warning');
            return;
        }
        if (state.logStartDate && state.logEndDate && state.logStartDate > state.logEndDate) {
            FT.toast('开始日期不能晚于结束日期', 'warning');
            return;
        }
        applyLogFilters();
        loadLogs(1);
    }

    function renderLogRows(rows) {
        if (rows.length === 0) {
            return '<tr><td colspan="7" style="text-align:center;color:#999;">没有符合条件的日志</td></tr>';
        }
        var html = '';
        rows.forEach(function (row) {
            var target = row.targetType
                ? (LOG_TARGET_LABELS[row.targetType] || row.targetType) + ' ' + (row.targetId == null ? '-' : row.targetId)
                : '-';
            html += '<tr>' +
                '<td>' + FT.escapeHtml(formatTime(row.createTime)) + '</td>' +
                '<td>' + FT.escapeHtml(row.username || '-') + '</td>' +
                '<td>' + FT.escapeHtml(row.operationType || '-') + '</td>' +
                '<td>' + FT.escapeHtml(row.operationDesc || '-') + '</td>' +
                '<td>' + FT.escapeHtml(row.familyName || (row.familyId == null ? '-' : '编号 ' + row.familyId)) + '</td>' +
                '<td>' + FT.escapeHtml(target) + '</td>' +
                '<td>' + FT.escapeHtml(row.ipAddress || '-') + '</td>' +
                '</tr>';
        });
        return html;
    }

    function renderLogPager() {
        var el = $('log-pager');
        el.innerHTML = '<button type="button" id="log-pager-prev"' + (state.logPage <= 1 ? ' disabled' : '') + '>上一页</button>' +
            '<span>第 ' + state.logPage + ' / ' + state.logTotalPages + ' 页</span>' +
            '<button type="button" id="log-pager-next"' + (state.logPage >= state.logTotalPages ? ' disabled' : '') + '>下一页</button>';
        $('log-pager-prev').addEventListener('click', function () {
            loadLogs(state.logPage - 1);
        });
        $('log-pager-next').addEventListener('click', function () {
            loadLogs(state.logPage + 1);
        });
    }

    // ========== 平台配置 ==========
    async function loadPlatformSettings() {
        var res = await FT.api('/api/admin/platform-settings');
        if (res.code !== 200) {
            $('platform-body').innerHTML = '<div class="detail-empty">平台配置加载失败</div>';
            return;
        }
        state.platformSettings = res.data || [];
        state.platformOriginalValues = {};
        state.platformSettings.forEach(function (item) {
            state.platformOriginalValues[item.key] = item.value;
        });
        renderPlatformSettings();
    }

    function renderPlatformSettings() {
        if (state.platformSettings.length === 0) {
            $('platform-body').innerHTML = '<div class="detail-empty">暂无可配置项</div>';
            return;
        }
        var html = '';
        state.platformSettings.forEach(function (item) {
            html += '<div class="setting-row">' +
                '<div class="setting-head"><span>' + FT.escapeHtml(item.label || item.key) + '</span>' +
                '<span class="setting-tag' + (item.overridden ? ' on' : '') + '">' +
                (item.overridden ? '已覆盖' : '默认') + '</span>' +
                (item.familyScoped ? '<span class="setting-tag">家族可覆盖</span>' : '') + '</div>' +
                '<div class="setting-desc">' + FT.escapeHtml(item.description || '') + '</div>' +
                '<div class="setting-field">' + renderField(item, 'platform-setting') +
                '<span class="admin-hint">默认值：' + FT.escapeHtml(item.defaultValue) + '</span></div>' +
                '</div>';
        });
        $('platform-body').innerHTML = html;
        document.querySelectorAll('#pane-settings [data-setting-key]').forEach(function (field) {
            field.addEventListener('change', refreshPlatformSaveState);
            field.addEventListener('input', refreshPlatformSaveState);
        });
        refreshPlatformSaveState();
    }

    function collectPlatformChanges() {
        var changed = [];
        document.querySelectorAll('#pane-settings [data-setting-key]').forEach(function (field) {
            var key = field.getAttribute('data-setting-key');
            var value = (field.value || '').trim();
            if (value !== String(state.platformOriginalValues[key] == null ? '' : state.platformOriginalValues[key])) {
                changed.push({key: key, value: value});
            }
        });
        return changed;
    }

    function describePlatformChanges(changed) {
        var byKey = {};
        state.platformSettings.forEach(function (item) {
            byKey[item.key] = item;
        });
        var parts = changed.map(function (c) {
            var item = byKey[c.key] || {};
            var from = state.platformOriginalValues[c.key];
            var to = c.value;
            if (item.valueType === 'BOOLEAN') {
                from = from === 'true' ? BOOL_TRUE_LABEL : BOOL_FALSE_LABEL;
                to = to === 'true' ? BOOL_TRUE_LABEL : BOOL_FALSE_LABEL;
            }
            return (item.label || c.key) + '：' + from + ' → ' + to;
        });
        return '平台配置对全平台生效，将执行以下修改：' + parts.join('；')
            + '。取值改回默认值即恢复默认，家族已单独覆盖的配置不受平台默认值影响。';
    }

    function refreshPlatformSaveState() {
        $('btn-platform-save').disabled = collectPlatformChanges().length === 0;
    }

    async function savePlatformSettings(changed) {
        var payload = {settings: {}};
        changed.forEach(function (c) {
            payload.settings[c.key] = c.value;
        });
        var res = await FT.api('/api/admin/platform-settings', {
            method: 'PUT',
            body: JSON.stringify(payload)
        });
        if (res.code !== 200) {
            return;
        }
        FT.toast('平台配置已保存', 'success');
        await loadPlatformSettings();
    }

    // ========== 用户详情 ==========
    async function openUserDetail(userId) {
        var isSameUser = state.userSelectedId === userId;
        state.userSelectedId = userId;
        if (!isSameUser) {
            state.resetResult = null;
        }
        var rows = document.querySelectorAll('#user-tbody tr[data-user-id]');
        rows.forEach(function (tr) {
            tr.classList.toggle('active', parseInt(tr.getAttribute('data-user-id'), 10) === userId);
        });

        var res = await FT.api('/api/admin/user/' + userId);
        if (res.code !== 200 || !res.data) {
            return;
        }
        state.userDetail = res.data;
        renderUserDetail();
    }

    function renderUserDetail() {
        var d = state.userDetail;
        var u = d.user || {};
        var families = d.families || [];
        var boundCount = families.filter(function (item) {
            return item.boundNodeId != null;
        }).length;
        var typeLabel = u.autoAccount ? ACCOUNT_TYPE_LABELS.AUTO : ACCOUNT_TYPE_LABELS.REAL;

        var html = '<h2>' + FT.escapeHtml(u.nickname || u.username || '账号详情') + '</h2>';
        html += '<dl class="kv-grid">';
        html += kv('编号', u.id);
        html += kv('用户名', u.username || '-');
        html += kv('账号类型', typeLabel);
        html += kv('下次登录改密', u.mustChangePassword ? '需要' : '不需要');
        html += kv('手机号', u.phone || '-');
        html += kv('邮箱', u.email || '-');
        html += kv('当前家族', u.currentFamilyName || '-');
        html += kv('注册时间', formatTime(u.createTime));
        html += kv('最近更新', formatTime(u.updateTime));
        html += '</dl>';

        html += '<div class="mini-stats">' +
            miniStat(families.length, '加入家族') +
            miniStat(boundCount, '标记本人') +
            '</div>';

        html += '<div class="section-title">账号安全</div>';
        html += '<div class="admin-actions">' +
            '<button class="ghost-btn" type="button" id="btn-unlock-user">解除登录锁定</button>' +
            '<button class="primary-btn" type="button" id="btn-reset-password">重置登录口令</button>' +
            '</div>';
        html += '<div id="reset-result"></div>';

        html += '<div class="section-title">所属家族</div>';
        html += renderUserFamilies(families);

        $('detail-pane').innerHTML = html;
        bindUserDetailEvents();
        renderResetResult();
    }

    function bindUserDetailEvents() {
        $('btn-reset-password').addEventListener('click', function () {
            var u = (state.userDetail || {}).user || {};
            if (u.id == null) {
                return;
            }
            FT.confirm('将把账号「' + (u.username || u.id) + '」的登录口令重置为随机一次性口令：'
                + '旧口令立即失效，该账号下次登录必须修改密码。', function () {
                resetUserPassword(u.id);
            }, {title: '确认重置登录口令'});
        });
        $('btn-unlock-user').addEventListener('click', function () {
            var u = (state.userDetail || {}).user || {};
            if (u.id == null) {
                return;
            }
            FT.confirm('将清空账号「' + (u.username || u.id) + '」的登录失败计数，'
                + '解除因连续输错口令触发的登录锁定。', function () {
                unlockUserAccount(u.id);
            }, {title: '确认解除登录锁定'});
        });
    }

    async function resetUserPassword(userId) {
        var res = await FT.api('/api/admin/user/' + userId + '/password/reset', {method: 'POST'});
        if (res.code !== 200 || !res.data) {
            return;
        }
        // 重新拉取详情以刷新「下次登录改密」，再把一次性口令回填到结果区
        var resetResult = res.data;
        await openUserDetail(userId);
        state.resetResult = resetResult;
        renderResetResult();
        await loadUsers(state.userPage);
        FT.toast('口令已重置，请立即转达本人', 'success');
    }

    async function unlockUserAccount(userId) {
        var res = await FT.api('/api/admin/user/' + userId + '/unlock', {method: 'POST'});
        if (res.code !== 200) {
            return;
        }
        FT.toast('已解除该账号的登录锁定', 'success');
    }

    function renderResetResult() {
        var box = $('reset-result');
        if (!box) {
            return;
        }
        var vo = state.resetResult;
        if (!vo) {
            box.innerHTML = '';
            return;
        }
        box.innerHTML = '<div class="reset-result">' +
            '<div>账号「' + FT.escapeHtml(vo.username || '-') + '」的新登录口令（仅此一次显示，关闭后不再可查）：</div>' +
            '<input class="reset-pwd" id="reset-pwd" readonly value="' + FT.escapeAttr(vo.newPassword || '') + '" ' +
            'aria-label="一次性登录口令">' +
            '<div class="admin-hint">点击口令框可全选，请复制后通过可靠渠道转达本人。</div>' +
            '</div>';
        var input = $('reset-pwd');
        input.addEventListener('click', function () {
            input.select();
        });
    }

    function renderUserFamilies(families) {
        if (families.length === 0) {
            return '<div class="detail-empty">该账号尚未加入任何家族</div>';
        }
        var html = '<table class="admin-table"><thead><tr>' +
            '<th>家族</th><th>角色</th><th>本人节点</th><th>加入时间</th>' +
            '</tr></thead><tbody>';
        families.forEach(function (item) {
            var name = FT.escapeHtml(item.familyName || ('#' + item.familyId));
            if (item.current) {
                name += '<span class="admin-hint"> 当前</span>';
            }
            html += '<tr><td>' + name + '</td>' +
                '<td>' + FT.escapeHtml(item.roleLabel || item.role || '-') + '</td>' +
                '<td>' + FT.escapeHtml(item.boundNodeName || '-') + '</td>' +
                '<td>' + FT.escapeHtml(formatTime(item.joinedTime)) + '</td></tr>';
        });
        return html + '</tbody></table>';
    }

    // ========== 数据巡检 ==========
    async function loadInspection(force) {
        if (state.inspection && !force) {
            renderInspection();
            return;
        }
        $('insp-body').innerHTML = '<div class="detail-empty">正在巡检，请稍候…</div>';
        var res;
        try {
            res = await FT.api('/api/admin/inspection');
        } catch (e) {
            // FT.api 已统一提示网络与权限错误，这里只需把面板复位
            $('insp-body').innerHTML = '<div class="detail-empty">巡检未完成，请点击「重新巡检」重试</div>';
            return;
        }
        if (res.code !== 200 || !res.data) {
            $('insp-body').innerHTML = '<div class="detail-empty">巡检未完成，请点击「重新巡检」重试</div>';
            return;
        }
        state.inspection = res.data;
        renderInspection();
    }

    // 一键修复全平台世代：仅重算未维护辈分的家族，单家族失败不影响其余家族
    async function runGenerationRepair() {
        var btn = $('btn-gen-repair');
        btn.disabled = true;
        try {
            var res = await FT.api('/api/admin/generation/repair-all', {method: 'POST'});
            if (res.code !== 200 || !res.data) {
                FT.toast('世代修复未完成，请稍后重试', 'error');
                return;
            }
            var d = res.data;
            var msg = '世代修复完成：扫描 ' + num(d.scannedFamilies) + ' 个家族，修复 '
                + num(d.repairedFamilies) + ' 个 / ' + num(d.updatedNodes) + ' 个节点，跳过已维护辈分 '
                + num(d.skippedManagedFamilies) + ' 个';
            if (num(d.failedFamilies) > 0) {
                FT.toast(msg + '，失败 ' + num(d.failedFamilies) + ' 个（详情见平台操作日志）', 'warning');
            } else {
                FT.toast(msg, 'success');
            }
            loadInspection(true);
        } finally {
            btn.disabled = false;
        }
    }

    function renderInspection() {
        var report = state.inspection;
        var items = report.items || [];
        var html = '<div class="mini-stats">'
            + miniStat(num(report.highCount), '高危异常记录')
            + miniStat(num(report.mediumCount), '中危异常记录')
            + miniStat(num(report.lowCount), '低危异常记录')
            + '</div>';
        html += '<div class="admin-hint" style="margin-bottom:12px;">共 ' + num(report.checkCount)
            + ' 项检查，' + num(report.problemCheckCount) + ' 项发现问题 · 生成于 '
            + formatTime(report.generatedTime) + '</div>';
        items.forEach(function (item) {
            html += renderItemCard(item);
        });
        $('insp-body').innerHTML = html;
    }

    function renderItemCard(item) {
        var total = num(item.total);
        var samples = item.samples || [];
        var clean = total === 0;
        var html = '<div class="insp-item' + (clean ? ' clean' : '') + '">';
        html += '<div class="insp-item-head">'
            + '<span class="setting-tag ' + severityTagClass(item.severity) + '">'
            + FT.escapeHtml(item.severityLabel || '') + '</span>'
            + '<span class="insp-item-title">' + FT.escapeHtml(item.title || '') + '</span>'
            + '<span class="insp-item-count">' + (clean ? '未发现问题' : total + ' 条') + '</span>'
            + '</div>';
        html += '<div class="insp-item-desc">' + FT.escapeHtml(item.description || '') + '</div>';
        if (!clean) {
            html += renderSampleTable(samples);
            if (item.truncated) {
                html += '<div class="insp-truncated">样本仅展示前 ' + samples.length + ' 条，另有 '
                    + (total - samples.length) + ' 条未列出</div>';
            }
        }
        return html + '</div>';
    }

    function renderSampleTable(samples) {
        if (samples.length === 0) {
            return '';
        }
        var html = '<table class="admin-table insp-samples"><thead><tr>'
            + '<th>记录ID</th><th>主体</th><th>详情</th></tr></thead><tbody>';
        samples.forEach(function (sample) {
            html += '<tr><td>' + FT.escapeHtml(sample.refId == null ? '-' : sample.refId) + '</td>'
                + '<td>' + FT.escapeHtml(sample.subject || '-') + '</td>'
                + '<td>' + FT.escapeHtml(sample.detail || '-') + '</td></tr>';
        });
        return html + '</tbody></table>';
    }

    function severityTagClass(severity) {
        return SEVERITY_TAG_CLASSES[severity] || 'sev-low';
    }

    // ========== 家族详情 ==========
    async function openDetail(familyId) {
        state.selectedId = familyId;
        var rows = document.querySelectorAll('#family-tbody tr[data-family-id]');
        rows.forEach(function (tr) {
            tr.classList.toggle('active', parseInt(tr.getAttribute('data-family-id'), 10) === familyId);
        });

        var res = await FT.api('/api/admin/family/' + familyId);
        if (res.code !== 200 || !res.data) {
            return;
        }
        state.detail = res.data;
        state.settings = res.data.settings || [];
        state.originalValues = {};
        state.settings.forEach(function (item) {
            state.originalValues[item.key] = item.value;
        });
        renderDetail();
    }

    function renderDetail() {
        var d = state.detail;
        var f = d.family || {};
        var html = '<h2>' + FT.escapeHtml(f.name || '家族详情') + '</h2>';

        html += '<dl class="kv-grid">';
        html += kv('编号', f.id);
        html += kv('状态', statusLabel(f.status));
        html += kv('堂号', f.hallName || '-');
        html += kv('祖籍', f.ancestralHome || '-');
        html += kv('邀请码', f.inviteCode || '-');
        html += kv('创建人', (f.creatorNickname || '-') + (f.creatorPhone ? '（' + f.creatorPhone + '）' : ''));
        html += kv('辈分排布', (f.generationCols || '-') + ' 列 × ' + (f.generationRows || '-') + ' 行');
        html += kv('创建时间', formatTime(f.createTime));
        html += kv('最近更新', formatTime(f.updateTime));
        html += '</dl>';

        html += '<div class="mini-stats">' +
            miniStat(f.memberCount, '成员') +
            miniStat(f.nodeCount, '节点') +
            miniStat(f.deceasedNodeCount, '已故') +
            miniStat(f.relationCount, '关系') +
            miniStat(d.snapshotCount, '快照') +
            miniStat(d.photoCount, '相册') +
            '</div>';

        html += '<div class="section-title">家族治理'
            + (f.statusDescription ? '<span class="admin-hint">' + FT.escapeHtml(f.statusDescription) + '</span>' : '')
            + '</div>' + renderGovernForm();

        html += '<div class="section-title">平台配置</div>' + renderSettingsForm();
        html += '<div class="admin-actions">' +
            '<button class="ghost-btn" type="button" id="btn-reload-settings">放弃修改</button>' +
            '<button class="primary-btn" type="button" id="btn-save-settings" disabled>保存配置</button>' +
            '</div>';

        html += '<div class="section-title">最近操作' +
            (d.lastOperationTime ? '（至 ' + formatTime(d.lastOperationTime) + '）' : '') + '</div>';
        html += renderOperations(d.recentOperations || []);

        $('detail-pane').innerHTML = html;
        bindDetailEvents();
        refreshSaveState();
    }

    function kv(label, value) {
        return '<dt>' + FT.escapeHtml(label) + '</dt><dd>' + FT.escapeHtml(value == null ? '-' : value) + '</dd>';
    }

    function miniStat(value, label) {
        return '<div class="mini-stat"><b>' + FT.escapeHtml(num(value)) + '</b><span>' + FT.escapeHtml(label) + '</span></div>';
    }

    // 家族治理表单：改名、冻结 / 归档 / 恢复、转让族长
    function renderGovernForm() {
        var f = (state.detail && state.detail.family) || {};
        var current = FAMILY_STATUS[f.status] ? f.status : 'NORMAL';
        var html = '<div class="gov-row">'
            + '<input type="text" id="gov-name" maxlength="' + MAX_FAMILY_NAME_LENGTH + '" placeholder="家族名称"'
            + ' value="' + FT.escapeAttr(f.name || '') + '">'
            + '<button class="ghost-btn" type="button" id="btn-rename">重命名</button>'
            + '</div>';

        html += '<div class="gov-row gov-btns">';
        ['FROZEN', 'ARCHIVED', 'NORMAL'].forEach(function (target) {
            if (target === current) {
                return;
            }
            html += '<button class="ghost-btn" type="button" data-gov-status="' + target + '">'
                + FT.escapeHtml(GOV_ACTION_LABELS[target]) + '</button>';
        });
        html += '</div>';
        html += '<div class="admin-hint">冻结后家族只读，成员不可修改数据；归档后家族对成员整体下线，仅平台管理员可恢复。</div>';

        html += '<div class="gov-row">'
            + '<input type="number" id="gov-owner" min="1" placeholder="新族长账号编号">'
            + '<button class="ghost-btn" type="button" id="btn-transfer">转让族长</button>'
            + '</div>';
        return html;
    }

    function statusLabel(code) {
        var status = FAMILY_STATUS[code];
        return status ? status.label : FAMILY_STATUS.NORMAL.label;
    }

    function bindGovernEvents() {
        var nameInput = $('gov-name');
        var currentName = (state.detail && state.detail.family && state.detail.family.name) || '';

        $('btn-rename').addEventListener('click', function () {
            var value = (nameInput.value || '').trim();
            if (!value) {
                FT.toast('家族名称不能为空', 'warning');
                return;
            }
            if (value.length > MAX_FAMILY_NAME_LENGTH) {
                FT.toast('家族名称不能超过 ' + MAX_FAMILY_NAME_LENGTH + ' 个字符', 'warning');
                return;
            }
            if (value === currentName) {
                FT.toast('名称没有变化', 'warning');
                return;
            }
            FT.confirm('将家族「' + currentName + '」重命名为「' + value + '」？', function () {
                submitGovern('/api/admin/family/' + state.selectedId, {name: value}, '家族已重命名');
            }, {title: '确认重命名家族'});
        });

        document.querySelectorAll('#detail-pane [data-gov-status]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                var target = btn.getAttribute('data-gov-status');
                if (!GOV_HINTS[target]) {
                    return;
                }
                FT.confirm('家族「' + currentName + '」将变更为' + statusLabel(target) + '。' + GOV_HINTS[target], function () {
                    submitGovern('/api/admin/family/' + state.selectedId + '/status',
                        {status: target}, '家族状态已更新');
                }, {title: '确认变更家族状态'});
            });
        });

        $('btn-transfer').addEventListener('click', function () {
            var userId = parseInt($('gov-owner').value, 10);
            if (isNaN(userId) || userId <= 0) {
                FT.toast('请填写新族长的账号编号', 'warning');
                return;
            }
            FT.confirm('将家族「' + currentName + '」的族长转让给账号 ' + userId + '？原族长降为普通成员。', function () {
                submitGovern('/api/admin/family/' + state.selectedId + '/owner',
                    {userId: userId}, '族长已转让');
            }, {title: '确认转让族长'});
        });
    }

    async function submitGovern(url, payload, okMessage) {
        var res = await FT.api(url, {method: 'PUT', body: JSON.stringify(payload)});
        if (res.code !== 200) {
            return;
        }
        FT.toast(okMessage, 'success');
        await openDetail(state.selectedId);
        await loadFamilies(state.page);
    }

    function renderSettingsForm() {
        if (state.settings.length === 0) {
            return '<div class="detail-empty">暂无可配置项</div>';
        }
        var html = '';
        state.settings.forEach(function (item) {
            html += '<div class="setting-row">' +
                '<div class="setting-head"><span>' + FT.escapeHtml(item.label || item.key) + '</span>' +
                '<span class="setting-tag' + (item.overridden ? ' on' : '') + '">' +
                (item.overridden ? '已覆盖' : '默认') + '</span></div>' +
                '<div class="setting-desc">' + FT.escapeHtml(item.description || '') + '</div>' +
                '<div class="setting-field">' + renderField(item) +
                '<span class="admin-hint">默认值：' + FT.escapeHtml(item.defaultValue) + '</span></div>' +
                '</div>';
        });
        return html;
    }

    function renderField(item, idBase) {
        var key = FT.escapeAttr(item.key);
        // 同时给出 id 与 name：控件由配置键唯一确定，便于无障碍标注与表单语义
        // idBase 区分家族/平台两套面板，避免同键配置产生重复 id
        var identity = 'id="' + (idBase || 'setting') + '-' + key + '" name="' + (idBase || 'setting') + '-' + key + '"';
        if (item.valueType === 'BOOLEAN') {
            var isOn = item.value === 'true';
            return '<select data-setting-key="' + key + '" ' + identity + '>' +
                '<option value="true"' + (isOn ? ' selected' : '') + '>' + BOOL_TRUE_LABEL + '</option>' +
                '<option value="false"' + (isOn ? '' : ' selected') + '>' + BOOL_FALSE_LABEL + '</option>' +
                '</select>';
        }
        if (item.valueType === 'INTEGER') {
            var min = item.minValue == null ? '' : ' min="' + item.minValue + '"';
            var max = item.maxValue == null ? '' : ' max="' + item.maxValue + '"';
            return '<input type="number" step="1" data-setting-key="' + key + '" ' + identity + ' value="' +
                FT.escapeAttr(item.value) + '"' + min + max + '>';
        }
        return '<input type="text" data-setting-key="' + key + '" ' + identity + ' value="' + FT.escapeAttr(item.value) + '">';
    }

    function bindDetailEvents() {
        bindGovernEvents();
        var fields = document.querySelectorAll('#detail-pane [data-setting-key]');
        fields.forEach(function (field) {
            field.addEventListener('change', refreshSaveState);
            field.addEventListener('input', refreshSaveState);
        });
        $('btn-save-settings').addEventListener('click', function () {
            var changed = collectChanges();
            if (changed.length === 0) {
                return;
            }
            FT.confirm(describeChanges(changed), function () {
                saveSettings(changed);
            }, {title: '确认修改家族配置'});
        });
        $('btn-reload-settings').addEventListener('click', function () {
            if (state.selectedId != null) {
                openDetail(state.selectedId);
            }
        });
    }

    function collectChanges() {
        var changed = [];
        var fields = document.querySelectorAll('#detail-pane [data-setting-key]');
        fields.forEach(function (field) {
            var key = field.getAttribute('data-setting-key');
            var value = (field.value || '').trim();
            if (value !== String(state.originalValues[key] == null ? '' : state.originalValues[key])) {
                changed.push({key: key, value: value});
            }
        });
        return changed;
    }

    function describeChanges(changed) {
        var labels = {};
        state.settings.forEach(function (item) {
            labels[item.key] = item.label || item.key;
        });
        var parts = changed.map(function (c) {
            var from = state.originalValues[c.key];
            var to = c.value;
            if (isBooleanSetting(c.key)) {
                from = from === 'true' ? BOOL_TRUE_LABEL : BOOL_FALSE_LABEL;
                to = to === 'true' ? BOOL_TRUE_LABEL : BOOL_FALSE_LABEL;
            }
            return labels[c.key] + '：' + from + ' → ' + to;
        });
        var familyName = (state.detail && state.detail.family && state.detail.family.name) || state.selectedId;
        return '将对家族「' + familyName + '」执行以下修改：' + parts.join('；');
    }

    function isBooleanSetting(key) {
        for (var i = 0; i < state.settings.length; i++) {
            if (state.settings[i].key === key) {
                return state.settings[i].valueType === 'BOOLEAN';
            }
        }
        return false;
    }

    function refreshSaveState() {
        var btn = $('btn-save-settings');
        if (!btn) {
            return;
        }
        btn.disabled = collectChanges().length === 0;
    }

    async function saveSettings(changed) {
        var payload = {settings: {}};
        changed.forEach(function (c) {
            payload.settings[c.key] = c.value;
        });
        var res = await FT.api('/api/admin/family/' + state.selectedId + '/settings', {
            method: 'PUT',
            body: JSON.stringify(payload)
        });
        if (res.code !== 200) {
            return;
        }
        FT.toast('配置已保存', 'success');
        await openDetail(state.selectedId);
    }

    function renderOperations(ops) {
        if (ops.length === 0) {
            return '<div class="detail-empty">暂无操作记录</div>';
        }
        var html = '<div class="op-list">';
        ops.forEach(function (op) {
            html += '<div class="op-item">' +
                '<span class="op-time">' + FT.escapeHtml(formatTime(op.createTime)) + '</span>' +
                '<span>' + FT.escapeHtml((op.username || '-') + ' · ' + (op.operationDesc || '')) + '</span>' +
                '</div>';
        });
        return html + '</div>';
    }

    document.addEventListener('DOMContentLoaded', boot);
})();
