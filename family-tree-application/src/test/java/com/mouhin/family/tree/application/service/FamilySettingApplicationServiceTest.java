package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.enums.FamilySettingKeyEnum;
import com.mouhin.family.tree.common.enums.PlatformSettingKeyEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilySetting;
import com.mouhin.family.tree.domain.entity.PlatformSetting;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.FamilyRepository;
import com.mouhin.family.tree.domain.repository.FamilySettingRepository;
import com.mouhin.family.tree.domain.repository.PlatformSettingRepository;
import com.mouhin.family.tree.domain.service.FamilySettingDomainService;
import com.mouhin.family.tree.domain.service.PlatformSettingDomainService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 家族级配置读取应用服务单元测试：覆盖无覆盖时按默认放行、开关关闭拒绝、
 * 节点配额达上限拦截与非法配额回落默认值，以及家族 → 平台 → 代码的三级生效链。
 *
 * @author Family-Tree
 * @date 2026-11-15
 */
@ExtendWith(MockitoExtension.class)
class FamilySettingApplicationServiceTest {

    private static final Long FAMILY_ID = 100L;

    @Mock
    private FamilySettingRepository familySettingRepository;

    @Mock
    private FamilyNodeRepository familyNodeRepository;

    @Mock
    private PlatformSettingRepository platformSettingRepository;

    private FamilySettingApplicationService newService() {
        return newService(List.of());
    }

    private FamilySettingApplicationService newService(List<PlatformSetting> platformOverrides) {
        when(platformSettingRepository.findAll()).thenReturn(platformOverrides);
        PlatformSettingApplicationService platformSettingService = new PlatformSettingApplicationService(
                platformSettingRepository, new PlatformSettingDomainService(),
                mock(FamilyRepository.class), mock(ApplicationEventPublisher.class));
        return new FamilySettingApplicationService(familySettingRepository,
                new FamilySettingDomainService(), familyNodeRepository, platformSettingService);
    }

    private FamilySetting override(String key, String value) {
        FamilySetting setting = new FamilySetting();
        setting.setFamilyId(FAMILY_ID);
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        return setting;
    }

    private PlatformSetting platformOverride(String key, String value) {
        PlatformSetting setting = new PlatformSetting();
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        return setting;
    }

    @Test
    void withoutOverridesEverySwitchFollowsItsDefault() {
        when(familySettingRepository.findByFamilyId(FAMILY_ID)).thenReturn(List.of());

        Map<String, String> effective = newService().effectiveSettings(FAMILY_ID);

        assertEquals(FamilySettingKeyEnum.AI_FEATURE_ENABLED.getDefaultValue(),
                effective.get(FamilySettingKeyEnum.AI_FEATURE_ENABLED.getCode()));
        assertEquals(FamilySettingKeyEnum.MAX_NODE_COUNT.getDefaultValue(),
                effective.get(FamilySettingKeyEnum.MAX_NODE_COUNT.getCode()));
    }

    @Test
    void enabledSwitchPassesAssertion() {
        when(familySettingRepository.findByFamilyId(FAMILY_ID)).thenReturn(List.of());

        newService().assertEnabled(FAMILY_ID, FamilySettingKeyEnum.DATA_EXPORT_ENABLED);
    }

    @Test
    void disabledSwitchIsRejectedWithItsLabel() {
        when(familySettingRepository.findByFamilyId(FAMILY_ID)).thenReturn(
                List.of(override(FamilySettingKeyEnum.DATA_EXPORT_ENABLED.getCode(), "false")));

        BusinessException ex = assertThrows(BusinessException.class, () -> newService()
                .assertEnabled(FAMILY_ID, FamilySettingKeyEnum.DATA_EXPORT_ENABLED));

        assertEquals("数据导出已被平台管理员关闭", ex.getMessage());
    }

    @Test
    void nodeQuotaAllowsCountBelowLimit() {
        when(familySettingRepository.findByFamilyId(FAMILY_ID)).thenReturn(
                List.of(override(FamilySettingKeyEnum.MAX_NODE_COUNT.getCode(), "10")));
        when(familyNodeRepository.countByFamilyId(FAMILY_ID)).thenReturn(9L);

        newService().assertNodeQuotaAvailable(FAMILY_ID);
    }

    @Test
    void nodeQuotaRejectsCountAtOrAboveLimit() {
        when(familySettingRepository.findByFamilyId(FAMILY_ID)).thenReturn(
                List.of(override(FamilySettingKeyEnum.MAX_NODE_COUNT.getCode(), "10")));
        when(familyNodeRepository.countByFamilyId(FAMILY_ID)).thenReturn(10L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> newService().assertNodeQuotaAvailable(FAMILY_ID));

        assertTrue(ex.getMessage().contains("上限10"));
    }

    @Test
    void malformedQuotaFallsBackToDefaultLimit() {
        when(familySettingRepository.findByFamilyId(FAMILY_ID)).thenReturn(
                List.of(override(FamilySettingKeyEnum.MAX_NODE_COUNT.getCode(), "不限")));
        when(familyNodeRepository.countByFamilyId(FAMILY_ID)).thenReturn(5000L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> newService().assertNodeQuotaAvailable(FAMILY_ID));

        assertTrue(ex.getMessage().contains("上限" + FamilySettingKeyEnum.MAX_NODE_COUNT.getDefaultValue()));
    }

    @Test
    void platformOverrideFormsTheFamilyDefaultLayer() {
        when(familySettingRepository.findByFamilyId(FAMILY_ID)).thenReturn(List.of());

        Map<String, String> effective = newService(
                List.of(platformOverride(FamilySettingKeyEnum.MAX_NODE_COUNT.getCode(), "20")))
                .effectiveSettings(FAMILY_ID);

        assertEquals("20", effective.get(FamilySettingKeyEnum.MAX_NODE_COUNT.getCode()));
    }

    @Test
    void familyOverrideWinsOverPlatformOverride() {
        when(familySettingRepository.findByFamilyId(FAMILY_ID)).thenReturn(
                List.of(override(FamilySettingKeyEnum.DATA_EXPORT_ENABLED.getCode(), "true")));

        Map<String, String> effective = newService(
                List.of(platformOverride(FamilySettingKeyEnum.DATA_EXPORT_ENABLED.getCode(), "false")))
                .effectiveSettings(FAMILY_ID);

        assertEquals("true", effective.get(FamilySettingKeyEnum.DATA_EXPORT_ENABLED.getCode()));
    }

    @Test
    void platformOnlyKeysNeverLeakIntoFamilyLayer() {
        when(familySettingRepository.findByFamilyId(FAMILY_ID)).thenReturn(List.of());

        Map<String, String> effective = newService(
                List.of(platformOverride(PlatformSettingKeyEnum.MAX_FAMILY_COUNT.getCode(), "3")))
                .effectiveSettings(FAMILY_ID);

        assertEquals(FamilySettingKeyEnum.MAX_NODE_COUNT.getDefaultValue(),
                effective.get(FamilySettingKeyEnum.MAX_NODE_COUNT.getCode()));
        assertTrue(effective.keySet().stream().noneMatch(PlatformSettingKeyEnum.REGISTER_ENABLED.getCode()::equals));
    }
}
