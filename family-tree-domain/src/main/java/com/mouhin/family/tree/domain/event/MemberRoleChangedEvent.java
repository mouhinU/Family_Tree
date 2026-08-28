package com.mouhin.family.tree.domain.event;

import java.time.LocalDateTime;

/**
 * 成员角色变更事件。
 * <p>
 * 当成员角色（族长/管理员/普通成员）发生变更时发布。
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
public record MemberRoleChangedEvent(Long familyId, Long userId, String newRole,
                                     LocalDateTime occurredOn) implements DomainEvent {

    public static MemberRoleChangedEvent of(Long familyId, Long userId, String newRole) {
        return new MemberRoleChangedEvent(familyId, userId, newRole, LocalDateTime.now());
    }
}
