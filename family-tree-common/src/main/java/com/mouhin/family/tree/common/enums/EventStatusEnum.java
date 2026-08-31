package com.mouhin.family.tree.common.enums;

import com.mouhin.family.tree.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 家族活动状态枚举
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@AllArgsConstructor
public enum EventStatusEnum {

    OPEN("OPEN", "报名中"),
    CLOSED("CLOSED", "已截止");

    private final String code;
    private final String description;

    /**
     * 严格解析：非法编码抛出业务异常（用于入参校验）。
     *
     * @param code 状态编码
     * @return 对应枚举
     */
    public static EventStatusEnum fromCode(String code) {
        if (code != null) {
            for (EventStatusEnum value : values()) {
                if (value.code.equals(code)) {
                    return value;
                }
            }
        }
        throw new BusinessException("无效的活动状态");
    }
}
