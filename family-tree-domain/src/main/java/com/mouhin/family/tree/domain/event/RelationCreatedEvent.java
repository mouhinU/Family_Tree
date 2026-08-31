package com.mouhin.family.tree.domain.event;

import java.time.LocalDateTime;

/**
 * 关系创建事件。
 * <p>
 * 当族谱中新增人物关系（亲子/夫妻/过继）时发布。
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
public record RelationCreatedEvent(Long familyId, Long relationId, Integer relationType,
                                   String fromNodeName, String toNodeName, Long userId,
                                   String username, String ipAddress,
                                   LocalDateTime occurredOn) implements DomainEvent {

    /**
     * 创建关系创建事件实例
     *
     * @param familyId     家族ID
     * @param relationId   关系ID
     * @param relationType 关系类型编码
     * @param fromNodeName 起始节点名称
     * @param toNodeName   目标节点名称
     * @param userId       操作用户ID
     * @param username     操作用户名
     * @param ipAddress    客户端IP
     * @return 事件实例
     */
    public static RelationCreatedEvent of(Long familyId, Long relationId, Integer relationType,
                                          String fromNodeName, String toNodeName, Long userId,
                                          String username, String ipAddress) {
        return new RelationCreatedEvent(familyId, relationId, relationType, fromNodeName,
                toNodeName, userId, username, ipAddress, LocalDateTime.now());
    }
}
