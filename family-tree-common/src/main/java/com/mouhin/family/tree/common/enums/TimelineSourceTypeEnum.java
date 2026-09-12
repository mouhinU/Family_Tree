package com.mouhin.family.tree.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 时间线来源类型枚举
 *
 * @author Family-Tree
 * @date 2026-09-12
 */
@Getter
@AllArgsConstructor
public enum TimelineSourceTypeEnum {

    NODE("NODE", "族谱节点"),
    RELATION("RELATION", "族谱关系"),
    EVENT("EVENT", "家族活动");

    private final String code;
    private final String description;

    /**
     * 严格解析：非法编码抛出业务异常
     *
     * @param code 来源类型编码
     * @return 对应枚举
     */
    public static TimelineSourceTypeEnum fromCode(String code) {
        if (code != null) {
            for (TimelineSourceTypeEnum value : values()) {
                if (value.code.equals(code)) {
                    return value;
                }
            }
        }
        throw new com.mouhin.family.tree.common.exception.BusinessException("无效的时间线来源类型");
    }
}
