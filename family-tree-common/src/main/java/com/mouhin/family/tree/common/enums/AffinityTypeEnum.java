package com.mouhin.family.tree.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 亲族家族类型枚举（跨族解析的四种视角）
 *
 * @author Family-Tree
 * @date 2026-11-20
 */
@Getter
@AllArgsConstructor
public enum AffinityTypeEnum {

    /**
     * 父族：血缘本族，无过继血缘链时即节点所在本族
     */
    FATHER(1, "父族"),

    /**
     * 母族：母亲节点沿链接指出的出身家族
     */
    MOTHER(2, "母族"),

    /**
     * 妻族：女性配偶节点沿链接指出的出身家族，离异再娶可有多条
     */
    WIFE(3, "妻族"),

    /**
     * 夫族：男性配偶节点沿链接指出的出身家族，对称支持入赘情形
     */
    HUSBAND(4, "夫族");

    /**
     * 类型编码
     */
    private final int code;

    /**
     * 类型描述
     */
    private final String description;

    public static AffinityTypeEnum fromCode(int code) {
        for (AffinityTypeEnum value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("无效的亲族家族类型编码: " + code);
    }
}
