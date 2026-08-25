package com.mouhin.family.tree.domain.event;

import java.time.LocalDateTime;

/**
 * 族谱树更新事件。
 * <p>
 * 当族谱树结构发生变更（节点增删改、关系变更等）时发布，
 * 用于通知缓存失效、日志记录等后续处理。
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
public record FamilyTreeUpdatedEvent(Long familyId, LocalDateTime occurredOn)
        implements DomainEvent {

    /**
     * 便捷构造方法，自动填充当前时间
     *
     * @param familyId 家族ID
     * @return 事件实例
     */
    public static FamilyTreeUpdatedEvent of(Long familyId) {
        return new FamilyTreeUpdatedEvent(familyId, LocalDateTime.now());
    }
}
