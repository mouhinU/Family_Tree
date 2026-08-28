package com.mouhin.family.tree.domain.event;

import java.time.LocalDateTime;

/**
 * 成员加入事件。
 * <p>
 * 当用户通过邀请码加入家族时发布。
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
public record MemberJoinedEvent(Long familyId, Long userId, String username,
                                LocalDateTime occurredOn) implements DomainEvent {

    public static MemberJoinedEvent of(Long familyId, Long userId, String username) {
        return new MemberJoinedEvent(familyId, userId, username, LocalDateTime.now());
    }
}
