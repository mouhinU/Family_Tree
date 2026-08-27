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
    var messageEl = document.getElementById('login-message');

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
                if (data.data.hasFamily) {
                    window.location.href = '/index.html';
                } else {
                    window.location.href = '/family-setup.html';
                }
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
        try {
            var body = {username: username, password: password};
            if (inviteCode) {
                body.inviteCode = inviteCode;
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
})();
