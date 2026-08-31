package com.mouhin.family.tree.domain.event;

import java.time.LocalDateTime;

/**
 * 节点颜色标签批量更新事件。
 * <p>
 * 当批量修改节点颜色标签时发布。
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
public record NodeColorUpdatedEvent(Long familyId, int nodeCount, String colorLabel, Long userId,
                                    String username, String ipAddress,
                                    LocalDateTime occurredOn) implements DomainEvent {

    /**
     * 创建节点颜色标签批量更新事件实例
     *
     * @param familyId   家族ID
     * @param nodeCount  受影响节点数量
     * @param colorLabel 目标颜色标签编码
     * @param userId     操作用户ID
     * @param username   操作用户名
     * @param ipAddress  客户端IP
     * @return 事件实例
     */
    public static NodeColorUpdatedEvent of(Long familyId, int nodeCount, String colorLabel,
                                           Long userId, String username, String ipAddress) {
        return new NodeColorUpdatedEvent(familyId, nodeCount, colorLabel, userId, username,
                ipAddress, LocalDateTime.now());
    }
}
