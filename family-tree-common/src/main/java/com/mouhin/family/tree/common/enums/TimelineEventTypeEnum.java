package com.mouhin.family.tree.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 时间线事件类型枚举
 *
 * @author Family-Tree
 * @date 2026-09-12
 */
@Getter
@AllArgsConstructor
public enum TimelineEventTypeEnum {

    BIRTH("BIRTH", "出生"),
    DEATH("DEATH", "去世"),
    MARRIAGE("MARRIAGE", "结婚"),
    ACTIVITY("ACTIVITY", "家族活动");

    private final String code;
    private final String description;

    /**
     * 严格解析：非法编码抛出业务异常
     *
     * @param code 事件类型编码
     * @return 对应枚举
     */
    public static TimelineEventTypeEnum fromCode(String code) {
        if (code != null) {
            for (TimelineEventTypeEnum value : values()) {
                if (value.code.equals(code)) {
                    return value;
                }
            }
        }
        throw new com.mouhin.family.tree.common.exception.BusinessException("无效的时间线事件类型");
    }
}
