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

        // 工具栏整体下拉隐藏：点击把手收起 / 展开，画布随之上移填补
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

        function escapeHtml(text) {
            var div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        }
    }

    // 启动
    init();
})();
