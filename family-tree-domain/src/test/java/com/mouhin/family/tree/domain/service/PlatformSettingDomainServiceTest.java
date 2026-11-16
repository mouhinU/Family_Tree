package com.mouhin.family.tree.domain.service;

import com.mouhin.family.tree.common.enums.FamilySettingKeyEnum;
import com.mouhin.family.tree.common.enums.PlatformSettingKeyEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.PlatformSetting;
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
 * 平台配置领域服务单元测试：覆盖默认值合并、家族层筛选、取值校验与"等值即默认"判定。
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
class PlatformSettingDomainServiceTest {

    private final PlatformSettingDomainService service = new PlatformSettingDomainService();

    private PlatformSetting setting(String key, String value) {
        PlatformSetting setting = new PlatformSetting();
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        return setting;
    }

    @Test
    void mergeFillsEveryRegisteredKeyWithDefault() {
        Map<String, String> effective = service.mergeWithDefaults(null);

        assertEquals(PlatformSettingKeyEnum.listAll().size(), effective.size());
        assertEquals(PlatformSettingKeyEnum.MAX_FAMILY_COUNT.getDefaultValue(),
                effective.get(PlatformSettingKeyEnum.MAX_FAMILY_COUNT.getCode()));
    }

    @Test
    void mergeAppliesOverrideAndIgnoresBlank() {
        Map<String, String> overrides = new HashMap<>(4);
        overrides.put(PlatformSettingKeyEnum.REGISTER_ENABLED.getCode(), "false");
        overrides.put(PlatformSettingKeyEnum.MAX_FAMILY_COUNT.getCode(), "   ");

        Map<String, String> effective = service.mergeWithDefaults(overrides);

        assertFalse(service.isEnabled(effective, PlatformSettingKeyEnum.REGISTER_ENABLED));
        assertEquals(Integer.parseInt(PlatformSettingKeyEnum.MAX_FAMILY_COUNT.getDefaultValue()),
                service.getInt(effective, PlatformSettingKeyEnum.MAX_FAMILY_COUNT));
    }

    @Test
    void familyScopedDefaultsKeepsOnlyFamilyKeys() {
        Map<String, String> effective = service.mergeWithDefaults(
                Map.of(PlatformSettingKeyEnum.MAX_FAMILY_COUNT.getCode(), "5"));

        Map<String, String> familyDefaults = service.familyScopedDefaults(effective);

        assertTrue(familyDefaults.containsKey(FamilySettingKeyEnum.MAX_NODE_COUNT.getCode()));
        assertFalse(familyDefaults.containsKey(PlatformSettingKeyEnum.MAX_FAMILY_COUNT.getCode()));
        assertFalse(familyDefaults.containsKey(PlatformSettingKeyEnum.INVITE_JOIN_ENABLED.getCode()));
        assertEquals(effective.get(FamilySettingKeyEnum.AI_FEATURE_ENABLED.getCode()),
                familyDefaults.get(FamilySettingKeyEnum.AI_FEATURE_ENABLED.getCode()));
    }

    @Test
    void familyScopedDefaultsOnMissingInputReturnsEmpty() {
        assertTrue(service.familyScopedDefaults(null).isEmpty());
    }

    @Test
    void toValidatedEntitiesNormalizesValues() {
        List<PlatformSetting> saved = service.toValidatedEntities(Map.of(
                PlatformSettingKeyEnum.MAX_FAMILY_COUNT.getCode(), " 300 ",
                PlatformSettingKeyEnum.INVITE_JOIN_ENABLED.getCode(), "FALSE"));

        Map<String, String> byKey = new LinkedHashMap<>(4);
        saved.forEach(item -> byKey.put(item.getSettingKey(), item.getSettingValue()));

        assertEquals("300", byKey.get(PlatformSettingKeyEnum.MAX_FAMILY_COUNT.getCode()));
        assertEquals("false", byKey.get(PlatformSettingKeyEnum.INVITE_JOIN_ENABLED.getCode()));
    }

    @Test
    void toValidatedEntitiesRejectsIllegalInput() {
        assertEquals("不支持的配置项：NOT_A_KEY",
                assertThrows(BusinessException.class, () -> service.toValidatedEntities(
                        Map.of("NOT_A_KEY", "true"))).getMessage());
        assertEquals("请至少提交一项配置",
                assertThrows(BusinessException.class, () -> service.toValidatedEntities(null)).getMessage());
        assertTrue(assertThrows(BusinessException.class, () -> service.toValidatedEntities(
                Map.of(PlatformSettingKeyEnum.MAX_FAMILY_COUNT.getCode(), "0"))).getMessage().contains("须在"));
    }

    @Test
    void toValidatedEntitiesCapsBatchSize() {
        int maxItems = PlatformSettingKeyEnum.listAll().size();
        Map<String, String> tooMany = new LinkedHashMap<>(maxItems * 2);
        for (PlatformSettingKeyEnum key : PlatformSettingKeyEnum.listAll()) {
            tooMany.put(key.getCode(), key.getDefaultValue());
        }
        tooMany.put("EXTRA_KEY", "true");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.toValidatedEntities(tooMany));

        assertEquals("单次最多提交" + maxItems + "项配置", ex.getMessage());
    }

    @Test
    void isDefaultValueComparesWithCodeDefault() {
        assertTrue(service.isDefaultValue(setting(PlatformSettingKeyEnum.REGISTER_ENABLED.getCode(), "true")));
        assertFalse(service.isDefaultValue(setting(PlatformSettingKeyEnum.REGISTER_ENABLED.getCode(), "false")));
        assertEquals("不支持的配置项：NOT_A_KEY",
                assertThrows(BusinessException.class, () -> service.isDefaultValue(
                        setting("NOT_A_KEY", "true"))).getMessage());
    }

    @Test
    void disabledMessageUsesLabelAndSuffix() {
        assertEquals("开放注册已被平台管理员关闭",
                service.disabledMessage(PlatformSettingKeyEnum.REGISTER_ENABLED));
    }

    @Test
    void getIntParsesOverrideAndTrimsWhitespace() {
        assertEquals(7, service.getInt(Map.of(PlatformSettingKeyEnum.MAX_FAMILY_COUNT.getCode(), " 7 "),
                PlatformSettingKeyEnum.MAX_FAMILY_COUNT));
        assertEquals(Integer.parseInt(PlatformSettingKeyEnum.MAX_FAMILY_COUNT.getDefaultValue()),
                service.getInt(Map.of(), PlatformSettingKeyEnum.MAX_FAMILY_COUNT));
    }
}
