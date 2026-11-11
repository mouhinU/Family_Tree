/**
 * 族谱前端 - 登录/注册模块
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
(function () {
    'use strict';

    var tabLogin = document.getElementById('tab-login');
    var tabRegister = document.getElementById('tab-register');
    var loginForm = document.getElementById('login-form');
    var registerForm = document.getElementById('register-form');
    var passwordChangeForm = document.getElementById('password-change-form');
    var messageEl = document.getElementById('login-message');
    var loginTabs = document.querySelector('.login-tabs');

    function redirectToApp(hasFamily) {
        window.location.href = hasFamily ? '/index.html' : '/family-setup.html';
    }

    // 检测 URL 中的邀请码参数，自动切换到注册页并填入邀请码
    var inviteParam = new URLSearchParams(window.location.search).get('invite');
    if (inviteParam) {
        tabRegister.click();
        document.getElementById('reg-invite-code').value = inviteParam.toUpperCase();
    }

    tabLogin.addEventListener('click', function () {
        tabLogin.classList.add('active');
        tabRegister.classList.remove('active');
        loginForm.style.display = 'block';
        registerForm.style.display = 'none';
        messageEl.textContent = '';
    });

    tabRegister.addEventListener('click', function () {
        tabRegister.classList.add('active');
        tabLogin.classList.remove('active');
        registerForm.style.display = 'block';
        loginForm.style.display = 'none';
        messageEl.textContent = '';
    });

    loginForm.addEventListener('submit', async function (e) {
        e.preventDefault();
        var username = document.getElementById('login-username').value;
        var password = document.getElementById('login-password').value;
        try {
            var res = await fetch('/api/auth/login', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({username: username, password: password})
            });
            var data = await res.json();
            if (data.code === 200) {
                if (data.data.csrfToken) {
                    sessionStorage.setItem('csrfToken', data.data.csrfToken);
                }
                if (data.data.mustChangePassword) {
                    enterForceChangeMode(password);
                    messageEl.textContent = '检测到初始密码，请设置新密码后继续';
                    messageEl.className = 'message success';
                    return;
                }
                redirectToApp(data.data.hasFamily);
            } else {
                messageEl.textContent = data.message || '登录失败';
                messageEl.className = 'message error';
            }
        } catch (err) {
            messageEl.textContent = '网络错误，请重试';
            messageEl.className = 'message error';
        }
    });

    registerForm.addEventListener('submit', async function (e) {
        e.preventDefault();
        var username = document.getElementById('reg-username').value;
        var password = document.getElementById('reg-password').value;
        var inviteCode = document.getElementById('reg-invite-code').value.trim();
        var email = document.getElementById('reg-email').value.trim();
        var phone = document.getElementById('reg-phone').value.trim();
        try {
            var body = {username: username, password: password};
            if (inviteCode) {
                body.inviteCode = inviteCode;
            }
            if (email) {
                body.email = email;
            }
            if (phone) {
                body.phone = phone;
            }
            var res = await fetch('/api/auth/register', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify(body)
            });
            var data = await res.json();
            if (data.code === 200) {
                messageEl.textContent = '注册成功，请登录';
                messageEl.className = 'message success';
                setTimeout(function () {
                    tabLogin.click();
                }, 1000);
            } else {
                messageEl.textContent = data.message || '注册失败';
                messageEl.className = 'message error';
            }
        } catch (err) {
            messageEl.textContent = '网络错误，请重试';
            messageEl.className = 'message error';
        }
    });

    // ========== 首登强制改密 ==========
    // 自动开通账号携带一次性初始口令，登录后强制设置新密码再进入应用。
    function enterForceChangeMode(currentPassword) {
        if (loginTabs) { loginTabs.style.display = 'none'; }
        loginForm.style.display = 'none';
        registerForm.style.display = 'none';
        passwordChangeForm.style.display = 'block';
        // 当前口令沿用刚登录成功的密码，仅用于提交校验，不在页面回显
        passwordChangeForm.dataset.currentPassword = currentPassword || '';
    }

    passwordChangeForm.addEventListener('submit', async function (e) {
        e.preventDefault();
        var newPwd = document.getElementById('change-new-password').value;
        var confirmPwd = document.getElementById('change-confirm-password').value;
        if (newPwd.length < 6) {
            messageEl.textContent = '新密码长度不能少于6位';
            messageEl.className = 'message error';
            return;
        }
        if (newPwd !== confirmPwd) {
            messageEl.textContent = '两次输入的新密码不一致';
            messageEl.className = 'message error';
            return;
        }
        try {
            var res = await fetch('/api/auth/password', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-CSRF-TOKEN': sessionStorage.getItem('csrfToken') || ''
                },
                body: JSON.stringify({
                    oldPassword: passwordChangeForm.dataset.currentPassword,
                    newPassword: newPwd
                })
            });
            var data = await res.json();
            if (data.code === 200) {
                messageEl.textContent = '密码已修改，正在进入…';
                messageEl.className = 'message success';
                // 改密后重新加载会话家族信息再跳转
                redirectToAppAfterChange();
            } else {
                messageEl.textContent = data.message || '密码修改失败';
                messageEl.className = 'message error';
            }
        } catch (err) {
            messageEl.textContent = '网络错误，请重试';
            messageEl.className = 'message error';
        }
    });

    // 改密成功后按当前家族状态跳转，避免沿用可能已过期的登录响应
    async function redirectToAppAfterChange() {
        try {
            var res = await fetch('/api/auth/me', {headers: {'Accept': 'application/json'}});
            var data = await res.json();
            var hasFamily = data.code === 200 && data.data && data.data.hasFamily;
            window.location.href = hasFamily ? '/index.html' : '/family-setup.html';
        } catch (err) {
            window.location.href = '/index.html';
        }
    }
})();
