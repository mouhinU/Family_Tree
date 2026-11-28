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
    var smsLoginForm = document.getElementById('sms-login-form');
    var loginModeToggle = document.getElementById('login-mode-toggle');
    var switchLoginMode = document.getElementById('switch-login-mode');
    var registerForm = document.getElementById('register-form');
    var passwordChangeForm = document.getElementById('password-change-form');
    var messageEl = document.getElementById('login-message');
    var loginTabs = document.querySelector('.login-tabs');

    var PHONE_STRICT_PATTERN = /^1[3-9]\d{9}$/;
    var SMS_CODE_PATTERN = /^\d{6}$/;
    var RESEND_INTERVAL_SECONDS = 60;

    // 注册短信验证开关：登录页打开时从公开接口拉取，开启时手机号与验证码转为必填；
    // 拉取失败退回可选口径，最终仍由注册链路后端校验兜底
    var registerSmsVerify = false;
    // 短信登录开关：关闭时隐藏"短信验证码登录"切换入口；拉取失败维持现状（入口保留，点击仍由后端拒绝）
    var smsLoginEnabled = true;
    // 开放注册开关：关闭时隐藏注册入口；拉取失败维持现状（入口保留，提交仍由后端拒绝）
    var registerEnabled = true;
    fetch('/api/auth/config')
        .then(function (res) {
            return res.json();
        })
        .then(function (data) {
            if (data.code === 200 && data.data) {
                if (data.data.registerEnabled === false) {
                    registerEnabled = false;
                    tabRegister.style.display = 'none';
                    if (registerForm.style.display !== 'none') {
                        tabLogin.click();
                    }
                }
                if (data.data.registerSmsVerify) {
                    registerSmsVerify = true;
                    document.getElementById('reg-phone-hint').textContent = '（必填，11位大陆手机号）';
                    document.getElementById('reg-code-hint').textContent = '（必填，6位短信验证码）';
                }
                if (data.data.smsLoginEnabled === false) {
                    smsLoginEnabled = false;
                    loginModeToggle.style.display = 'none';
                }
            }
        })
        .catch(function () {
            // 开关不可得时维持现状提示，不做前端强制
        });

    /** 已归属家族的用户落到家族过渡页，可在其中切换家族或再创建、加入一个 */
    function redirectToApp(hasFamily) {
        window.location.href = hasFamily ? '/portal.html' : '/family-setup.html';
    }

    tabLogin.addEventListener('click', function () {
        tabLogin.classList.add('active');
        tabRegister.classList.remove('active');
        registerForm.style.display = 'none';
        setLoginMode('password');
        messageEl.textContent = '';
    });

    tabRegister.addEventListener('click', function () {
        tabRegister.classList.add('active');
        tabLogin.classList.remove('active');
        registerForm.style.display = 'block';
        loginForm.style.display = 'none';
        smsLoginForm.style.display = 'none';
        loginModeToggle.style.display = 'none';
        messageEl.textContent = '';
    });

    // 检测 URL 中的注册入口与邀请码参数，自动切换到注册页并填入邀请码
    var registerParam = new URLSearchParams(window.location.search).get('mode') === 'register';
    var inviteParam = new URLSearchParams(window.location.search).get('invite');
    if (registerParam || inviteParam) {
        tabRegister.click();
    }
    if (inviteParam) {
        document.getElementById('reg-invite-code').value = inviteParam.toUpperCase();
    }

    // ========== 短信验证码通用逻辑 ==========
    // 登录区内密码/短信两种方式互斥切换，切换链接文案随模式更新
    function setLoginMode(mode) {
        var smsMode = mode === 'sms';
        loginForm.style.display = smsMode ? 'none' : 'block';
        smsLoginForm.style.display = smsMode ? 'block' : 'none';
        loginModeToggle.style.display = smsLoginEnabled ? 'block' : 'none';
        switchLoginMode.textContent = smsMode ? '密码登录' : '短信验证码登录';
    }

    switchLoginMode.addEventListener('click', function () {
        var smsVisible = smsLoginForm.style.display !== 'none';
        setLoginMode(smsVisible ? 'password' : 'sms');
        if (!smsVisible) {
            carryPhoneToSmsLogin();
        }
        messageEl.textContent = '';
    });

    // 密码登录框若已填写 11 位大陆手机号，切到短信登录时自动带入手机号
    function carryPhoneToSmsLogin() {
        var username = document.getElementById('login-username').value.trim();
        if (PHONE_STRICT_PATTERN.test(username)) {
            document.getElementById('sms-phone').value = username;
        }
    }

    function startResendCountdown(btn, originText) {
        var seconds = RESEND_INTERVAL_SECONDS;
        btn.disabled = true;
        btn.textContent = seconds + 's 后重发';
        var timer = setInterval(function () {
            seconds--;
            if (seconds <= 0) {
                clearInterval(timer);
                btn.disabled = false;
                btn.textContent = originText;
            } else {
                btn.textContent = seconds + 's 后重发';
            }
        }, 1000);
    }

    var SEND_QUOTA_PATTERN = /过于频繁|次数已达上限/;

    async function sendSmsCode(btn, phone, scene) {
        if (!PHONE_STRICT_PATTERN.test(phone)) {
            messageEl.textContent = '请先输入正确的11位大陆手机号';
            messageEl.className = 'message error';
            return;
        }
        // 点击即禁用：等响应回来再禁用挡不住同一秒内的连点，而服务端按请求占用重发间隔并按条计费
        btn.disabled = true;
        try {
            var res = await fetch('/api/auth/sms/send-code', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({phone: phone, scene: scene})
            });
            var data = await res.json();
            if (data.code === 200) {
                messageEl.textContent = '验证码已发送，5分钟内有效';
                messageEl.className = 'message success';
                startResendCountdown(btn, '发送验证码');
            } else {
                var failMsg = data.message || '验证码发送失败';
                messageEl.textContent = failMsg;
                messageEl.className = 'message error';
                if (SEND_QUOTA_PATTERN.test(failMsg)) {
                    // 限流类拒绝说明本次请求已消耗额度，冷却期内重试没有意义
                    startResendCountdown(btn, '发送验证码');
                } else {
                    btn.disabled = false;
                }
            }
        } catch (err) {
            messageEl.textContent = '网络错误，请重试';
            messageEl.className = 'message error';
            btn.disabled = false;
        }
    }

    document.getElementById('btn-sms-reg-send').addEventListener('click', function () {
        sendSmsCode(this, document.getElementById('reg-phone').value.trim(), 'REGISTER');
    });

    document.getElementById('btn-sms-login-send').addEventListener('click', function () {
        sendSmsCode(this, document.getElementById('sms-phone').value.trim(), 'LOGIN');
    });

    smsLoginForm.addEventListener('submit', async function (e) {
        e.preventDefault();
        var phone = document.getElementById('sms-phone').value.trim();
        var code = document.getElementById('sms-code').value.trim();
        try {
            var res = await fetch('/api/auth/login-sms', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({phone: phone, code: code})
            });
            var data = await res.json();
            if (data.code === 200) {
                if (data.data.csrfToken) {
                    sessionStorage.setItem('csrfToken', data.data.csrfToken);
                }
                if (data.data.mustChangePassword) {
                    enterForceChangeMode('');
                    messageEl.textContent = '检测到初始密码，请设置新密码后继续（需输入原初始密码）';
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
        var phoneCode = document.getElementById('reg-phone-code').value.trim();
        if (registerSmsVerify) {
            if (!PHONE_STRICT_PATTERN.test(phone)) {
                messageEl.textContent = '当前开启短信验证注册，请填写11位大陆手机号';
                messageEl.className = 'message error';
                return;
            }
            if (!SMS_CODE_PATTERN.test(phoneCode)) {
                messageEl.textContent = '请填写6位短信验证码';
                messageEl.className = 'message error';
                return;
            }
        } else if (phone && !PHONE_STRICT_PATTERN.test(phone)) {
            messageEl.textContent = '手机号格式不正确，应为11位大陆手机号';
            messageEl.className = 'message error';
            return;
        }
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
            if (phoneCode) {
                body.phoneCode = phoneCode;
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
        smsLoginForm.style.display = 'none';
        loginModeToggle.style.display = 'none';
        registerForm.style.display = 'none';
        passwordChangeForm.style.display = 'block';
        // 当前口令沿用刚登录成功的密码，仅用于提交校验，不在页面回显
        passwordChangeForm.dataset.currentPassword = currentPassword || '';
    }

    passwordChangeForm.addEventListener('submit', async function (e) {
        e.preventDefault();
        var newPwd = document.getElementById('change-new-password').value;
        var confirmPwd = document.getElementById('change-confirm-password').value;
        var phone = document.getElementById('change-phone').value.trim();
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
        if (phone && !PHONE_STRICT_PATTERN.test(phone)) {
            messageEl.textContent = '手机号须为11位大陆号码';
            messageEl.className = 'message error';
            return;
        }
        try {
            // 手机号先于改密提交：改密后本表单无法重复提交（原口令已失效），把可纠正的失败放在前面
            if (phone) {
                var bindRes = await fetch('/api/auth/profile', {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-CSRF-TOKEN': sessionStorage.getItem('csrfToken') || ''
                    },
                    body: JSON.stringify({phone: phone})
                });
                var bindData = await bindRes.json();
                if (bindData.code !== 200) {
                    messageEl.textContent = bindData.message || '手机号绑定失败，可留空重试或稍后在「我的资料」补填';
                    messageEl.className = 'message error';
                    return;
                }
            }
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
            redirectToApp(hasFamily);
        } catch (err) {
            window.location.href = '/index.html';
        }
    }
})();
