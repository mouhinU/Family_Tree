package com.mouhin.family.tree.domain.event;

import java.time.LocalDateTime;

/**
 * 用户节点绑定事件。
 * <p>
 * 当用户在某家族完成「标记为我」（绑定族谱节点）后发布，供跨族亲族链接同步监听。
 *
 * @author Family-Tree
 * @date 2026-11-21
 */
public record UserNodeBoundEvent(Long familyId, Long userId, Long nodeId,
                                 LocalDateTime occurredOn) implements DomainEvent {

    /**
     * 创建用户节点绑定事件实例
     *
     * @param familyId 家族ID
     * @param userId   绑定用户ID
     * @param nodeId   被绑定节点ID
     * @return 事件实例
     */
    public static UserNodeBoundEvent of(Long familyId, Long userId, Long nodeId) {
        return new UserNodeBoundEvent(familyId, userId, nodeId, LocalDateTime.now());
    }
}
