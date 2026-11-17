package com.mouhin.family.tree.domain.service;

import com.mouhin.family.tree.common.enums.FamilySettingKeyEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilySetting;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 家族平台配置领域服务单元测试：覆盖默认值合并、开关 / 数值校验与规整，以及非法输入的失败关闭。
 *
 * @author Family-Tree
 * @date 2026-11-15
 */
class FamilySettingDomainServiceTest {

    private static final Long FAMILY_ID = 100L;

    private final FamilySettingDomainService service = new FamilySettingDomainService();

    @Test
    void mergeFillsMissingKeysWithDefaults() {
        Map<String, String> effective = service.mergeWithDefaults(null, null);

        assertEquals(FamilySettingKeyEnum.MAX_NODE_COUNT.getDefaultValue(),
                effective.get(FamilySettingKeyEnum.MAX_NODE_COUNT.getCode()));
    }

    @Test
    void mergeIgnoresBlankOverride() {
        Map<String, String> overrides = new HashMap<>(2);
        overrides.put(FamilySettingKeyEnum.AI_FEATURE_ENABLED.getCode(), "  ");

        Map<String, String> effective = service.mergeWithDefaults(overrides, null);

        assertTrue(service.isEnabled(effective, FamilySettingKeyEnum.AI_FEATURE_ENABLED));
    }

    @Test
    void mergeAppliesOverride() {
        Map<String, String> overrides = new HashMap<>(2);
        overrides.put(FamilySettingKeyEnum.DATA_EXPORT_ENABLED.getCode(), "false");

        Map<String, String> effective = service.mergeWithDefaults(overrides, null);

        assertFalse(service.isEnabled(effective, FamilySettingKeyEnum.DATA_EXPORT_ENABLED));
    }

    @Test
    void mergeTakesPlatformLayerWhenFamilyNotOverridden() {
        Map<String, String> platformDefaults = new HashMap<>(2);
        platformDefaults.put(FamilySettingKeyEnum.MAX_NODE_COUNT.getCode(), "3000");
        platformDefaults.put(FamilySettingKeyEnum.DATA_EXPORT_ENABLED.getCode(), "false");

        Map<String, String> effective = service.mergeWithDefaults(null, platformDefaults);

        assertFalse(service.isEnabled(effective, FamilySettingKeyEnum.DATA_EXPORT_ENABLED));
        assertEquals(3000, service.getInt(effective, FamilySettingKeyEnum.MAX_NODE_COUNT));
    }

    @Test
    void familyOverrideBeatsPlatformLayer() {
        Map<String, String> platformDefaults = new HashMap<>(2);
        platformDefaults.put(FamilySettingKeyEnum.MAX_NODE_COUNT.getCode(), "3000");
        Map<String, String> overrides = new HashMap<>(2);
        overrides.put(FamilySettingKeyEnum.MAX_NODE_COUNT.getCode(), "800");

        Map<String, String> effective = service.mergeWithDefaults(overrides, platformDefaults);

        assertEquals(800, service.getInt(effective, FamilySettingKeyEnum.MAX_NODE_COUNT));
    }

    @Test
    void isEnabledFallsBackToDefaultWhenKeyAbsent() {
        // 已登记开关的默认值均为 true：缺失配置项时按默认放行
        assertTrue(service.isEnabled(Map.of(), FamilySettingKeyEnum.DATA_EXPORT_ENABLED));
        assertTrue(service.isEnabled(null, FamilySettingKeyEnum.AI_FEATURE_ENABLED));
    }

    @Test
    void getIntFallsBackToDefaultOnMalformedValue() {
        Map<String, String> effective = new HashMap<>(2);
        effective.put(FamilySettingKeyEnum.MAX_NODE_COUNT.getCode(), "not-a-number");

        assertEquals(Integer.parseInt(FamilySettingKeyEnum.MAX_NODE_COUNT.getDefaultValue()),
                service.getInt(effective, FamilySettingKeyEnum.MAX_NODE_COUNT));
        assertEquals(Integer.parseInt(FamilySettingKeyEnum.MAX_NODE_COUNT.getDefaultValue()),
                service.getInt(null, FamilySettingKeyEnum.MAX_NODE_COUNT));
    }

    @Test
    void getIntParsesOverride() {
        Map<String, String> effective = new HashMap<>(2);
        effective.put(FamilySettingKeyEnum.MAX_NODE_COUNT.getCode(), " 8888 ");

        assertEquals(8888, service.getInt(effective, FamilySettingKeyEnum.MAX_NODE_COUNT));
    }

    @Test
    void booleanValueIsNormalizedToLowercase() {
        List<FamilySetting> saved = service.toValidatedEntities(FAMILY_ID,
                Map.of(FamilySettingKeyEnum.AI_FEATURE_ENABLED.getCode(), "TRUE"));

        assertEquals(1, saved.size());
        assertEquals("true", saved.get(0).getSettingValue());
        assertEquals(FAMILY_ID, saved.get(0).getFamilyId());
    }

    @Test
    void integerValueKeepsParsedForm() {
        List<FamilySetting> saved = service.toValidatedEntities(FAMILY_ID,
                Map.of(FamilySettingKeyEnum.MAX_NODE_COUNT.getCode(), " 6000 "));

        assertEquals("6000", saved.get(0).getSettingValue());
    }

    @Test
    void booleanRejectsNonBooleanText() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.toValidatedEntities(FAMILY_ID,
                Map.of(FamilySettingKeyEnum.DATA_EXPORT_ENABLED.getCode(), "yes")));

        assertEquals("配置项「数据导出」仅接受 true 或 false", ex.getMessage());
    }

    @Test
    void integerRejectsValueOutOfRange() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.toValidatedEntities(FAMILY_ID,
                Map.of(FamilySettingKeyEnum.MAX_NODE_COUNT.getCode(), "9")));

        assertTrue(ex.getMessage().contains("须在"));
    }

    @Test
    void integerRejectsNonNumericText() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.toValidatedEntities(FAMILY_ID,
                Map.of(FamilySettingKeyEnum.MAX_NODE_COUNT.getCode(), "5000abc")));

        assertEquals("配置项「节点数量上限」必须是整数", ex.getMessage());
    }

    @Test
    void unknownKeyIsRejected() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.toValidatedEntities(FAMILY_ID,
                Map.of("NOT_A_KEY", "true")));

        assertEquals("不支持的配置项：NOT_A_KEY", ex.getMessage());
    }

    @Test
    void blankValueIsRejected() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.toValidatedEntities(FAMILY_ID,
                Map.of(FamilySettingKeyEnum.GEDCOM_IMPORT_ENABLED.getCode(), "   ")));

        assertEquals("配置项「GEDCOM 导入」不能为空", ex.getMessage());
    }

    @Test
    void emptyOrMissingInputFailsClosed() {
        assertEquals("缺少家族ID",
                assertThrows(BusinessException.class, () -> service.toValidatedEntities(null,
                        Map.of(FamilySettingKeyEnum.AI_FEATURE_ENABLED.getCode(), "true"))).getMessage());
        assertEquals("请至少提交一项配置",
                assertThrows(BusinessException.class, () -> service.toValidatedEntities(FAMILY_ID,
                        new LinkedHashMap<>())).getMessage());
        assertEquals("请至少提交一项配置",
                assertThrows(BusinessException.class, () -> service.toValidatedEntities(FAMILY_ID, null)).getMessage());
    }

    @Test
    void isDefaultValueComparesWithCodeDefaultWhenPlatformLayerAbsent() {
        FamilySetting setting = onlySetting(FamilySettingKeyEnum.MAX_NODE_COUNT.getCode(), "5000");

        assertTrue(service.isDefaultValue(setting, null));
    }

    @Test
    void isDefaultValueUsesPlatformValueInsteadOfCodeDefault() {
        FamilySetting setting = onlySetting(FamilySettingKeyEnum.MAX_NODE_COUNT.getCode(), "30000");
        Map<String, String> platformDefaults = Map.of(FamilySettingKeyEnum.MAX_NODE_COUNT.getCode(), "30000");

        assertTrue(service.isDefaultValue(setting, platformDefaults));
        assertFalse(service.isDefaultValue(onlySetting(FamilySettingKeyEnum.MAX_NODE_COUNT.getCode(), "5000"),
                platformDefaults));
    }

    @Test
    void isDefaultValueFallsBackToCodeDefaultOnBlankPlatformValue() {
        FamilySetting setting = onlySetting(FamilySettingKeyEnum.MAX_NODE_COUNT.getCode(), "5000");
        Map<String, String> platformDefaults = Map.of(FamilySettingKeyEnum.MAX_NODE_COUNT.getCode(), "  ");

        assertTrue(service.isDefaultValue(setting, platformDefaults));
    }

    private FamilySetting onlySetting(String key, String value) {
        return service.toValidatedEntities(FAMILY_ID, Map.of(key, value)).getFirst();
    }
}
