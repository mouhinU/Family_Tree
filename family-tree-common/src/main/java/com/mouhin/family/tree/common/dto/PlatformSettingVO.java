package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 平台级配置项视图对象（含 schema 描述，供平台管理页直接渲染表单）
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Getter
@Setter
public class PlatformSettingVO {

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
     * 平台生效值：平台已覆盖时取覆盖值，否则取代码默认值
     */
    private String value;

    /**
     * 代码默认值
     */
    private String defaultValue;

    /**
     * 生效值是否来自平台覆盖（false 表示当前展示的是代码默认值）
     */
    private Boolean overridden;

    /**
     * 是否属于家族可覆盖的配置键：true 时本值仅作为新建家族的默认值，家族覆盖优先于本值
     */
    private Boolean familyScoped;

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

    @Override
    public String toString() {
        return "PlatformSettingVO{key=" + key + ", label=" + label + ", valueType=" + valueType
                + ", value=" + value + ", defaultValue=" + defaultValue + ", overridden=" + overridden
                + ", familyScoped=" + familyScoped + "}";
    }
}
