package com.mouhin.family.tree.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 性别枚举
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@Getter
@AllArgsConstructor
public enum GenderEnum {

    MALE(1, "男"),
    FEMALE(2, "女"),
    UNKNOWN(0, "未知");

    private final int code;
    private final String description;

    public static GenderEnum fromCode(int code) {
        for (GenderEnum value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
