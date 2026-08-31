package com.mouhin.family.tree.common.enums;

import com.mouhin.family.tree.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 纪念日分类枚举
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@AllArgsConstructor
public enum AnniversaryCategoryEnum {

    WEDDING("wedding", "结婚周年"),
    SCHOOL("school", "入学/毕业"),
    MEMORIAL("memorial", "纪念"),
    OTHER("other", "其他");

    private final String code;
    private final String description;

    /**
     * 严格解析：非法编码抛出业务异常（用于入参校验）。
     *
     * @param code 分类编码
     * @return 对应枚举
     */
    public static AnniversaryCategoryEnum fromCode(String code) {
        if (code != null) {
            for (AnniversaryCategoryEnum value : values()) {
                if (value.code.equals(code)) {
                    return value;
                }
            }
        }
        throw new BusinessException("无效的纪念日分类");
    }
}
