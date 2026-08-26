package com.mouhin.family.tree.common.enums;

import com.mouhin.family.tree.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 留言分类枚举
 *
 * @author Family-Tree
 * @date 2026-08-26
 */
@Getter
@AllArgsConstructor
public enum MessageCategoryEnum {

    GENERAL("GENERAL", "普通留言"),
    FEATURE("FEATURE", "功能需求");

    private final String code;
    private final String description;

    /**
     * 严格解析：非法编码抛出业务异常（用于入参校验）。
     *
     * @param code 分类编码
     * @return 对应枚举
     */
    public static MessageCategoryEnum fromCode(String code) {
        if (code != null) {
            for (MessageCategoryEnum value : values()) {
                if (value.code.equals(code)) {
                    return value;
                }
            }
        }
        throw new BusinessException("无效的留言分类");
    }
}
