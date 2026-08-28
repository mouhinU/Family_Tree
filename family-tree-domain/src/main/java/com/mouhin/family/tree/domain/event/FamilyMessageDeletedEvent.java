package com.mouhin.family.tree.domain.event;

import java.time.LocalDateTime;

/**
 * 家族留言删除事件。
 * <p>
 * 当家族留言被删除时发布。
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
public record FamilyMessageDeletedEvent(Long familyId, Long messageId, Long userId,
                                         LocalDateTime occurredOn) implements DomainEvent {

    public static FamilyMessageDeletedEvent of(Long familyId, Long messageId, Long userId) {
        return new FamilyMessageDeletedEvent(familyId, messageId, userId, LocalDateTime.now());
    }
}
