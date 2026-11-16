package com.mouhin.family.tree.domain.service;

import com.mouhin.family.tree.common.enums.SettingValueTypeEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilySetting;

/**
 * 配置取值规范化器：家族级与平台级配置共用同一套按值类型的校验规则。
 * <p>
 * 抽出共性的原因是"平台管理页能写进去的值"必须与"业务判定读到的值"完全一致，
 * 两处各自实现会随配置项增多而漂移。
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
final class SettingValueNormalizer {

    /**
     * 布尔配置允许的取值
     */
    private static final String TRUE_TEXT = "true";

    /**
     * 布尔配置允许的取值
     */
    private static final String FALSE_TEXT = "false";

    private SettingValueNormalizer() {
    }

    /**
     * 按配置键声明的值类型规范化并校验取值。
     *
     * @param label     配置项中文名，用于错误提示
     * @param type      值类型
     * @param minValue  数值型最小值（含），不适用时为 null
     * @param maxValue  数值型最大值（含），不适用时为 null
     * @param maxLength 文本型最大长度，不适用时为 null
     * @param raw       原始取值
     * @return 规范化后的取值
     */
    static String normalize(String label, SettingValueTypeEnum type, Integer minValue,
                            Integer maxValue, Integer maxLength, String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new BusinessException("配置项「" + label + "」不能为空");
        }
        return switch (type) {
            case BOOLEAN -> normalizeBoolean(label, value);
            case INTEGER -> normalizeInteger(label, minValue, maxValue, value);
            case STRING -> normalizeString(label, maxLength, value);
        };
    }

    private static String normalizeBoolean(String label, String value) {
        String lowered = value.toLowerCase();
        if (!TRUE_TEXT.equals(lowered) && !FALSE_TEXT.equals(lowered)) {
            throw new BusinessException("配置项「" + label + "」仅接受 true 或 false");
        }
        return lowered;
    }

    private static String normalizeInteger(String label, Integer minValue, Integer maxValue, String value) {
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new BusinessException("配置项「" + label + "」必须是整数");
        }
        if (minValue != null && parsed < minValue) {
            throw new BusinessException("配置项「" + label + "」须在 " + minValue + " 至 " + maxValue + " 之间");
        }
        if (maxValue != null && parsed > maxValue) {
            throw new BusinessException("配置项「" + label + "」须在 " + minValue + " 至 " + maxValue + " 之间");
        }
        return String.valueOf(parsed);
    }

    private static String normalizeString(String label, Integer maxLength, String value) {
        int limit = maxLength == null ? FamilySetting.MAX_VALUE_LENGTH : maxLength;
        if (value.length() > limit) {
            throw new BusinessException("配置项「" + label + "」长度不能超过" + limit);
        }
        return value;
    }
}
