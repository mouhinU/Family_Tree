package com.mouhin.family.tree.web.config;

import com.mouhin.family.tree.web.websocket.FamilyWebSocketHandler;
import com.mouhin.family.tree.web.websocket.FamilyWebSocketHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final FamilyWebSocketHandler familyWebSocketHandler;
    private final FamilyWebSocketHandshakeInterceptor handshakeInterceptor;

    public WebSocketConfig(FamilyWebSocketHandler familyWebSocketHandler,
                           FamilyWebSocketHandshakeInterceptor handshakeInterceptor) {
        this.familyWebSocketHandler = familyWebSocketHandler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 握手拦截器负责强制登录会话、同源校验并注入服务端 familyId；
        // Spring 侧放行交由 OriginHandshakeInterceptor 不再限制（默认 *），实际安全由拦截器把关。
        registry.addHandler(familyWebSocketHandler, "/ws/family")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
