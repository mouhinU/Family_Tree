/**
 * 族谱前端 - 公开首页交互
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
(function () {
    'use strict';

    var CONFIG_URL = '/api/auth/config';
    var STATUS_URL = '/api/auth/status';
    var INVITE_CODE_PATTERN = /^[A-Z0-9]+$/;
    var DESKTOP_MEDIA_QUERY = '(min-width: 821px)';
    var ANONYMOUS_STATE = 'anonymous';
    var AUTHENTICATED_STATE = 'authenticated';

    var menuToggle = document.getElementById('menu-toggle');
    var siteNav = document.getElementById('site-nav');
    var registerStatus = document.getElementById('register-status');
    var registrationEntries = document.querySelectorAll('[data-registration-entry]');
    var authStateEntries = document.querySelectorAll('[data-auth-state]');
    var inviteForm = document.getElementById('home-invite-form');
    var inviteInput = document.getElementById('home-invite-code');
    var inviteMessage = document.getElementById('invite-message');
    var signedIn = false;
    var registrationEnabled = false;

    function closeMenu(returnFocus) {
        siteNav.classList.remove('is-open');
        menuToggle.setAttribute('aria-expanded', 'false');
        if (returnFocus) {
            menuToggle.focus();
        }
    }

    menuToggle.addEventListener('click', function () {
        var expanded = menuToggle.getAttribute('aria-expanded') === 'true';
        siteNav.classList.toggle('is-open', !expanded);
        menuToggle.setAttribute('aria-expanded', String(!expanded));
    });

    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape' && menuToggle.getAttribute('aria-expanded') === 'true') {
            closeMenu(true);
        }
    });

    siteNav.addEventListener('click', function (event) {
        if (event.target.closest('a')) {
            closeMenu(false);
        }
    });

    var desktopMedia = window.matchMedia(DESKTOP_MEDIA_QUERY);
    desktopMedia.addEventListener('change', function (event) {
        if (event.matches) {
            closeMenu(false);
        }
    });

    function renderAuthState() {
        var activeState = signedIn ? AUTHENTICATED_STATE : ANONYMOUS_STATE;
        authStateEntries.forEach(function (entry) {
            entry.hidden = entry.getAttribute('data-auth-state') !== activeState;
        });
    }

    function renderRegistrationEntries() {
        registrationEntries.forEach(function (entry) {
            entry.hidden = signedIn || !registrationEnabled;
        });
    }

    function handleStatusResponse(res) {
        if (!res.ok) {
            return false;
        }
        return res.json().then(function (data) {
            return Boolean(data && data.code === 200 && data.data && data.data.loggedIn === true);
        });
    }

    function handleConfigResponse(res) {
        if (!res.ok) {
            throw new Error('config.request.failed');
        }
        return res.json().then(function (data) {
            if (!data || data.code !== 200 || !data.data) {
                throw new Error('config.invalid');
            }
            return data.data.registerEnabled !== false;
        });
    }

    Promise.all([
        fetch(STATUS_URL, {cache: 'no-store', headers: {Accept: 'application/json'}})
            .then(handleStatusResponse)
            .catch(function () {
                return false;
            }),
        fetch(CONFIG_URL, {cache: 'no-store', headers: {Accept: 'application/json'}})
            .then(handleConfigResponse)
            .catch(function () {
                registerStatus.textContent = '注册状态暂时无法确认，请先登录或联系家族管理员';
                registerStatus.hidden = false;
                return false;
            })
    ]).then(function (results) {
        signedIn = results[0];
        registrationEnabled = results[1];
        if (signedIn) {
            registerStatus.hidden = true;
        } else if (!registrationEnabled && registerStatus.textContent === '') {
            registerStatus.textContent = '当前暂未开放注册，请联系家族管理员';
            registerStatus.hidden = false;
        }
        renderAuthState();
        renderRegistrationEntries();
    });

    function clearInviteError() {
        inviteMessage.textContent = '';
        inviteInput.removeAttribute('aria-invalid');
    }

    inviteInput.addEventListener('input', clearInviteError);

    inviteForm.addEventListener('submit', function (event) {
        event.preventDefault();
        var inviteCode = inviteInput.value.trim().toUpperCase();
        if (!INVITE_CODE_PATTERN.test(inviteCode)) {
            inviteMessage.textContent = '请输入有效的邀请码，仅包含字母和数字';
            inviteInput.setAttribute('aria-invalid', 'true');
            inviteInput.focus();
            return;
        }
        inviteInput.value = inviteCode;
        window.location.href = '/login.html?invite=' + encodeURIComponent(inviteCode);
    });

    document.querySelectorAll('a[href="#invite"]').forEach(function (link) {
        link.addEventListener('click', function (event) {
            event.preventDefault();
            inviteInput.focus();
        });
    });
})();
