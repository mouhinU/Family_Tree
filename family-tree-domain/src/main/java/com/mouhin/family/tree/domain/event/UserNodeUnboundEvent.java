package com.mouhin.family.tree.domain.event;

import java.time.LocalDateTime;

/**
 * 用户节点解绑事件。
 * <p>
 * 当用户解除某家族的「标记为我」绑定后发布，供跨族亲族链接同步监听。
 *
 * @author Family-Tree
 * @date 2026-11-21
 */
public record UserNodeUnboundEvent(Long familyId, Long userId, Long nodeId,
                                   LocalDateTime occurredOn) implements DomainEvent {

    /**
     * 创建用户节点解绑事件实例
     *
     * @param familyId 家族ID
     * @param userId   解绑用户ID
     * @param nodeId   原绑定节点ID
     * @return 事件实例
     */
    public static UserNodeUnboundEvent of(Long familyId, Long userId, Long nodeId) {
        return new UserNodeUnboundEvent(familyId, userId, nodeId, LocalDateTime.now());
    }
}
