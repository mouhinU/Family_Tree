package com.mouhin.family.tree.domain.service;

import com.mouhin.family.tree.common.enums.FamilySettingKeyEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilySetting;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 家族配置领域服务：配置键白名单校验、取值规范化与三级默认值合并。
 * <p>
 * 平台管理页保存与业务侧读取共用本服务，保证"能写进去的值"与"业务判定读到的值"同一套规则。
 * 生效链为 家族覆盖 → 平台覆盖 → 代码默认，平台层由 {@code PlatformSettingDomainService} 产出。
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Service
public class FamilySettingDomainService {

    /**
     * 合并出家族级配置的生效值：家族覆盖 → 平台覆盖 → 代码默认。
     *
     * @param overrides        家族已显式覆盖的配置，可为 null
     * @param platformDefaults 平台层生效值（{@code PlatformSettingDomainService} 产出），可为 null
     * @return 全部已登记配置键的生效值，按枚举声明顺序排列
     */
    public Map<String, String> mergeWithDefaults(Map<String, String> overrides, Map<String, String> platformDefaults) {
        Map<String, String> effective = new LinkedHashMap<>(FamilySettingKeyEnum.listAll().size() * 2);
        for (FamilySettingKeyEnum key : FamilySettingKeyEnum.listAll()) {
            effective.put(key.getCode(), firstNotBlank(
                    overrides == null ? null : overrides.get(key.getCode()),
                    platformDefaults == null ? null : platformDefaults.get(key.getCode()),
                    key.getDefaultValue()));
        }
        return effective;
    }

    /**
     * 把平台侧提交的原始配置批量校验并转换为待落库实体。
     * <p>
     * 同一配置键重复提交时以最后一条为准；未登记的键与非法取值直接拒绝，避免脏配置影响业务判定。
     *
     * @param familyId 家族ID
     * @param rawInput 配置键编码 → 原始配置值，条目数不得超过已登记配置键总数
     * @return 校验通过的配置实体列表
     */
    public List<FamilySetting> toValidatedEntities(Long familyId, Map<String, String> rawInput) {
        if (familyId == null) {
            throw new BusinessException("缺少家族ID");
        }
        if (rawInput == null || rawInput.isEmpty()) {
            throw new BusinessException("请至少提交一项配置");
        }
        int maxItems = FamilySettingKeyEnum.listAll().size();
        if (rawInput.size() > maxItems) {
            throw new BusinessException("单次最多提交" + maxItems + "项配置");
        }

        Map<String, FamilySetting> byKey = new LinkedHashMap<>(maxItems * 2);
        for (Map.Entry<String, String> entry : rawInput.entrySet()) {
            FamilySettingKeyEnum keyEnum = resolveKey(entry.getKey());
            FamilySetting setting = new FamilySetting();
            setting.setFamilyId(familyId);
            setting.setSettingKey(keyEnum.getCode());
            setting.setSettingValue(normalize(keyEnum, entry.getValue()));
            setting.validate();
            byKey.put(keyEnum.getCode(), setting);
        }
        return List.copyOf(byKey.values());
    }

    /**
     * 判断开关型配置的生效值是否为开启。
     *
     * @param effective {@link #mergeWithDefaults} 返回的生效配置
     * @param key       配置键
     * @return 是否开启
     */
    public boolean isEnabled(Map<String, String> effective, FamilySettingKeyEnum key) {
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
    public int getInt(Map<String, String> effective, FamilySettingKeyEnum key) {
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
     * 判断提交值是否等同于上层默认值：等同时家族层不必保留覆盖行，直接回落继承。
     * <p>
     * 家族层的「默认」是平台层生效值（平台覆盖 → 代码默认），而非配置键自带的代码默认值；
     * 否则平台改过默认后，家族按平台值提交会被误判成自定义覆盖而永远删不掉。
     *
     * @param setting          校验后的配置实体
     * @param platformDefaults 平台层生效值，可为 null
     * @return 是否与上层默认值一致
     */
    public boolean isDefaultValue(FamilySetting setting, Map<String, String> platformDefaults) {
        FamilySettingKeyEnum keyEnum = resolveKey(setting.getSettingKey());
        String inherited = platformDefaults == null ? null : platformDefaults.get(keyEnum.getCode());
        String parentDefault = firstNotBlank(inherited, keyEnum.getDefaultValue());
        return parentDefault != null && parentDefault.equals(setting.getSettingValue());
    }

    /**
     * 按编码解析配置键，未登记时拒绝。
     *
     * @param code 配置键编码
     * @return 配置键枚举
     */
    private FamilySettingKeyEnum resolveKey(String code) {
        Optional<FamilySettingKeyEnum> found = FamilySettingKeyEnum.findByCode(code);
        return found.orElseThrow(() -> new BusinessException("不支持的配置项：" + code));
    }

    /**
     * 按配置键声明的值类型规范化并校验取值。
     *
     * @param keyEnum 配置键
     * @param raw     原始取值
     * @return 规范化后的取值
     */
    private String normalize(FamilySettingKeyEnum keyEnum, String raw) {
        return SettingValueNormalizer.normalize(keyEnum.getLabel(), keyEnum.getValueType(),
                keyEnum.getMinValue(), keyEnum.getMaxValue(), keyEnum.getMaxLength(), raw);
    }

    /**
     * 返回第一个非空白取值，用于按优先级择层。
     *
     * @param candidates 由高到低优先级的候选取值
     * @return 首个非空白值；全部为空时返回 null
     */
    private String firstNotBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }
}
