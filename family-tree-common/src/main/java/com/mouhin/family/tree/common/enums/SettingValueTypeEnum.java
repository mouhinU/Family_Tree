package com.mouhin.family.tree.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 家族配置值类型。
 * <p>
 * 决定平台管理页渲染何种控件，以及后端保存时按何种规则校验取值。
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Getter
@AllArgsConstructor
public enum SettingValueTypeEnum {

    /**
     * 布尔开关，落库取值固定为 "true" / "false"
     */
    BOOLEAN("BOOLEAN", "开关"),

    /**
     * 整数阈值，落库取值须落在配置键定义的闭区间内
     */
    INTEGER("INTEGER", "数值"),

    /**
     * 字符串配置，落库长度须不超过配置键定义的最大长度
     */
    STRING("STRING", "文本");

    /**
     * 类型编码，随接口下发给前端作为控件判定依据
     */
    private final String code;

    /**
     * 中文名称，用于平台管理页展示
     */
    private final String label;
}
