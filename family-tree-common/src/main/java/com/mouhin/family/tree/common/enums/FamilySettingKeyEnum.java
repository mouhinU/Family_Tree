package com.mouhin.family.tree.common.enums;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 家族级配置键白名单。
 * <p>
 * 平台管理页只能读写本枚举登记的键，未登记的键一律拒绝，避免任意键写脏数据。
 * 新增配置项只需在此登记并在使用点校验，不需要改表：family_setting 只存被显式覆盖的值，
 * 其余取值由 {@code defaultValue} 提供。
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Getter
public enum FamilySettingKeyEnum {

    /**
     * AI 能力总开关：智能录入、族谱问答、人物故事、OCR 识别
     */
    AI_FEATURE_ENABLED("AI_FEATURE_ENABLED", "AI 智能功能", SettingValueTypeEnum.BOOLEAN,
            "true", null, null, null,
            "关闭后该家族的成员无法使用智能录入、族谱问答、人物故事与 OCR 识别"),

    /**
     * 数据导出开关：结构化数据导出与 GEDCOM 导出
     */
    DATA_EXPORT_ENABLED("DATA_EXPORT_ENABLED", "数据导出", SettingValueTypeEnum.BOOLEAN,
            "true", null, null, null,
            "关闭后该家族不能导出族谱数据（含 GEDCOM 文件）"),

    /**
     * GEDCOM 导入开关：整树导入与追加导入
     */
    GEDCOM_IMPORT_ENABLED("GEDCOM_IMPORT_ENABLED", "GEDCOM 导入", SettingValueTypeEnum.BOOLEAN,
            "true", null, null, null,
            "关闭后该家族不能通过 GEDCOM 文件导入族谱"),

    /**
     * 单家族节点数量上限，新增节点时由应用层按当前节点数校验
     */
    MAX_NODE_COUNT("MAX_NODE_COUNT", "节点数量上限", SettingValueTypeEnum.INTEGER,
            "5000", 10, 200000, null,
            "该家族族谱节点总数的上限，达到上限后新增节点将被拒绝");

    /**
     * 配置键编码，落库值与接口入参均使用该编码
     */
    private final String code;

    /**
     * 中文名称，用于平台管理页展示
     */
    private final String label;

    /**
     * 值类型，决定前端控件形态与后端校验规则
     */
    private final SettingValueTypeEnum valueType;

    /**
     * 默认值：家族未显式覆盖时生效的取值
     */
    private final String defaultValue;

    /**
     * 数值型配置的最小值（含），非数值型为 null
     */
    private final Integer minValue;

    /**
     * 数值型配置的最大值（含），非数值型为 null
     */
    private final Integer maxValue;

    /**
     * 文本型配置的最大长度，非文本型为 null
     */
    private final Integer maxLength;

    /**
     * 配置用途说明，用于平台管理页提示文案
     */
    private final String description;

    FamilySettingKeyEnum(String code, String label, SettingValueTypeEnum valueType, String defaultValue,
                         Integer minValue, Integer maxValue, Integer maxLength, String description) {
        this.code = code;
        this.label = label;
        this.valueType = valueType;
        this.defaultValue = defaultValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.maxLength = maxLength;
        this.description = description;
    }

    /**
     * 按编码查找配置键，未登记的编码返回空。
     *
     * @param code 配置键编码
     * @return 配置键枚举，未知编码时为空
     */
    public static Optional<FamilySettingKeyEnum> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        for (FamilySettingKeyEnum key : values()) {
            if (key.code.equals(code.trim())) {
                return Optional.of(key);
            }
        }
        return Optional.empty();
    }

    /**
     * 全部已登记的配置键，按枚举声明顺序返回。
     *
     * @return 配置键列表
     */
    public static List<FamilySettingKeyEnum> listAll() {
        List<FamilySettingKeyEnum> keys = new ArrayList<>(values().length);
        Collections.addAll(keys, values());
        return keys;
    }
}
