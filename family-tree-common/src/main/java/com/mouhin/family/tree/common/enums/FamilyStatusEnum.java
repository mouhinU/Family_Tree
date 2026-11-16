package com.mouhin.family.tree.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 家族状态枚举：平台管理员对家族整体的治理标记
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Getter
@AllArgsConstructor
public enum FamilyStatusEnum {

    /**
     * 正常：读写均不受限
     */
    NORMAL("NORMAL", "正常", "家族读写不受限制"),

    /**
     * 冻结：成员可浏览，所有写操作被拒绝
     */
    FROZEN("FROZEN", "冻结", "家族进入只读，成员可查看但无法新增或修改数据"),

    /**
     * 归档：家族整体下线，仅平台管理员可访问
     */
    ARCHIVED("ARCHIVED", "归档", "家族对普通成员下线，仅平台管理员可查看与恢复");

    /**
     * 状态编码
     */
    private final String code;

    /**
     * 状态展示名
     */
    private final String label;

    /**
     * 状态含义说明
     */
    private final String description;

    /**
     * 是否禁止家族内的写操作
     *
     * @return 冻结与归档均禁止写操作
     */
    public boolean blocksWrites() {
        return this != NORMAL;
    }

    /**
     * 是否对普通成员完全下线
     *
     * @return 仅归档状态下线
     */
    public boolean hidesFromMembers() {
        return this == ARCHIVED;
    }

    /**
     * 根据 code 获取枚举，非法值抛异常
     *
     * @param code 状态编码
     * @return 枚举实例
     */
    public static FamilyStatusEnum fromCode(String code) {
        for (FamilyStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的家族状态：" + code);
    }
}
