package com.mouhin.family.tree.domain.entity;

import com.mouhin.family.tree.common.enums.FamilyMemberRoleEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 家族成员实体
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Getter
@Setter
public class FamilyMember {

    private Long id;
    private Long familyId;
    private Long userId;
    private String role;
    private LocalDateTime joinedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 判断是否为族长
     *
     * @return 是否为族长角色
     */
    public boolean isOwner() {
        return Objects.equals(role, FamilyMemberRoleEnum.OWNER.getCode());
    }

    /**
     * 判断是否为管理员
     *
     * @return 是否为管理员角色
     */
    public boolean isAdmin() {
        return Objects.equals(role, FamilyMemberRoleEnum.ADMIN.getCode());
    }

    /**
     * 判断是否为管理者（族长或管理员）
     *
     * @return 是否为管理者
     */
    public boolean isManager() {
        return isOwner() || isAdmin();
    }

    /**
     * 校验角色变更的合法性
     *
     * @param newRole 新角色编码
     */
    public void validateRoleChange(String newRole) {
        try {
            FamilyMemberRoleEnum.fromCode(newRole);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("无效的成员角色：" + newRole);
        }
        if (isOwner() && !Objects.equals(newRole, FamilyMemberRoleEnum.OWNER.getCode())) {
            // 族长角色变更需要特殊处理（移交），此处仅做基本校验
            return;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FamilyMember that = (FamilyMember) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "FamilyMember{"
                + "id=" + id
                + ", familyId=" + familyId
                + ", userId=" + userId
                + ", role='" + role + '\''
                + '}';
    }
}
