package com.mouhin.family.tree.web.config;

import com.mouhin.family.tree.web.websocket.FamilyWebSocketHandler;
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

    public WebSocketConfig(FamilyWebSocketHandler familyWebSocketHandler) {
        this.familyWebSocketHandler = familyWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(familyWebSocketHandler, "/ws/family")
                .setAllowedOrigins("*");
    }
}
