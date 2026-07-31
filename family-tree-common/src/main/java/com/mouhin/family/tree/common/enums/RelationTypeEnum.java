package com.mouhin.family.tree.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 关系类型枚举
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@Getter
@AllArgsConstructor
public enum RelationTypeEnum {

    PARENT_CHILD(1, "亲子"),
    SPOUSE(2, "夫妻");

    private final int code;
    private final String description;

    public static RelationTypeEnum fromCode(int code) {
        for (RelationTypeEnum value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("无效的关系类型编码: " + code);
    }
}
