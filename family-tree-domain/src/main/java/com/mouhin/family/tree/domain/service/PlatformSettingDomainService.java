package com.mouhin.family.tree.domain.service;

import com.mouhin.family.tree.common.enums.PlatformSettingKeyEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.PlatformSetting;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 平台配置领域服务：配置键白名单校验、取值规范化与默认值合并。
 * <p>
 * 平台层既是平台独有配置的生效值来源，也是家族配置三级生效链的中间层：
 * 家族未覆盖时取平台覆盖值，平台也未覆盖时取代码默认值。
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Service
public class PlatformSettingDomainService {

    /**
     * 平台独有配置被关闭时的统一提示后缀
     */
    private static final String DISABLED_MESSAGE_SUFFIX = "已被平台管理员关闭";

    /**
     * 合并后的平台生效配置：配置键编码 → 生效值（覆盖值优先，缺省回落代码默认值）
     *
     * @param overrides 平台已显式覆盖的配置，可为 null
     * @return 全部已登记配置键的生效值，按枚举声明顺序排列
     */
    public Map<String, String> mergeWithDefaults(Map<String, String> overrides) {
        Map<String, String> effective = new LinkedHashMap<>(PlatformSettingKeyEnum.listAll().size() * 2);
        for (PlatformSettingKeyEnum key : PlatformSettingKeyEnum.listAll()) {
            String overridden = overrides == null ? null : overrides.get(key.getCode());
            effective.put(key.getCode(),
                    overridden == null || overridden.isBlank() ? key.getDefaultValue() : overridden);
        }
        return effective;
    }

    /**
     * 挑出可作为家族配置默认层的平台生效值。
     * <p>
     * 只有与家族配置键同名的平台键才参与家族生效链，平台独有键在此过滤掉。
     *
     * @param platformEffective {@link #mergeWithDefaults} 返回的平台生效配置
     * @return 家族级配置键编码 → 平台层取值
     */
    public Map<String, String> familyScopedDefaults(Map<String, String> platformEffective) {
        Map<String, String> defaults = new LinkedHashMap<>(PlatformSettingKeyEnum.listAll().size() * 2);
        if (platformEffective == null) {
            return defaults;
        }
        for (PlatformSettingKeyEnum key : PlatformSettingKeyEnum.listAll()) {
            if (key.isFamilyScoped()) {
                defaults.put(key.getCode(), platformEffective.get(key.getCode()));
            }
        }
        return defaults;
    }

    /**
     * 把平台侧提交的原始配置批量校验并转换为待落库实体。
     * <p>
     * 同一配置键重复提交时以最后一条为准；未登记的键与非法取值直接拒绝，避免脏配置影响业务判定。
     *
     * @param rawInput 配置键编码 → 原始配置值，条目数不得超过已登记配置键总数
     * @return 校验通过的配置实体列表
     */
    public List<PlatformSetting> toValidatedEntities(Map<String, String> rawInput) {
        if (rawInput == null || rawInput.isEmpty()) {
            throw new BusinessException("请至少提交一项配置");
        }
        int maxItems = PlatformSettingKeyEnum.listAll().size();
        if (rawInput.size() > maxItems) {
            throw new BusinessException("单次最多提交" + maxItems + "项配置");
        }

        Map<String, PlatformSetting> byKey = new LinkedHashMap<>(maxItems * 2);
        for (Map.Entry<String, String> entry : rawInput.entrySet()) {
            PlatformSettingKeyEnum keyEnum = resolveKey(entry.getKey());
            PlatformSetting setting = new PlatformSetting();
            setting.setSettingKey(keyEnum.getCode());
            setting.setSettingValue(SettingValueNormalizer.normalize(keyEnum.getLabel(), keyEnum.getValueType(),
                    keyEnum.getMinValue(), keyEnum.getMaxValue(), keyEnum.getMaxLength(), entry.getValue()));
            setting.validate();
            byKey.put(keyEnum.getCode(), setting);
        }
        return List.copyOf(byKey.values());
    }

    /**
     * 判断提交值是否等同于代码默认值：等同时无需保留覆盖行。
     *
     * @param setting 校验后的配置实体
     * @return 是否与默认值一致
     */
    public boolean isDefaultValue(PlatformSetting setting) {
        PlatformSettingKeyEnum keyEnum = resolveKey(setting.getSettingKey());
        return keyEnum.getDefaultValue().equals(setting.getSettingValue());
    }

    /**
     * 判断开关型配置的生效值是否为开启。
     *
     * @param effective {@link #mergeWithDefaults} 返回的生效配置
     * @param key       配置键
     * @return 是否开启
     */
    public boolean isEnabled(Map<String, String> effective, PlatformSettingKeyEnum key) {
        if (effective == null) {
            return Boolean.parseBoolean(key.getDefaultValue());
        }
        return Boolean.parseBoolean(effective.getOrDefault(key.getCode(), key.getDefaultValue()));
    }

    /**
     * 读取数值型配置的生效值。
     *
     * @param effective {@link #mergeWithDefaults} 返回的生效配置
     * @param key       配置键
     * @return 生效数值；取值非法时回落默认值
     */
    public int getInt(Map<String, String> effective, PlatformSettingKeyEnum key) {
        String raw = effective == null ? null : effective.get(key.getCode());
        if (raw == null || raw.isBlank()) {
            raw = key.getDefaultValue();
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return Integer.parseInt(key.getDefaultValue());
        }
    }

    /**
     * 拼装平台独有开关被关闭时的提示文案。
     *
     * @param key 配置键
     * @return 面向用户的错误信息
     */
    public String disabledMessage(PlatformSettingKeyEnum key) {
        return key.getLabel() + DISABLED_MESSAGE_SUFFIX;
    }

    /**
     * 按编码解析配置键，未登记时拒绝。
     *
     * @param code 配置键编码
     * @return 配置键枚举
     */
    private PlatformSettingKeyEnum resolveKey(String code) {
        Optional<PlatformSettingKeyEnum> found = PlatformSettingKeyEnum.findByCode(code);
        return found.orElseThrow(() -> new BusinessException("不支持的配置项：" + code));
    }
}
