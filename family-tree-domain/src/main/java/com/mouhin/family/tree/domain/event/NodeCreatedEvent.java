package com.mouhin.family.tree.domain.event;

import java.time.LocalDateTime;

/**
 * 节点创建事件。
 * <p>
 * 当族谱中新增人物节点时发布。
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
public record NodeCreatedEvent(Long familyId, Long nodeId, String nodeName, Long userId,
                               String username, String ipAddress,
                               LocalDateTime occurredOn) implements DomainEvent {

    /**
     * 创建节点创建事件实例
     *
     * @param familyId  家族ID
     * @param nodeId    节点ID
     * @param nodeName  节点名称
     * @param userId    操作用户ID
     * @param username  操作用户名
     * @param ipAddress 客户端IP
     * @return 事件实例
     */
    public static NodeCreatedEvent of(Long familyId, Long nodeId, String nodeName, Long userId,
                                      String username, String ipAddress) {
        return new NodeCreatedEvent(familyId, nodeId, nodeName, userId, username, ipAddress,
                LocalDateTime.now());
    }
}
