package com.mouhin.family.tree.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 跨族节点链接类型枚举
 *
 * <p>描述链接两端的身份语义，与 {@code family_relation} 的族内关系类型正交。</p>
 *
 * @author Family-Tree
 * @date 2026-11-20
 */
@Getter
@AllArgsConstructor
public enum NodeLinkTypeEnum {

    /**
     * 血缘同人：同一人在出身族与现居族的两个节点互为同人（外嫁女/入赘男回指出身族）
     */
    BLOOD(1, "血缘同人"),

    /**
     * 姻亲指认：本族节点指向他族的姻亲对应人节点
     */
    MARRIAGE(2, "姻亲指认");

    /**
     * 类型编码
     */
    private final int code;

    /**
     * 类型描述
     */
    private final String description;

    public static NodeLinkTypeEnum fromCode(int code) {
        for (NodeLinkTypeEnum value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("无效的跨族链接类型编码: " + code);
    }
}
