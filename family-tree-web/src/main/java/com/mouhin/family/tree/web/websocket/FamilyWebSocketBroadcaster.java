package com.mouhin.family.tree.web.websocket;

import tools.jackson.databind.json.JsonMapper;
import com.mouhin.family.tree.domain.event.MemberJoinedEvent;
import com.mouhin.family.tree.domain.event.NodeCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 领域事件 WebSocket 广播器。
 * <p>
 * 监听领域事件并通过 WebSocket 实时推送给在线家族成员。
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
@Component
public class FamilyWebSocketBroadcaster {

    private static final Logger logger = LoggerFactory.getLogger(FamilyWebSocketBroadcaster.class);

    private final FamilyWebSocketHandler webSocketHandler;
    private final JsonMapper objectMapper = JsonMapper.builder().build();

    public FamilyWebSocketBroadcaster(FamilyWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @EventListener
    public void onNodeCreated(NodeCreatedEvent event) {
        broadcast(event.familyId(), "NODE_CREATED", Map.of(
                "nodeId", event.nodeId(),
                "nodeName", event.nodeName(),
                "userId", event.userId()));
    }

    @EventListener
    public void onMemberJoined(MemberJoinedEvent event) {
        broadcast(event.familyId(), "MEMBER_JOINED", Map.of(
                "userId", event.userId()));
    }

    private void broadcast(Long familyId, String type, Map<String, Object> data) {
        try {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("type", type);
            message.put("data", data);
            String json = objectMapper.writeValueAsString(message);
            webSocketHandler.broadcastToFamily(familyId, json);
        } catch (Exception e) {
            logger.warn("Failed to broadcast {} for family {}: {}", type, familyId, e.getMessage());
        }
    }
}
