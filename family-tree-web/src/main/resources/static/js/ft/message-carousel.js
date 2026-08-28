/**
 * 留言轮播模块
 * 在页面底部显示横向滚动的留言条，古风风格
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
(function () {
    'use strict';

    var STORAGE_KEY = 'ft_carousel_closed';
    var carousel = document.getElementById('message-carousel');
    var track = document.getElementById('msg-carousel-track');
    var closeBtn = document.getElementById('msg-carousel-close');
    var refreshTimer = null;

    /**
     * 初始化轮播：检查关闭状态 + 加载数据 + 启动定时刷新
     */
    function init() {
        bindClose();
        if (isClosed()) {
            return;
        }
        loadMessages();
        refreshTimer = setInterval(loadMessages, 60000);
    }

    /**
     * 判断用户是否已关闭轮播
     */
    function isClosed() {
        try {
            return sessionStorage.getItem(STORAGE_KEY) === '1';
        } catch (e) {
            return false;
        }
    }

    /**
     * 绑定关闭按钮事件
     */
    function bindClose() {
        if (closeBtn) {
            closeBtn.addEventListener('click', function () {
                try {
                    sessionStorage.setItem(STORAGE_KEY, '1');
                } catch (e) {
                    // 静默
                }
                carousel.style.display = 'none';
                document.body.classList.remove('msg-carousel-visible');
                if (refreshTimer) {
                    clearInterval(refreshTimer);
                    refreshTimer = null;
                }
            });
        }
    }

    /**
     * 从 API 加载最新留言
     */
    function loadMessages() {
        if (isClosed()) {
            return;
        }
        FT.api('/api/message?page=1&size=20').then(function (res) {
            if (res.code === 200 && res.data && res.data.records && res.data.records.length > 0) {
                render(res.data.records);
                carousel.style.display = 'flex';
                document.body.classList.add('msg-carousel-visible');
            } else {
                carousel.style.display = 'none';
                document.body.classList.remove('msg-carousel-visible');
            }
        }).catch(function () {
            // 静默失败，不影响主功能
        });
    }

    /**
     * 渲染留言到轮播轨道
     * @param {Array} messages 留言列表
     */
    function render(messages) {
        if (!messages || messages.length === 0) {
            track.innerHTML = '';
            return;
        }

        var html = messages.map(function (msg) {
            var text = FT.escapeHtml(msg.content || '');
            return '<span class="msg-carousel-item">'
                + '<span class="msg-carousel-text">' + text + '</span>'
                + '</span>';
        }).join('');

        track.innerHTML = '<div class="msg-carousel-inner">' + html + html + '</div>';

        var inner = track.querySelector('.msg-carousel-inner');
        if (inner) {
            var halfWidth = inner.scrollWidth / 2;
            var duration = Math.max(halfWidth / 60, 10);
            inner.style.setProperty('--msg-carousel-duration', duration + 's');
        }
    }

    /**
     * 公开刷新接口（供留言板发帖后调用）
     */
    FT.refreshMessageCarousel = function () {
        if (isClosed()) {
            return;
        }
        loadMessages();
    };

    FT.initMessageCarousel = init;

})();
