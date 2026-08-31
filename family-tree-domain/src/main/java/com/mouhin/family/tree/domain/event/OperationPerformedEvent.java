package com.mouhin.family.tree.domain.event;

import java.time.LocalDateTime;

/**
 * 统一操作审计事件。
 * <p>
 * 凡是需要写入操作日志的操作，完成后发布本事件，
 * 由 {@code OperationLogEventListener} 消费并记录到操作日志。
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
public record OperationPerformedEvent(Long userId, String username, String operationType,
                                      String operationDesc, String targetType, Long targetId,
                                      Long familyId, String ipAddress,
                                      LocalDateTime occurredOn) implements DomainEvent {

    /**
     * 创建操作审计事件实例
     *
     * @param userId        操作用户ID（可为null）
     * @param username      操作用户名（可为null）
     * @param operationType 操作类型
     * @param operationDesc 操作描述
     * @param targetType    操作对象类型
     * @param targetId      操作对象ID（可为null）
     * @param familyId      家族ID（可为null）
     * @param ipAddress     客户端IP（可为null）
     * @return 事件实例
     */
    public static OperationPerformedEvent of(Long userId, String username, String operationType,
                                             String operationDesc, String targetType, Long targetId,
                                             Long familyId, String ipAddress) {
        return new OperationPerformedEvent(userId, username, operationType, operationDesc,
                targetType, targetId, familyId, ipAddress, LocalDateTime.now());
    }
}
