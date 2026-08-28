package com.mouhin.family.tree.domain.event;

import java.time.LocalDateTime;

/**
 * 成员移除事件。
 * <p>
 * 当成员被管理员从家族中移除时发布。
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
public record MemberRemovedEvent(Long familyId, Long userId,
                                 LocalDateTime occurredOn) implements DomainEvent {

    public static MemberRemovedEvent of(Long familyId, Long userId) {
        return new MemberRemovedEvent(familyId, userId, LocalDateTime.now());
    }
}
