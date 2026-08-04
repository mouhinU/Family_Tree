package com.mouhin.family.tree.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 家族成员角色枚举
 *
 * @author Family-Tree
 * @date 2026-08-03
 */
@Getter
@AllArgsConstructor
public enum FamilyMemberRoleEnum {

    OWNER("OWNER", "族长"),
    ADMIN("ADMIN", "管理员"),
    MEMBER("MEMBER", "成员");

    private final String code;
    private final String description;

    /**
     * 根据 code 获取枚举，非法值抛异常
     *
     * @param code 角色编码
     * @return 枚举实例
     */
    public static FamilyMemberRoleEnum fromCode(String code) {
        for (FamilyMemberRoleEnum role : values()) {
            if (role.code.equals(code)) {
                return role;
            }
        }
        throw new IllegalArgumentException("未知的家族成员角色：" + code);
    }
}
