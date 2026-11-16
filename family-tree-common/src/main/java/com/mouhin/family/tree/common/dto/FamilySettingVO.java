package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 家族配置项视图对象（含 schema 描述，供平台管理页直接渲染表单）
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Getter
@Setter
public class FamilySettingVO {

    /**
     * 配置键编码
     */
    private String key;

    /**
     * 中文名称
     */
    private String label;

    /**
     * 值类型编码（BOOLEAN / INTEGER / STRING）
     */
    private String valueType;

    /**
     * 生效值：家族已覆盖时取覆盖值，否则取默认值
     */
    private String value;

    /**
     * 默认值
     */
    private String defaultValue;

    /**
     * 生效值是否来自家族覆盖（false 表示当前展示的是默认值）
     */
    private Boolean overridden;

    /**
     * 数值型配置最小值（含），不适用时为 null
     */
    private Integer minValue;

    /**
     * 数值型配置最大值（含），不适用时为 null
     */
    private Integer maxValue;

    /**
     * 文本型配置最大长度，不适用时为 null
     */
    private Integer maxLength;

    /**
     * 用途说明
     */
    private String description;
}
