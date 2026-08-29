/**
 * 族谱前端 - 主入口模块
 * 工具栏事件绑定与应用初始化（须最后加载）。
 *
 * @author Family-Tree
 * @date 2026-08-02
 */
(function () {
    'use strict';

    const FT = window.FT;

    async function init() {
        // 恢复 CSRF Token（登录时存储在 sessionStorage）
        var savedToken = sessionStorage.getItem('csrfToken');
        if (savedToken) {
            FT.initCsrfToken(savedToken);
        }
        FT.setupScrollOverlay();
        try {
            const res = await FT.api('/api/auth/me');
            if (res.code !== 200) {
                window.location.href = '/login.html';
                return;
            }
            FT.state.currentUser = res.data;
            // 刷新 CSRF Token（/api/auth/me 每次返回新 token）
            if (res.data.csrfToken) {
                FT.initCsrfToken(res.data.csrfToken);
                sessionStorage.setItem('csrfToken', res.data.csrfToken);
            }
            document.getElementById('user-nickname').textContent = FT.state.currentUser.nickname || '';

            // 家族信息展示
            if (!res.data.hasFamily) {
                window.location.href = '/family-setup.html';
                return;
            }
            const familyNameEl = document.getElementById('family-name');
            if (familyNameEl) {
                familyNameEl.textContent = res.data.familyName || '';
            }

            // 卷轴封面动态显示家族名称（如：蒙氏族谱、嬴氏族谱）
            var scrollSealEl = document.getElementById('scroll-seal-text');
            if (scrollSealEl && res.data.familyName) {
                var familyName = res.data.familyName;
                var sealText = '族谱';
                // 如果家族名称格式为"X氏家族"，提取X
                if (familyName.length >= 2 && familyName.charAt(1) === '氏') {
                    sealText = familyName.charAt(0) + '氏族谱';
                } else if (familyName.length >= 3 && familyName.indexOf('氏家族') >= 0) {
                    // 如果包含"氏家族"，提取"氏"前面的字
                    var idx = familyName.indexOf('氏');
                    if (idx > 0) {
                        sealText = familyName.charAt(idx - 1) + '氏族谱';
                    } else {
                        sealText = familyName + '族谱';
                    }
                } else {
                    // 其他情况直接使用家族名称
                    sealText = familyName + '族谱';
                }
                scrollSealEl.textContent = sealText;
            }

            // 族长/管理员标识
            var role = res.data.familyRole;
            if (role === 'OWNER' || role === 'ADMIN') {
                var badge = document.getElementById('family-role-badge');
                if (badge) {
                    badge.style.display = 'inline-block';
                    badge.textContent = role === 'OWNER' ? '族长' : '管理员';
                }
                var logBtn = document.getElementById('btn-operation-log');
                if (logBtn) {
                    logBtn.style.display = 'inline-block';
                }
            }
        } catch (e) {
            window.location.href = '/login.html';
            return;
        }
        bindEvents();
        await FT.loadTree();
        // 初始化留言轮播
        if (FT.initMessageCarousel) {
            FT.initMessageCarousel();
        }
        // 初始化 WebSocket 实时推送
        if (FT.wsInit) {
            FT.wsInit();
        }
    }

    // ========== 事件绑定 ==========
    function bindEvents() {
        document.getElementById('btn-logout').addEventListener('click', async function () {
            await FT.api('/api/auth/logout', {method: 'POST'});
            window.location.href = '/login.html';
        });

        document.getElementById('btn-add-root').addEventListener('click', function () {
            FT.showNodeModal('添加始祖', {});
        });

        document.getElementById('btn-generation').addEventListener('click', function () {
            FT.showGenerationModal();
        });

        // 导出族谱：将整棵树（含宣纸背景与辈分水印）导出为单页 PDF
        document.getElementById('btn-export').addEventListener('click', function () {
            FT.exportToPdf();
        });

        document.getElementById('chk-hide-deceased').addEventListener('change', function () {
            FT.state.hideDeceased = this.checked;
            FT.renderTree();
        });

        document.getElementById('chk-hide-marryout').addEventListener('change', function () {
            FT.state.hideMarryOut = this.checked;
            FT.renderTree();
        });

        document.getElementById('sel-layout').addEventListener('change', function () {
            FT.state.layoutDirection = this.value;
            FT.renderTree();
        });

        // 颜色图例折叠开关：默认隐藏，点击展开 / 收起
        document.getElementById('legend-toggle').addEventListener('click', function () {
            document.getElementById('legend').classList.toggle('open');
        });

        // 工具栏整体下拉隐藏：点击把手收起 / 展开，画布随之上移填补；
        // 移动端默认展开，保证搜索开关与工具项在族谱页面直接可见，桌面端保持默认收起
        if (window.matchMedia('(max-width: 768px)').matches) {
            document.getElementById('toolbar').classList.remove('collapsed');
            document.body.classList.remove('toolbar-hidden');
            document.getElementById('toolbar-handle').title = '收起工具栏';
        }
        document.getElementById('toolbar-handle').addEventListener('click', function () {
            const collapsed = document.getElementById('toolbar').classList.toggle('collapsed');
            document.body.classList.toggle('toolbar-hidden', collapsed);
            this.title = collapsed ? '展开工具栏' : '收起工具栏';
        });

        // 家族管理按钮
        const btnFamily = document.getElementById('btn-family');
        if (btnFamily) {
            btnFamily.addEventListener('click', function () {
                FT.showFamilyModal();
            });
        }

        // 个人信息按钮（在更多下拉菜单中）
        const btnProfile = document.getElementById('btn-profile');
        if (btnProfile) {
            btnProfile.addEventListener('click', function () {
                var dropdown = document.getElementById('header-more-dropdown');
                if (dropdown) dropdown.style.display = 'none';
                FT.showProfileModal();
            });
        }

        // 操作日志按钮（在更多下拉菜单中）
        const btnLog = document.getElementById('btn-operation-log');
        if (btnLog) {
            btnLog.addEventListener('click', function () {
                var dropdown = document.getElementById('header-more-dropdown');
                if (dropdown) dropdown.style.display = 'none';
                FT.showOperationLogModal();
            });
        }

        // 时间线按钮
        var btnTimeline = document.getElementById('btn-timeline');
        if (btnTimeline) {
            btnTimeline.addEventListener('click', function () {
                FT.showTimelineModal();
            });
        }

        // 关系路径分析按钮
        var btnRelationPath = document.getElementById('btn-relation-path');
        if (btnRelationPath) {
            btnRelationPath.addEventListener('click', function () {
                FT.showRelationPathModal();
            });
        }

        // 留言板按钮
        var btnMessage = document.getElementById('btn-message');
        if (btnMessage) {
            btnMessage.addEventListener('click', function () {
                FT.showMessageModal();
            });
        }

        // 忌日提醒按钮
        var btnDeathAnniversary = document.getElementById('btn-death-anniversary');
        if (btnDeathAnniversary) {
            btnDeathAnniversary.addEventListener('click', function () {
                FT.showDeathAnniversaryModal();
            });
        }

        // AI 助手按钮
        var btnAi = document.getElementById('btn-ai');
        if (btnAi) {
            btnAi.addEventListener('click', function () {
                FT.showAiModal();
            });
        }

        // 通知铃铛按钮
        var btnNotification = document.getElementById('btn-notification');
        if (btnNotification) {
            btnNotification.addEventListener('click', function () {
                FT.showNotificationModal();
            });
        }

        // 切换家族按钮
        var btnFamilySwitch = document.getElementById('btn-family-switch');
        if (btnFamilySwitch) {
            btnFamilySwitch.addEventListener('click', function () {
                FT.showFamilySwitcherModal();
                // 点击后关闭下拉菜单
                document.getElementById('header-more-dropdown').style.display = 'none';
            });
        }

        // 邀请族人按钮
        var btnInvite = document.getElementById('btn-invite');
        if (btnInvite) {
            btnInvite.addEventListener('click', function () {
                FT.showInviteModal();
                document.getElementById('header-more-dropdown').style.display = 'none';
            });
        }

        // 移动端触摸提示问号
        var btnTouchHint = document.getElementById('btn-touch-hint');
        var touchHintPopup = document.getElementById('touch-hint-popup');
        var touchHintTimer = null;
        if (btnTouchHint && touchHintPopup) {
            btnTouchHint.addEventListener('click', function (e) {
                e.stopPropagation();
                var isShown = touchHintPopup.classList.toggle('show');
                if (touchHintTimer) {
                    clearTimeout(touchHintTimer);
                }
                if (isShown) {
                    touchHintTimer = setTimeout(function () {
                        touchHintPopup.classList.remove('show');
                    }, 3000);
                }
            });
            document.addEventListener('click', function () {
                touchHintPopup.classList.remove('show');
                if (touchHintTimer) {
                    clearTimeout(touchHintTimer);
                }
            });
        }

        // ========== 更多操作下拉菜单 ==========
        var btnMore = document.getElementById('btn-more');
        var moreDropdown = document.getElementById('header-more-dropdown');
        if (btnMore && moreDropdown) {
            btnMore.addEventListener('click', function (e) {
                e.stopPropagation();
                var isVisible = moreDropdown.style.display !== 'none';
                moreDropdown.style.display = isVisible ? 'none' : 'block';
            });

            // 点击空白处关闭下拉菜单
            document.addEventListener('click', function (e) {
                if (!btnMore.contains(e.target) && !moreDropdown.contains(e.target)) {
                    moreDropdown.style.display = 'none';
                }
            });

            // 下拉菜单项点击后关闭菜单
            moreDropdown.querySelectorAll('.header-more-item').forEach(function (item) {
                item.addEventListener('click', function () {
                    moreDropdown.style.display = 'none';
                });
            });
        }

        // ========== 搜索 ==========
        var searchInput = document.getElementById('search-input');
        var searchDropdown = document.getElementById('search-dropdown');
        var searchTimer = null;

        searchInput.addEventListener('input', function () {
            clearTimeout(searchTimer);
            var keyword = this.value.trim();
            if (!keyword) {
                searchDropdown.style.display = 'none';
                return;
            }
            searchTimer = setTimeout(function () {
                FT.api('/api/node/search?keyword=' + encodeURIComponent(keyword)).then(function (res) {
                    if (res.code === 200) {
                        renderSearchResults(res.data || [], keyword);
                    }
                });
            }, 300);
        });

        searchInput.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') {
                searchDropdown.style.display = 'none';
                searchInput.blur();
            }
        });

        // 点击空白处关闭搜索下拉
        document.addEventListener('click', function (e) {
            if (!searchInput.contains(e.target) && !searchDropdown.contains(e.target)) {
                searchDropdown.style.display = 'none';
            }
        });

        function renderSearchResults(nodes, keyword) {
            if (nodes.length === 0) {
                searchDropdown.innerHTML = '<div class="search-empty">未找到「' + escapeHtml(keyword) + '」</div>';
                searchDropdown.style.display = 'block';
                return;
            }
            var html = nodes.map(function (n) {
                var meta = [];
                if (n.generation) meta.push('第' + n.generation + '世');
                if (n.gender === 1) meta.push('男');
                else if (n.gender === 2) meta.push('女');
                // 传统名字字段（字、号、讳）
                var traditionalNames = [];
                if (n.hui) traditionalNames.push('讳' + n.hui);
                if (n.zi) traditionalNames.push('字' + n.zi);
                if (n.hao) traditionalNames.push('号' + n.hao);
                if (traditionalNames.length > 0) meta.push(traditionalNames.join(' '));
                if (n.birthDate) meta.push(n.birthDate);
                var nameDisplay = escapeHtml(n.name);
                var subtitle = '';
                if (n.hui || n.zi || n.hao) {
                    var parts = [];
                    if (n.hui) parts.push('讳' + escapeHtml(n.hui));
                    if (n.zi) parts.push('字' + escapeHtml(n.zi));
                    if (n.hao) parts.push('号' + escapeHtml(n.hao));
                    subtitle = '<div class="search-subtitle">' + parts.join(' · ') + '</div>';
                }
                return '<div class="search-item" data-node-id="' + n.id + '">'
                    + '<div class="search-name">' + nameDisplay + '</div>'
                    + subtitle
                    + (meta.length ? '<div class="search-meta">' + meta.join(' · ') + '</div>' : '')
                    + '</div>';
            }).join('');
            searchDropdown.innerHTML = html;
            searchDropdown.style.display = 'block';

            // 绑定点击事件：定位到节点
            searchDropdown.querySelectorAll('.search-item').forEach(function (item) {
                item.addEventListener('click', function () {
                    var nodeId = parseInt(this.getAttribute('data-node-id'));
                    searchDropdown.style.display = 'none';
                    searchInput.value = '';
                    FT.focusNode && FT.focusNode(nodeId);
                });
            });
        }

        // ========== 移动端搜索浮层 ==========
        var mobileSearchOverlay = document.getElementById('mobile-search-overlay');
        var mobileSearchInput = document.getElementById('mobile-search-input');
        var mobileSearchResults = document.getElementById('mobile-search-results');
        var mobileSearchTimer = null;

        if (mobileSearchInput) {
            mobileSearchInput.addEventListener('input', function () {
                clearTimeout(mobileSearchTimer);
                var keyword = this.value.trim();
                if (!keyword) {
                    mobileSearchResults.innerHTML = '';
                    return;
                }
                mobileSearchTimer = setTimeout(function () {
                    FT.api('/api/node/search?keyword=' + encodeURIComponent(keyword)).then(function (res) {
                        if (res.code === 200) {
                            renderMobileSearchResults(res.data || [], keyword);
                        }
                    });
                }, 300);
            });

            mobileSearchInput.addEventListener('keydown', function (e) {
                if (e.key === 'Escape') {
                    closeMobileSearch();
                }
            });
        }

        function renderMobileSearchResults(nodes, keyword) {
            if (nodes.length === 0) {
                mobileSearchResults.innerHTML = '<div class="search-empty">未找到「' + escapeHtml(keyword) + '」</div>';
                return;
            }
            var html = nodes.map(function (n) {
                var meta = [];
                if (n.generation) meta.push('第' + n.generation + '世');
                if (n.gender === 1) meta.push('男');
                else if (n.gender === 2) meta.push('女');
                if (n.birthDate) meta.push(n.birthDate);
                var nameDisplay = escapeHtml(n.name);
                var subtitle = '';
                if (n.hui || n.zi || n.hao) {
                    var parts = [];
                    if (n.hui) parts.push('讳' + escapeHtml(n.hui));
                    if (n.zi) parts.push('字' + escapeHtml(n.zi));
                    if (n.hao) parts.push('号' + escapeHtml(n.hao));
                    subtitle = '<div class="search-subtitle">' + parts.join(' · ') + '</div>';
                }
                return '<div class="search-item" data-node-id="' + n.id + '">'
                    + '<div class="search-name">' + nameDisplay + '</div>'
                    + subtitle
                    + (meta.length ? '<div class="search-meta">' + meta.join(' · ') + '</div>' : '')
                    + '</div>';
            }).join('');
            mobileSearchResults.innerHTML = html;

            mobileSearchResults.querySelectorAll('.search-item').forEach(function (item) {
                item.addEventListener('click', function () {
                    var nodeId = parseInt(this.getAttribute('data-node-id'));
                    closeMobileSearch();
                    FT.focusNode && FT.focusNode(nodeId);
                });
            });
        }

        function openMobileSearch() {
            if (mobileSearchOverlay) {
                mobileSearchOverlay.style.display = 'flex';
                mobileSearchOverlay.classList.add('show');
                setTimeout(function () { mobileSearchInput && mobileSearchInput.focus(); }, 100);
            }
        }

        function closeMobileSearch() {
            if (mobileSearchOverlay) {
                mobileSearchOverlay.classList.remove('show');
                setTimeout(function () { mobileSearchOverlay.style.display = 'none'; }, 50);
                if (mobileSearchInput) mobileSearchInput.value = '';
                if (mobileSearchResults) mobileSearchResults.innerHTML = '';
            }
        }

        var mobileSearchClose = document.getElementById('mobile-search-close');
        if (mobileSearchClose) {
            mobileSearchClose.addEventListener('click', closeMobileSearch);
        }

        // 移动端顶部导航搜索入口：打开搜索浮层
        var btnMobileSearch = document.getElementById('btn-mobile-search');
        if (btnMobileSearch) {
            btnMobileSearch.addEventListener('click', openMobileSearch);
        }

        // ========== 移动端侧滑抽屉 ==========
        var drawer = document.getElementById('mobile-drawer');
        var drawerOverlay = document.getElementById('mobile-drawer-overlay');
        var hamburgerBtn = document.getElementById('btn-hamburger');

        function openDrawer() {
            if (drawer && drawerOverlay) {
                drawer.classList.add('open');
                drawerOverlay.classList.add('show');
                if (hamburgerBtn) hamburgerBtn.classList.add('active');
                document.body.style.overflow = 'hidden';
            }
        }

        function closeDrawer() {
            if (drawer && drawerOverlay) {
                drawer.classList.remove('open');
                drawerOverlay.classList.remove('show');
                if (hamburgerBtn) hamburgerBtn.classList.remove('active');
                document.body.style.overflow = '';
            }
        }

        if (hamburgerBtn) {
            hamburgerBtn.addEventListener('click', function () {
                if (drawer && drawer.classList.contains('open')) {
                    closeDrawer();
                } else {
                    openDrawer();
                }
            });
        }

        if (drawerOverlay) {
            drawerOverlay.addEventListener('click', closeDrawer);
        }

        // 抽屉内通知按钮
        var drawerNotifBtn = document.getElementById('drawer-btn-notification');
        if (drawerNotifBtn) {
            drawerNotifBtn.addEventListener('click', function () {
                closeDrawer();
                FT.showNotificationModal();
            });
        }

        // 抽屉导航项绑定
        var drawerBindings = {
            'drawer-btn-generation': function () { closeDrawer(); FT.showGenerationModal(); },
            'drawer-btn-family': function () { closeDrawer(); FT.showFamilyModal(); },
            'drawer-btn-timeline': function () { closeDrawer(); FT.showTimelineModal(); },
            'drawer-btn-relation-path': function () { closeDrawer(); FT.showRelationPathModal(); },
            'drawer-btn-profile': function () { closeDrawer(); FT.showProfileModal(); },
            'drawer-btn-family-switch': function () { closeDrawer(); FT.showFamilySwitcherModal(); },
            'drawer-btn-death-anniversary': function () { closeDrawer(); FT.showDeathAnniversaryModal(); },
            'drawer-btn-operation-log': function () { closeDrawer(); FT.showOperationLogModal(); },
            'drawer-btn-export': function () { closeDrawer(); FT.exportToPdf(); },
            'drawer-btn-add-root': function () { closeDrawer(); FT.showNodeModal('添加始祖', {}); },
            'drawer-btn-logout': function () {
                closeDrawer();
                FT.api('/api/auth/logout', {method: 'POST'}).then(function () {
                    window.location.href = '/login.html';
                });
            }
        };

        Object.keys(drawerBindings).forEach(function (id) {
            var el = document.getElementById(id);
            if (el) {
                el.addEventListener('click', drawerBindings[id]);
            }
        });

        // ========== 移动端底部导航栏 ==========
        var bottomNavTree = document.getElementById('bottom-nav-tree');
        var bottomNavMessage = document.getElementById('bottom-nav-message');
        var bottomNavInvite = document.getElementById('bottom-nav-invite');
        var bottomNavMore = document.getElementById('bottom-nav-more');

        if (bottomNavTree) {
            bottomNavTree.addEventListener('click', function () {
                setActiveBottomNav(this);
            });
        }

        if (bottomNavMessage) {
            bottomNavMessage.addEventListener('click', function () {
                setActiveBottomNav(this);
                FT.showMessageModal();
            });
        }

        if (bottomNavInvite) {
            bottomNavInvite.addEventListener('click', function () {
                setActiveBottomNav(this);
                FT.showInviteModal();
            });
        }

        if (bottomNavMore) {
            bottomNavMore.addEventListener('click', function () {
                setActiveBottomNav(this);
                openDrawer();
            });
        }

        function setActiveBottomNav(activeEl) {
            document.querySelectorAll('.bottom-nav-item').forEach(function (item) {
                item.classList.remove('active');
            });
            if (activeEl) activeEl.classList.add('active');
        }

        function escapeHtml(text) {
            var div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        }
    }

    // 同步用户信息到抽屉菜单
    function syncDrawerUserInfo() {
        var user = FT.state.currentUser;
        if (!user) return;

        var drawerNickname = document.getElementById('drawer-user-nickname');
        if (drawerNickname) drawerNickname.textContent = user.nickname || '';

        var role = user.familyRole;
        var drawerBadge = document.getElementById('drawer-role-badge');
        if (drawerBadge && (role === 'OWNER' || role === 'ADMIN')) {
            drawerBadge.style.display = 'inline-block';
            drawerBadge.textContent = role === 'OWNER' ? '族长' : '管理员';
        }

        // 操作日志按钮（仅 OWNER/ADMIN 可见）
        var drawerLogBtn = document.getElementById('drawer-btn-operation-log');
        if (drawerLogBtn && (role === 'OWNER' || role === 'ADMIN')) {
            drawerLogBtn.style.display = 'flex';
        }

        // 家族名称
        var drawerFamilyName = document.getElementById('drawer-family-name');
        if (drawerFamilyName) {
            // 从 header 的 family-name 获取
            var headerFamilyName = document.getElementById('family-name');
            drawerFamilyName.textContent = (headerFamilyName && headerFamilyName.textContent) || '';
        }
    }

    // 启动
    init();
    // 初始化完成后同步抽屉用户信息
    setTimeout(syncDrawerUserInfo, 500);
})();
