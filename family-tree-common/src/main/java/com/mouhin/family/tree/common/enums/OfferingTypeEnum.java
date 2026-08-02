package com.mouhin.family.tree.common.enums;

import com.mouhin.family.tree.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 祭奠类型枚举
 *
 * @author Family-Tree
 * @date 2026-08-01
 */
@Getter
@AllArgsConstructor
public enum OfferingTypeEnum {

    INCENSE(1, "香烛"),
    PAPER(2, "烧纸");

    private final int code;
    private final String description;

    /**
     * 严格解析：非法编码抛出业务异常（用于入参校验）。
     *
     * @param code 祭奠类型编码
     * @return 对应枚举
     */
    public static OfferingTypeEnum fromCode(Integer code) {
        if (code != null) {
            for (OfferingTypeEnum value : values()) {
                if (value.code == code) {
                    return value;
                }
            }
        }
        throw new BusinessException("无效的祭奠类型");
    }
}
