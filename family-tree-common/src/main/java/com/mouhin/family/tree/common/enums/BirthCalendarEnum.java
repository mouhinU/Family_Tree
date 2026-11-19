package com.mouhin.family.tree.common.enums;

import com.mouhin.family.tree.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 出生日期录入历法枚举
 *
 * <p>记录出生日期当初按阳历还是阴历录入，详情弹窗据此决定农历行与阳历行的先后。
 * 落库存码值，NULL 表示未记录（历史数据），渲染时回落启发式规则。</p>
 *
 * @author Family-Tree
 * @date 2026-11-19
 */
@Getter
@AllArgsConstructor
public enum BirthCalendarEnum {

    /**
     * 阳历录入
     */
    SOLAR(1, "阳历"),

    /**
     * 阴历（农历）录入
     */
    LUNAR(2, "阴历");

    private final int code;
    private final String description;

    /**
     * 校验历法码值：null 视为未记录直接放行，非空必须为已定义码值
     *
     * @param code 历法码值
     * @throws BusinessException 码值非法时抛出
     */
    public static void validateCode(Integer code) {
        if (code == null) {
            return;
        }
        for (BirthCalendarEnum value : values()) {
            if (value.code == code) {
                return;
            }
        }
        throw new BusinessException("出生日期历法须为1阳历/2阴历");
    }
}
