package com.mouhin.family.tree.web.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 族谱 WebSocket 处理器。
 * <p>
 * 客户端连接后发送 {@code {"type":"register","familyId":123}} 注册家族，
 * 服务端通过 {@link #broadcastToFamily} 向同家族所有在线成员推送消息。
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
@Component
public class FamilyWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(FamilyWebSocketHandler.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** familyId → 在线会话集合 */
    private final ConcurrentHashMap<Long, Set<WebSocketSession>> familySessions = new ConcurrentHashMap<>();

    /** sessionId → familyId（用于断连时清理） */
    private final ConcurrentHashMap<String, Long> sessionFamilyMap = new ConcurrentHashMap<>();

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            JsonNode json = objectMapper.readTree(message.getPayload());
            String type = json.has("type") ? json.get("type").asText() : "";
            if ("register".equals(type) && json.has("familyId")) {
                Long familyId = json.get("familyId").asLong();
                registerSession(familyId, session);
                // 发送确认消息
                session.sendMessage(new TextMessage(
                        objectMapper.writeValueAsString(
                                Map.of("type", "registered", "familyId", familyId))));
                logger.info("WebSocket session {} registered for family {}", session.getId(), familyId);
            }
        } catch (Exception e) {
            logger.warn("Failed to handle WebSocket message: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long familyId = sessionFamilyMap.remove(session.getId());
        if (familyId != null) {
            Set<WebSocketSession> sessions = familySessions.get(familyId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    familySessions.remove(familyId);
                }
            }
            logger.info("WebSocket session {} disconnected from family {}", session.getId(), familyId);
        }
    }

    /**
     * 向指定家族的所有在线成员广播消息
     *
     * @param familyId 家族ID
     * @param message  JSON 消息内容
     */
    public void broadcastToFamily(Long familyId, String message) {
        Set<WebSocketSession> sessions = familySessions.get(familyId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        TextMessage textMessage = new TextMessage(message);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(textMessage);
                } catch (IOException e) {
                    logger.warn("Failed to send WebSocket message to session {}: {}",
                            session.getId(), e.getMessage());
                }
            }
        }
    }

    private void registerSession(Long familyId, WebSocketSession session) {
        familySessions.computeIfAbsent(familyId, k -> ConcurrentHashMap.newKeySet()).add(session);
        sessionFamilyMap.put(session.getId(), familyId);
    }
}
