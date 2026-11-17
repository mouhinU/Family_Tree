package com.mouhin.family.tree.common.enums;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 平台级配置键白名单。
 * <p>
 * 平台管理页只能读写本枚举登记的键，未登记的键一律拒绝。配置项分两类：
 * <ul>
 *     <li>与 {@code FamilySettingKeyEnum} 同名的键：作为家族配置的"平台默认值"，
 *     生效链为 家族覆盖 → 平台覆盖 → 本枚举默认值；</li>
 *     <li>平台独有键：全站统一生效，不存在家族覆盖层。</li>
 * </ul>
 * platform_setting 只存被显式覆盖的值，新增配置项只需在此登记并在使用点校验，不需要改表。
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Getter
public enum PlatformSettingKeyEnum {

    /**
     * AI 能力总开关的平台默认值
     */
    AI_FEATURE_ENABLED("AI_FEATURE_ENABLED", "AI 智能功能默认", SettingValueTypeEnum.BOOLEAN,
            "true", null, null, null,
            "新建家族的默认值；家族未单独覆盖时，全站 AI 智能录入、族谱问答、人物故事与 OCR 按此开关生效"),

    /**
     * 数据导出开关的平台默认值
     */
    DATA_EXPORT_ENABLED("DATA_EXPORT_ENABLED", "数据导出默认", SettingValueTypeEnum.BOOLEAN,
            "true", null, null, null,
            "新建家族的默认值；家族未单独覆盖时，是否允许导出族谱数据（含 GEDCOM 文件）按此开关生效"),

    /**
     * GEDCOM 导入开关的平台默认值
     */
    GEDCOM_IMPORT_ENABLED("GEDCOM_IMPORT_ENABLED", "GEDCOM 导入默认", SettingValueTypeEnum.BOOLEAN,
            "true", null, null, null,
            "新建家族的默认值；家族未单独覆盖时，是否允许通过 GEDCOM 文件导入族谱按此开关生效"),

    /**
     * 单家族节点数量上限的平台默认值
     */
    MAX_NODE_COUNT("MAX_NODE_COUNT", "节点数量上限默认", SettingValueTypeEnum.INTEGER,
            "5000", 10, 200000, null,
            "新建家族的默认值；家族未单独覆盖时，族谱节点总数按此上限校验"),

    /**
     * 真人注册总开关：平台独有配置，无家族覆盖层
     */
    REGISTER_ENABLED("REGISTER_ENABLED", "开放注册", SettingValueTypeEnum.BOOLEAN,
            "true", null, null, null,
            "关闭后所有新账号无法自助注册，已有账号登录与系统代开账号不受影响"),

    /**
     * 邀请码加入家族开关：平台独有配置，无家族覆盖层
     */
    INVITE_JOIN_ENABLED("INVITE_JOIN_ENABLED", "邀请码加入", SettingValueTypeEnum.BOOLEAN,
            "true", null, null, null,
            "关闭后成员不能凭邀请码加入其他家族，家族内已有的成员关系不受影响"),

    /**
     * 全平台家族总数上限：平台独有配置，无家族覆盖层
     */
    MAX_FAMILY_COUNT("MAX_FAMILY_COUNT", "家族总数上限", SettingValueTypeEnum.INTEGER,
            "200", 1, 1000000, null,
            "全平台可创建的家族总数上限，达到上限后新建家族将被拒绝，用于控制容量与成本"),

    /**
     * 单家族成员数上限：平台独有配置，无家族覆盖层
     */
    MAX_FAMILY_MEMBERS("MAX_FAMILY_MEMBERS", "家族成员上限", SettingValueTypeEnum.INTEGER,
            "50", 1, 100000, null,
            "单个家族可拥有的成员数上限，达到上限后凭邀请码加入家族将被拒绝，已在册成员不受影响"),

    /**
     * 单账号可拥有的家族数上限：平台独有配置，无家族覆盖层
     */
    MAX_FAMILY_PER_USER("MAX_FAMILY_PER_USER", "每账号家族数上限", SettingValueTypeEnum.INTEGER,
            "1", 1, 100, null,
            "单个账号可归属的家族数上限，默认 1 即一人只能属于一个家族；调到 N 后已在册成员可再创建或加入家族，"
                    + "并在过渡页切换当前家族");

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
     * 默认值：平台未显式覆盖时生效的取值
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

    PlatformSettingKeyEnum(String code, String label, SettingValueTypeEnum valueType, String defaultValue,
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
     * 按编码查找配置键，未登记时返回空。
     *
     * @param code 配置键编码
     * @return 配置键枚举，未知编码时为空
     */
    public static Optional<PlatformSettingKeyEnum> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        for (PlatformSettingKeyEnum key : values()) {
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
    public static List<PlatformSettingKeyEnum> listAll() {
        List<PlatformSettingKeyEnum> keys = new ArrayList<>(values().length);
        Collections.addAll(keys, values());
        return keys;
    }

    /**
     * 判断该配置键是否同时是家族级配置键，即它充当家族配置的平台默认层。
     *
     * @return 存在同名家族配置键时返回 true
     */
    public boolean isFamilyScoped() {
        return FamilySettingKeyEnum.findByCode(code).isPresent();
    }
}
