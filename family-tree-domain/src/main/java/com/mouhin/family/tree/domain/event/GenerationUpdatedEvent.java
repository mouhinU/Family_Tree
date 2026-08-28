package com.mouhin.family.tree.domain.event;

import java.time.LocalDateTime;

/**
 * 辈分排次更新事件。
 * <p>
 * 当家族辈分名称列表被批量保存（增删改）时发布。
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
public record GenerationUpdatedEvent(Long familyId, LocalDateTime occurredOn) implements DomainEvent {

    public static GenerationUpdatedEvent of(Long familyId) {
        return new GenerationUpdatedEvent(familyId, LocalDateTime.now());
    }
}
