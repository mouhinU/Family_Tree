/**
 * 族谱前端 - 家族设置模块
 * 新用户创建家族或加入已有家族。
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
(function () {
    'use strict';

    var messageEl = document.getElementById('setup-message');

    function showMessage(text, type) {
        messageEl.textContent = text;
        messageEl.className = type;
    }

    document.getElementById('btn-create').addEventListener('click', async function () {
        var name = document.getElementById('create-name').value.trim();
        if (!name) {
            showMessage('请输入家族名称', 'error');
            return;
        }
        try {
            var headers = {'Content-Type': 'application/json'};
            var csrfToken = sessionStorage.getItem('csrfToken');
            if (csrfToken) {
                headers['X-CSRF-TOKEN'] = csrfToken;
            }
            var res = await fetch('/api/family', {
                method: 'POST',
                headers: headers,
                body: JSON.stringify({name: name})
            });
            var data = await res.json();
            if (data.code === 200) {
                showMessage('家族创建成功，正在跳转...', 'success');
                setTimeout(function () {
                    window.location.href = '/index.html';
                }, 800);
            } else {
                showMessage(data.message || '创建失败', 'error');
            }
        } catch (err) {
            showMessage('网络错误，请重试', 'error');
        }
    });

    document.getElementById('btn-join').addEventListener('click', async function () {
        var code = document.getElementById('join-code').value.trim();
        if (!code) {
            showMessage('请输入邀请码', 'error');
            return;
        }
        try {
            var headers = {'Content-Type': 'application/json'};
            var csrfToken = sessionStorage.getItem('csrfToken');
            if (csrfToken) {
                headers['X-CSRF-TOKEN'] = csrfToken;
            }
            var res = await fetch('/api/family/join', {
                method: 'POST',
                headers: headers,
                body: JSON.stringify({inviteCode: code})
            });
            var data = await res.json();
            if (data.code === 200) {
                showMessage('加入成功，正在跳转...', 'success');
                setTimeout(function () {
                    window.location.href = '/index.html';
                }, 800);
            } else {
                showMessage(data.message || '加入失败', 'error');
            }
        } catch (err) {
            showMessage('网络错误，请重试', 'error');
        }
    });
})();
