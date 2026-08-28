/**
 * 族谱前端 - WebSocket 实时推送模块
 * 连接服务端 WebSocket，接收家族成员变动、节点创建等实时通知。
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
(function () {
    'use strict';

    var FT = window.FT;

    /** WebSocket 实例 */
    var ws = null;

    /** 重连定时器 */
    var reconnectTimer = null;

    /** 重连间隔（毫秒） */
    var RECONNECT_INTERVAL = 5000;

    /** 最大重连次数 */
    var MAX_RECONNECT = 10;

    /** 当前重连次数 */
    var reconnectCount = 0;

    /**
     * 初始化 WebSocket 连接
     */
    function init() {
        var familyId = FT.state && FT.state.familyId;
        if (!familyId) {
            return;
        }
        connect(familyId);
    }

    /**
     * 建立 WebSocket 连接
     */
    function connect(familyId) {
        if (ws && ws.readyState === WebSocket.OPEN) {
            return;
        }

        var protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        var url = protocol + '//' + window.location.host + '/ws/family';

        try {
            ws = new WebSocket(url);
        } catch (e) {
            scheduleReconnect(familyId);
            return;
        }

        ws.onopen = function () {
            reconnectCount = 0;
            // 注册家族
            ws.send(JSON.stringify({ type: 'register', familyId: familyId }));
        };

        ws.onmessage = function (event) {
            try {
                var msg = JSON.parse(event.data);
                handleMessage(msg);
            } catch (e) {
                // 忽略非 JSON 消息
            }
        };

        ws.onclose = function () {
            scheduleReconnect(familyId);
        };

        ws.onerror = function () {
            if (ws) { ws.close(); }
        };
    }

    /**
     * 处理收到的 WebSocket 消息
     */
    function handleMessage(msg) {
        switch (msg.type) {
            case 'registered':
                // 注册成功，加载未读通知数
                loadUnreadCount();
                break;
            case 'NODE_CREATED':
                handleNodeCreated(msg.data);
                break;
            case 'MEMBER_JOINED':
                handleMemberJoined(msg.data);
                break;
        }
    }

    /**
     * 处理节点创建事件
     */
    function handleNodeCreated(data) {
        if (data && data.nodeName) {
            FT.toast('家族新增了成员: ' + data.nodeName, 'info', 3000);
        }
        // 刷新族谱树
        if (FT.loadTree) {
            FT.loadTree();
        }
        // 更新未读通知数
        loadUnreadCount();
    }

    /**
     * 处理成员加入事件
     */
    function handleMemberJoined(data) {
        FT.toast('有新成员加入了家族', 'info', 3000);
        // 更新未读通知数
        loadUnreadCount();
    }

    /**
     * 定时重连
     */
    function scheduleReconnect(familyId) {
        if (reconnectCount >= MAX_RECONNECT) {
            return;
        }
        if (reconnectTimer) {
            clearTimeout(reconnectTimer);
        }
        reconnectCount++;
        reconnectTimer = setTimeout(function () {
            connect(familyId);
        }, RECONNECT_INTERVAL);
    }

    /**
     * 加载未读通知数
     */
    async function loadUnreadCount() {
        try {
            var res = await FT.api('/api/notification/unread-count');
            if (res.code === 200) {
                updateBadge(res.data || 0);
            }
        } catch (e) {
            // 忽略
        }
    }

    /**
     * 更新通知徽标
     */
    function updateBadge(count) {
        var badge = document.getElementById('notification-badge');
        if (!badge) {
            return;
        }
        if (count > 0) {
            badge.textContent = count > 99 ? '99+' : count;
            badge.style.display = 'inline-flex';
        } else {
            badge.style.display = 'none';
        }
    }

    /**
     * 关闭 WebSocket 连接
     */
    function close() {
        if (reconnectTimer) {
            clearTimeout(reconnectTimer);
            reconnectTimer = null;
        }
        if (ws) {
            ws.close();
            ws = null;
        }
    }

    // 暴露公共方法
    FT.wsInit = init;
    FT.wsClose = close;
    FT.wsLoadUnreadCount = loadUnreadCount;
})();
