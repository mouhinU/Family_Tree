package com.mouhin.family.tree.domain.event;

import java.time.LocalDateTime;

/**
 * 家族创建事件。
 * <p>
 * 当新家族被创建时发布。
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
public record FamilyCreatedEvent(Long familyId, Long creatorUserId, String familyName,
                                 LocalDateTime occurredOn) implements DomainEvent {

    public static FamilyCreatedEvent of(Long familyId, Long creatorUserId, String familyName) {
        return new FamilyCreatedEvent(familyId, creatorUserId, familyName, LocalDateTime.now());
    }
}
