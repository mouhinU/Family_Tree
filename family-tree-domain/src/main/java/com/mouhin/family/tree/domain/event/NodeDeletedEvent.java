package com.mouhin.family.tree.domain.event;

import java.time.LocalDateTime;

/**
 * 节点删除事件。
 * <p>
 * 当族谱中人物节点被删除时发布。
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
public record NodeDeletedEvent(Long familyId, Long nodeId, String nodeName, Long userId,
                               String username, String ipAddress,
                               LocalDateTime occurredOn) implements DomainEvent {

    /**
     * 创建节点删除事件实例
     *
     * @param familyId  家族ID
     * @param nodeId    节点ID
     * @param nodeName  节点名称
     * @param userId    操作用户ID
     * @param username  操作用户名
     * @param ipAddress 客户端IP
     * @return 事件实例
     */
    public static NodeDeletedEvent of(Long familyId, Long nodeId, String nodeName, Long userId,
                                      String username, String ipAddress) {
        return new NodeDeletedEvent(familyId, nodeId, nodeName, userId, username, ipAddress,
                LocalDateTime.now());
    }
}
