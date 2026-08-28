package com.mouhin.family.tree.domain.event;

import java.time.LocalDateTime;

/**
 * 家族留言发布事件。
 * <p>
 * 当家族留言板有新留言或回复时发布。
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
public record FamilyMessagePostedEvent(Long familyId, Long messageId, Long userId,
                                       Long parentId, LocalDateTime occurredOn) implements DomainEvent {

    public static FamilyMessagePostedEvent of(Long familyId, Long messageId, Long userId, Long parentId) {
        return new FamilyMessagePostedEvent(familyId, messageId, userId, parentId, LocalDateTime.now());
    }
}
