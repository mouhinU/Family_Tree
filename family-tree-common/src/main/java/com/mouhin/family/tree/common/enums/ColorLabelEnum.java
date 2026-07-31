package com.mouhin.family.tree.common.enums;

import com.mouhin.family.tree.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 节点颜色标注枚举
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@Getter
@AllArgsConstructor
public enum ColorLabelEnum {

    DEFAULT("default", "#4A90D9", "默认"),
    PATERNAL("paternal", "#E74C3C", "父系"),
    MATERNAL("maternal", "#F39C12", "母系"),
    SPOUSE_FAMILY("spouse_family", "#9B59B6", "姻亲"),
    ADOPTED("adopted", "#27AE60", "过继"),
    HIGHLIGHT("highlight", "#E91E63", "高亮");

    private final String code;
    private final String hexColor;
    private final String description;

    /**
     * 宽容解析：非法 code 回退为 DEFAULT。仅用于读取渲染兜底，避免历史脏数据导致渲染失败。
     */
    public static ColorLabelEnum fromCode(String code) {
        for (ColorLabelEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return DEFAULT;
    }

    /**
     * 严格校验：code 合法则通过，非法则抛业务异常。用于创建/更新等写入路径，防止非法颜色入库。
     */
    public static void validateCode(String code) {
        for (ColorLabelEnum value : values()) {
            if (value.code.equals(code)) {
                return;
            }
        }
        throw new BusinessException("无效的颜色标注：" + code);
    }
}
