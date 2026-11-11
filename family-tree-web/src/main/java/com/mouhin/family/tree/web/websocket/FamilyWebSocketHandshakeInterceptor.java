package com.mouhin.family.tree.web.websocket;

import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.net.URI;
import java.util.Map;

/**
 * 族谱 WebSocket 握手拦截器。
 * <p>
 * 在握手阶段强制校验：仅允许已登录会话建立连接，并做同源校验；
 * 将服务端会话中的 userId/familyId 注入 WebSocket 会话属性，
 * 后续注册以服务端数据为准，杜绝客户端自报 familyId 窃听他人家族事件。
 *
 * @author Family-Tree
 * @date 2026-11-10
 */
@Component
public class FamilyWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(FamilyWebSocketHandshakeInterceptor.class);

    /** WebSocket 会话属性键：用户ID */
    public static final String ATTR_USER_ID = "userId";

    /** WebSocket 会话属性键：家族ID */
    public static final String ATTR_FAMILY_ID = "familyId";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }
        HttpServletRequest httpRequest = servletRequest.getServletRequest();

        if (!isSameOrigin(request)) {
            logger.warn("WebSocket handshake rejected: cross-origin request from {}", request.getURI());
            return false;
        }

        HttpSession session = httpRequest.getSession(false);
        if (session == null) {
            logger.warn("WebSocket handshake rejected: no HTTP session");
            return false;
        }
        Long userId = (Long) session.getAttribute(FamilyTreeConsts.SESSION_USER_ID);
        if (userId == null) {
            logger.warn("WebSocket handshake rejected: unauthenticated session {}", session.getId());
            return false;
        }

        Long familyId = (Long) session.getAttribute(FamilyTreeConsts.SESSION_FAMILY_ID);
        attributes.put(ATTR_USER_ID, userId);
        attributes.put(ATTR_FAMILY_ID, familyId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 握手后无需处理
    }

    /**
     * 同源校验：Origin 请求头的主机名必须与当前请求主机名一致。
     * <p>
     * 仅比较主机名、忽略端口与协议，兼容 nginx TLS 终结反向代理
     * （Origin 为 https://域名，后端看到的是内部 8090 端口）。
     * 浏览器发起的 WebSocket 握手必然携带 Origin；缺失 Origin 视为非浏览器来源，拒绝。
     *
     * @param request 握手请求
     * @return 是否同源
     */
    private boolean isSameOrigin(ServerHttpRequest request) {
        String origin = request.getHeaders().getOrigin();
        if (origin == null || origin.isBlank()) {
            return false;
        }
        String originHost = hostOf(origin);
        if (originHost == null) {
            return false;
        }
        // 反向代理优先信任 X-Forwarded-Host，其次 Host 头，最后请求 URI 主机
        String serverHost = firstHost(request.getHeaders().getFirst("X-Forwarded-Host"));
        if (serverHost == null) {
            serverHost = request.getURI().getHost();
        }
        return originHost.equalsIgnoreCase(serverHost);
    }

    /**
     * 从 URL 中提取主机名（不含端口）。
     */
    private String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 取逗号分隔的代理链第一个主机名。
     */
    private String firstHost(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        String first = headerValue.split(",")[0].trim();
        return first.isEmpty() ? null : first;
    }
}
