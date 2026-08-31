package com.mouhin.family.tree.domain.event;

import java.time.LocalDateTime;

/**
 * 关系更新事件。
 * <p>
 * 当族谱中人物关系信息（婚离状态、日期等）被修改时发布。
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
public record RelationUpdatedEvent(Long familyId, Long relationId, Integer relationType,
                                   String fromNodeName, String toNodeName, Long userId,
                                   String username, String ipAddress,
                                   LocalDateTime occurredOn) implements DomainEvent {

    /**
     * 创建关系更新事件实例
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
    public static RelationUpdatedEvent of(Long familyId, Long relationId, Integer relationType,
                                          String fromNodeName, String toNodeName, Long userId,
                                          String username, String ipAddress) {
        return new RelationUpdatedEvent(familyId, relationId, relationType, fromNodeName,
                toNodeName, userId, username, ipAddress, LocalDateTime.now());
    }
}
