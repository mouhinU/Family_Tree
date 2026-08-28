package com.mouhin.family.tree.domain.event;

import java.time.LocalDateTime;

/**
 * 家族祭奠事件。
 * <p>
 * 当用户为已故族人献上祭品（鲜花/祭拜）时发布。
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
public record FamilyOfferingMadeEvent(Long familyId, Long nodeId, Long userId,
                                      LocalDateTime occurredOn) implements DomainEvent {

    public static FamilyOfferingMadeEvent of(Long familyId, Long nodeId, Long userId) {
        return new FamilyOfferingMadeEvent(familyId, nodeId, userId, LocalDateTime.now());
    }
}
