/**
 * 族谱前端 - 家族与用户管理模块
 * 家族管理弹窗、个人信息维护、标记为我、家族切换弹窗。
 *
 * @author Family-Tree
 * @date 2026-08-09
 */
(function () {
    'use strict';

    var FT = window.FT;

    // ========== 家族管理弹窗 ==========
    async function showFamilyModal() {
        var user = FT.state.currentUser;
        if (!user || !user.hasFamily) {
            window.location.href = '/family-setup.html';
            return;
        }

        var isOwner = user.familyRole === 'OWNER';
        var isAdmin = isOwner || user.familyRole === 'ADMIN';
        var memberPageSize = 20;
        var memberCurrentPage = 1;

        // 加载家族详情（含堂号、籍贯）
        var familyInfo = {};
        try {
            var familyRes = await FT.api('/api/family');
            if (familyRes.code === 200 && familyRes.data) {
                familyInfo = familyRes.data;
            }
        } catch (e) { /* ignore */ }

        // 渲染"已注册成员列表"：分页拉取 + 翻页 + 行内角色/移除操作
        async function renderMemberList(page) {
            var region = document.getElementById('family-members-region');
            if (!region) { return; }
            memberCurrentPage = page;
            region.innerHTML = '<p>加载中...</p>';
            var res;
            try {
                res = await FT.api('/api/family/members?page=' + page + '&size=' + memberPageSize);
            } catch (e) {
                region.innerHTML = '<p>加载失败</p>';
                return;
            }
            if (res.code !== 200 || !res.data) {
                region.innerHTML = '<p>' + FT.escapeHtml(res.message || '加载失败') + '</p>';
                return;
            }
            var pr = res.data;
            var recs = pr.records || [];
            if (recs.length === 0) {
                region.innerHTML = '<p>暂无成员</p>';
                return;
            }
            var totalPages = pr.totalPages || 1;
            var html = '<ul class="family-member-list">';
            recs.forEach(function (m) {
                var roleLabel = '';
                if (m.role === 'OWNER') { roleLabel = '（族长）'; }
                else if (m.role === 'ADMIN') { roleLabel = '（管理员）'; }

                var actionBtns = '';
                if (isOwner && m.role !== 'OWNER') {
                    if (m.role === 'ADMIN') {
                        actionBtns += ' <button class="btn-role-toggle" data-uid="' + m.userId + '" data-role="MEMBER">取消管理员</button>';
                    } else {
                        actionBtns += ' <button class="btn-role-toggle" data-uid="' + m.userId + '" data-role="ADMIN">设为管理员</button>';
                    }
                    actionBtns += ' <button class="btn-remove-member" data-uid="' + m.userId + '">移除</button>';
                } else if (isAdmin && !isOwner && m.role === 'MEMBER') {
                    actionBtns += ' <button class="btn-remove-member" data-uid="' + m.userId + '">移除</button>';
                }
                html += '<li>' + FT.escapeHtml(m.nickname || ('用户' + m.userId)) + roleLabel + actionBtns + '</li>';
            });
            html += '</ul>';

            html += '<div class="member-pager" style="display:flex;align-items:center;gap:10px;margin-top:8px;">';
            html += '<button class="btn-page-prev btn-sm"' + (page <= 1 ? ' disabled' : '') + '>上一页</button>';
            html += '<span>第 ' + pr.page + ' / ' + totalPages + ' 页，共 ' + pr.total + ' 人</span>';
            html += '<button class="btn-page-next btn-sm"' + (page >= totalPages ? ' disabled' : '') + '>下一页</button>';
            html += '</div>';
            region.innerHTML = html;

            // 行内操作：切换角色 / 移除（成功后刷新当前页）
            region.querySelectorAll('.btn-role-toggle').forEach(function (btn) {
                btn.addEventListener('click', function () {
                    var uid = this.getAttribute('data-uid');
                    var role = this.getAttribute('data-role');
                    var actionText = role === 'ADMIN' ? '设为管理员' : '取消管理员';
                    FT.confirm('确定' + actionText + '？', async function () {
                        var r = await FT.api('/api/family/member/role', {
                            method: 'PUT',
                            body: JSON.stringify({ userId: parseInt(uid, 10), role: role })
                        });
                        if (r.code === 200) {
                            FT.toast('操作成功', 'success');
                            renderMemberList(memberCurrentPage);
                        } else {
                            FT.toast(r.message || '操作失败');
                        }
                    });
                });
            });
            region.querySelectorAll('.btn-remove-member').forEach(function (btn) {
                btn.addEventListener('click', function () {
                    var uid = this.getAttribute('data-uid');
                    FT.confirm('确定移除该成员？', async function () {
                        var r = await FT.api('/api/family/member/' + uid, { method: 'DELETE' });
                        if (r.code === 200) {
                            FT.toast('已移除', 'success');
                            renderMemberList(memberCurrentPage);
                        } else {
                            FT.toast(r.message || '操作失败');
                        }
                    });
                });
            });
            // 翻页按钮
            var prevBtn = region.querySelector('.btn-page-prev');
            var nextBtn = region.querySelector('.btn-page-next');
            if (prevBtn) {
                prevBtn.addEventListener('click', function () {
                    if (memberCurrentPage > 1) { renderMemberList(memberCurrentPage - 1); }
                });
            }
            if (nextBtn) {
                nextBtn.addEventListener('click', function () {
                    if (memberCurrentPage < totalPages) { renderMemberList(memberCurrentPage + 1); }
                });
            }
        }

        var inviteSection = '';
        if (isAdmin) {
            inviteSection = '<div class="family-invite-section">' +
                '<div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;">' +
                '<label style="margin:0;">邀请码：<strong id="family-invite-code">' + (user.inviteCode || '') + '</strong></label>' +
                '<button id="btn-refresh-invite" class="btn-sm">刷新邀请码</button>' +
                '<button id="btn-gen-invite-link" class="btn-sm">生成邀请链接</button>' +
                '</div>' +
                '<div id="invite-link-box" class="invite-link-box" style="display:none;">' +
                '<input type="text" id="invite-link-input" class="invite-link-input" readonly>' +
                '<button id="btn-copy-invite-link" class="btn-sm">复制</button>' +
                '</div>' +
                '</div>';
        }

        // 堂号/籍贯编辑区（管理员可编辑）
        var hallSection = '';
        if (isAdmin) {
            hallSection = '<div class="family-hall-section" style="margin:12px 0;padding:10px;background:#faf8f2;border:1px solid #e8e0cc;border-radius:6px;">' +
                '<div style="display:flex;gap:16px;flex-wrap:wrap;">' +
                '<div class="form-group" style="flex:1;min-width:160px;margin-bottom:0;"><label style="font-size:13px;">堂号</label>' +
                '<input type="text" id="family-hall-name" value="' + FT.escapeAttr(familyInfo.hallName || '') + '" placeholder="如：三廉堂" style="width:100%;"></div>' +
                '<div class="form-group" style="flex:1;min-width:160px;margin-bottom:0;"><label style="font-size:13px;">籍贯</label>' +
                '<input type="text" id="family-ancestral-home" value="' + FT.escapeAttr(familyInfo.ancestralHome || '') + '" placeholder="如：广西蒙山" style="width:100%;"></div>' +
                '</div>' +
                '<button class="btn-sm" id="btn-save-family-info" style="margin-top:8px;">保存堂号/籍贯</button>' +
                '</div>';
        } else {
            // 非管理员只读展示
            var hallInfo = familyInfo.hallName || familyInfo.ancestralHome;
            if (hallInfo) {
                hallSection = '<div style="margin:8px 0;font-size:13px;color:#666;">' +
                    (familyInfo.hallName ? '堂号：' + FT.escapeHtml(familyInfo.hallName) + ' ' : '') +
                    (familyInfo.ancestralHome ? '籍贯：' + FT.escapeHtml(familyInfo.ancestralHome) : '') +
                    '</div>';
            }
        }

        var bodyHtml = '<h3>家族管理</h3>' +
            '<div class="family-modal-content">' +
            '<div class="family-info-row"><span class="family-name-label">家族名称：</span><strong>' + (user.familyName || '家族') + '</strong></div>' +
            hallSection +
            inviteSection +
            '<h4>已注册成员列表</h4>' +
            '<div id="family-members-region"><p>加载中...</p></div>' +
            '</div>' +
            '<div class="modal-actions">' +
            '<button class="btn-cancel" data-close-modal>关闭</button></div>';

        FT.showModal(bodyHtml, true);

        // 首次加载成员列表（分页；行内角色/移除/翻页事件均在 renderMemberList 内绑定）
        renderMemberList(1);

        // 绑定刷新邀请码事件
        var refreshBtn = document.getElementById('btn-refresh-invite');
        if (refreshBtn) {
            refreshBtn.addEventListener('click', async function() {
                var res = await FT.api('/api/family/invite-code', {method: 'PUT'});
                if (res.code === 200 && res.data) {
                    document.getElementById('family-invite-code').textContent = res.data.inviteCode;
                    FT.state.currentUser.inviteCode = res.data.inviteCode;
                    // 隐藏已生成的链接（邀请码已变更）
                    var linkBox = document.getElementById('invite-link-box');
                    if (linkBox) { linkBox.style.display = 'none'; }
                } else {
                    FT.toast(res.message || '刷新失败');
                }
            });
        }

        // 绑定生成邀请链接事件
        var genLinkBtn = document.getElementById('btn-gen-invite-link');
        if (genLinkBtn) {
            genLinkBtn.addEventListener('click', function() {
                var code = (FT.state.currentUser.inviteCode || '').toUpperCase();
                if (!code) { FT.toast('邀请码为空，请先刷新'); return; }
                var link = location.origin + '/login.html?invite=' + code;
                var linkInput = document.getElementById('invite-link-input');
                linkInput.value = link;
                document.getElementById('invite-link-box').style.display = 'flex';
                linkInput.select();
            });
        }

        // 绑定复制邀请链接事件
        var copyBtn = document.getElementById('btn-copy-invite-link');
        if (copyBtn) {
            copyBtn.addEventListener('click', function() {
                var linkInput = document.getElementById('invite-link-input');
                linkInput.select();
                if (navigator.clipboard && navigator.clipboard.writeText) {
                    navigator.clipboard.writeText(linkInput.value).then(function() {
                        FT.toast('链接已复制', 'success');
                    });
                } else {
                    document.execCommand('copy');
                    FT.toast('链接已复制', 'success');
                }
            });
        }

        // 绑定保存堂号/籍贯事件
        var saveFamilyInfoBtn = document.getElementById('btn-save-family-info');
        if (saveFamilyInfoBtn) {
            saveFamilyInfoBtn.addEventListener('click', async function() {
                var hallName = document.getElementById('family-hall-name').value.trim() || null;
                var ancestralHome = document.getElementById('family-ancestral-home').value.trim() || null;
                var res = await FT.api('/api/family/info', {
                    method: 'PUT',
                    body: JSON.stringify({hallName: hallName, ancestralHome: ancestralHome})
                });
                if (res.code === 200) {
                    FT.toast('保存成功', 'success');
                } else {
                    FT.toast(res.message || '保存失败');
                }
            });
        }
    }

    // ========== 个人信息维护 ==========
    // 编辑当前登录用户的基本信息：昵称、出生日期、辈分、邮箱、手机号
    async function showProfileModal() {
        var user = FT.state.currentUser;
        if (!user) { return; }

        // 辈分下拉：从 generationNames 构建，补充当前值
        var generationNames = FT.state.generationNames;
        var genKeys = Object.keys(generationNames).map(Number).sort(function (a, b) { return a - b; });
        var currentGen = user.generation;
        if (currentGen != null && genKeys.indexOf(currentGen) === -1) {
            genKeys.push(currentGen);
            genKeys.sort(function (a, b) { return a - b; });
        }
        var genOpts = genKeys.map(function (g) {
            var sel = g === currentGen ? ' selected' : '';
            var label = generationNames[g] ? '第' + g + '世（' + FT.escapeHtml(generationNames[g]) + '）' : '第' + g + '世';
            return '<option value="' + g + '"' + sel + '>' + label + '</option>';
        }).join('');
        var genUnset = currentGen == null ? ' selected' : '';

        FT.showModal(
            '<h3>个人信息</h3>' +
            '<div class="form-group"><label>昵称</label><input type="text" id="profile-nickname" value="' + FT.escapeAttr(user.nickname || '') + '" placeholder="请输入昵称"></div>' +
            '<div class="form-group"><label>出生日期</label><input type="date" id="profile-birth" value="' + FT.escapeAttr(user.birthDate || '') + '"></div>' +
            '<div class="form-group"><label>辈分（第几世）</label><select id="profile-generation"><option value=""' + genUnset + '>未设置</option>' + genOpts + '</select></div>' +
            '<div class="form-group"><label>邮箱（留空不修改，可用于登录）</label><input type="email" maxlength="100" id="profile-email" value="' + FT.escapeAttr(user.email || '') + '" placeholder="请输入邮箱"></div>' +
            '<div class="form-group"><label>手机号（留空不修改，可用于登录）</label><input type="tel" maxlength="20" id="profile-phone" value="' + FT.escapeAttr(user.phone || '') + '" placeholder="11位大陆手机号"></div>' +
            '<div class="modal-actions">' +
            '<button class="btn-cancel" id="profile-changepwd">修改密码</button>' +
            '<button class="btn-cancel" data-close-modal>取消</button>' +
            '<button class="btn-confirm" id="profile-save">保存</button></div>'
        );

        document.getElementById('profile-changepwd').addEventListener('click', function () {
            FT.closeModal();
            FT.showChangePasswordModal(false);
        });

        document.getElementById('profile-save').addEventListener('click', async function () {
            var nickname = document.getElementById('profile-nickname').value.trim();
            var birthDate = document.getElementById('profile-birth').value || null;
            var genRaw = document.getElementById('profile-generation').value;
            var email = document.getElementById('profile-email').value.trim();
            var phone = document.getElementById('profile-phone').value.trim();

            if (!nickname) { FT.toast('昵称不能为空', 'warning'); return; }

            var body = {
                nickname: nickname,
                birthDate: birthDate,
                generation: genRaw ? parseInt(genRaw) : null
            };
            // 联系方式留空表示不修改，不提交该字段
            if (email) { body.email = email; }
            if (phone) { body.phone = phone; }

            var res = await FT.api('/api/auth/profile', {method: 'PUT', body: JSON.stringify(body)});
            if (res.code === 200) {
                // 更新客户端缓存
                FT.state.currentUser.nickname = nickname;
                FT.state.currentUser.birthDate = birthDate;
                FT.state.currentUser.generation = body.generation;
                if (email) { FT.state.currentUser.email = email; }
                if (phone) { FT.state.currentUser.phone = phone; }
                document.getElementById('user-nickname').textContent = nickname;
                FT.closeModal();
            } else {
                FT.toast(res.message || '保存失败');
            }
        });
    }

    // ========== 标记为我 ==========
    // 将右键选中的节点标记为当前登录用户所在位置
    async function markAsSelf(nodeId) {
        var user = FT.state.currentUser;
        if (!user) { return; }

        // 若该节点已标记为"我"，则取消标记
        var newNodeId = (user.nodeId === nodeId) ? null : nodeId;

        var res = await FT.api('/api/auth/my-node', {
            method: 'PUT',
            body: JSON.stringify({nodeId: newNodeId})
        });
        if (res.code === 200) {
            user.nodeId = newNodeId;
            FT.renderTree();
        } else {
            FT.toast(res.message || '标记失败');
        }
    }

    // ========== 家族切换 ==========
    async function showFamilySwitcherModal() {
        var res = await FT.api('/api/family/my-list');
        if (res.code !== 200) { FT.toast(res.message || '加载失败'); return; }
        var families = res.data || [];
        var currentFamilyId = FT.state.currentUser.familyId;

        if (families.length <= 1) {
            FT.toast('您只属于一个家族', 'warning');
            return;
        }

        var html = '<h3 style="margin-bottom:10px;">切换家族</h3>';
        html += '<div style="max-height:300px;overflow-y:auto;">';
        families.forEach(function(f) {
            var isCurrent = f.id === currentFamilyId;
            var roleLabel = f.currentRole === 'OWNER' ? '（族长）' : (f.currentRole === 'ADMIN' ? '（管理员）' : '');
            html += '<div class="family-switch-item' + (isCurrent ? ' current' : '') + '" data-id="' + f.id + '" ' +
                'style="padding:10px;border:1px solid ' + (isCurrent ? '#a63a2b' : '#e0e0e0') + ';border-radius:6px;margin-bottom:8px;cursor:pointer;' +
                (isCurrent ? 'background:#fdf6f0;' : '') + '">' +
                '<strong>' + FT.escapeHtml(f.name) + '</strong>' + roleLabel +
                (isCurrent ? ' <span style="color:#a63a2b;font-size:12px;">← 当前</span>' : '') +
                '</div>';
        });
        html += '</div>';
        html += '<div class="modal-actions"><button class="btn-cancel" data-close-modal>关闭</button></div>';
        FT.showModal(html);

        document.querySelectorAll('.family-switch-item').forEach(function(el) {
            el.addEventListener('click', async function() {
                var familyId = parseInt(el.dataset.id);
                if (familyId === currentFamilyId) return;
                var switchRes = await FT.api('/api/family/switch/' + familyId, {method: 'PUT'});
                if (switchRes.code === 200) {
                    FT.toast('已切换家族', 'success');
                    FT.closeModal();
                    location.reload();
                } else {
                    FT.toast(switchRes.message || '切换失败');
                }
            });
        });
    }

    // ========== 修改密码 ==========
    // 独立遮罩层，force=true 时不可关闭（一次性初始口令首登强制改密）。
    // 提交沿用 FT.api 统一封装（自动带 CSRF、业务错误集中提示）。
    function showChangePasswordModal(force) {
        if (document.getElementById('pwd-change-container')) { return; }
        var notice = force
            ? '<p class="confirm-message">检测到初始密码，为保障账号安全，请设置新密码后继续使用。</p>'
            : '';
        var overlay = document.createElement('div');
        overlay.id = 'pwd-change-container';
        overlay.className = 'modal-overlay confirm-overlay';
        overlay.innerHTML = '<div class="modal modal-confirm">' +
            '<h3>修改密码</h3>' + notice +
            '<div class="form-group"><label>当前密码</label>' +
            '<input type="password" id="pwd-old" autocomplete="current-password"></div>' +
            '<div class="form-group"><label>新密码（至少6位）</label>' +
            '<input type="password" id="pwd-new" minlength="6" autocomplete="new-password"></div>' +
            '<div class="form-group"><label>确认新密码</label>' +
            '<input type="password" id="pwd-confirm" minlength="6" autocomplete="new-password"></div>' +
            '<div class="modal-actions">' +
            (force ? '' : '<button class="btn-cancel" id="pwd-cancel">取消</button>') +
            '<button class="btn-confirm" id="pwd-ok">确定</button>' +
            '</div></div>';
        document.body.appendChild(overlay);

        function close() { overlay.remove(); }

        if (!force) {
            overlay.addEventListener('click', function (e) {
                if (e.target === this) { close(); }
            });
            var cancelBtn = document.getElementById('pwd-cancel');
            if (cancelBtn) { cancelBtn.addEventListener('click', close); }
        }

        document.getElementById('pwd-ok').addEventListener('click', async function () {
            var oldPwd = document.getElementById('pwd-old').value;
            var newPwd = document.getElementById('pwd-new').value;
            var confirmPwd = document.getElementById('pwd-confirm').value;
            if (!oldPwd) { FT.toast('请输入当前密码', 'warning'); return; }
            if (newPwd.length < 6) { FT.toast('新密码长度不能少于6位', 'warning'); return; }
            if (newPwd !== confirmPwd) { FT.toast('两次输入的新密码不一致', 'warning'); return; }
            try {
                var res = await FT.api('/api/auth/password', {
                    method: 'POST',
                    body: JSON.stringify({oldPassword: oldPwd, newPassword: newPwd})
                });
                // res.code !== 200 时 FT.api 已集中弹出错误，此处仅在成功时收尾
                if (res && res.code === 200) {
                    if (FT.state.currentUser) { FT.state.currentUser.mustChangePassword = false; }
                    if (force) {
                        location.reload();
                    } else {
                        FT.toast('密码已修改', 'success');
                        close();
                    }
                }
            } catch (e) {
                // 错误提示已由 FT.api / FT.toast 统一处理
            }
        });
    }

    FT.showFamilyModal = showFamilyModal;
    FT.showProfileModal = showProfileModal;
    FT.markAsSelf = markAsSelf;
    FT.showFamilySwitcherModal = showFamilySwitcherModal;
    FT.showChangePasswordModal = showChangePasswordModal;
})();
