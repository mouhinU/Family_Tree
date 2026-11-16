package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.PlatformSettingSaveDTO;
import com.mouhin.family.tree.common.dto.PlatformSettingVO;
import com.mouhin.family.tree.common.enums.FamilySettingKeyEnum;
import com.mouhin.family.tree.common.enums.PlatformSettingKeyEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.PlatformSetting;
import com.mouhin.family.tree.domain.event.OperationPerformedEvent;
import com.mouhin.family.tree.domain.repository.FamilyRepository;
import com.mouhin.family.tree.domain.repository.PlatformSettingRepository;
import com.mouhin.family.tree.domain.service.PlatformSettingDomainService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 平台级配置应用服务单元测试：覆盖配置视图拼装、覆盖值的新增 / 更新 / 恢复默认、
 * 审计事件文案，以及注册开关与家族总数上限的业务断言。
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@ExtendWith(MockitoExtension.class)
class PlatformSettingApplicationServiceTest {

    private static final Long OPERATOR_ID = 9L;

    private static final String OPERATOR_NAME = "pfadmin";

    private static final String OPERATOR_IP = "127.0.0.1";

    @Mock
    private PlatformSettingRepository platformSettingRepository;

    @Mock
    private FamilyRepository familyRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PlatformSettingApplicationService service() {
        return new PlatformSettingApplicationService(platformSettingRepository,
                new PlatformSettingDomainService(), familyRepository, eventPublisher);
    }

    private PlatformSetting existing(String key, String value) {
        PlatformSetting setting = new PlatformSetting();
        setting.setId(1L);
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        return setting;
    }

    private PlatformSettingSaveDTO dto(String key, String value) {
        PlatformSettingSaveDTO dto = new PlatformSettingSaveDTO();
        dto.setSettings(new LinkedHashMap<>(Map.of(key, value)));
        return dto;
    }

    @Test
    void listExposesSchemaWithEffectiveValuesAndFlags() {
        when(platformSettingRepository.findAll()).thenReturn(
                List.of(existing(PlatformSettingKeyEnum.MAX_FAMILY_COUNT.getCode(), "5")));

        List<PlatformSettingVO> voList = service().listPlatformSettings();

        assertEquals(PlatformSettingKeyEnum.listAll().size(), voList.size());
        PlatformSettingVO quota = voList.stream()
                .filter(item -> PlatformSettingKeyEnum.MAX_FAMILY_COUNT.getCode().equals(item.getKey()))
                .findFirst().orElseThrow();
        assertEquals("5", quota.getValue());
        assertEquals(PlatformSettingKeyEnum.MAX_FAMILY_COUNT.getDefaultValue(), quota.getDefaultValue());
        assertTrue(quota.getOverridden());
        assertFalse(quota.getFamilyScoped());
        PlatformSettingVO aiDefault = voList.stream()
                .filter(item -> FamilySettingKeyEnum.AI_FEATURE_ENABLED.getCode().equals(item.getKey()))
                .findFirst().orElseThrow();
        assertTrue(aiDefault.getFamilyScoped());
        assertFalse(aiDefault.getOverridden());
    }

    @Test
    void saveInsertsWhenNoOverrideExists() {
        when(platformSettingRepository.findByKey(PlatformSettingKeyEnum.REGISTER_ENABLED.getCode()))
                .thenReturn(null);

        service().savePlatformSettings(dto(PlatformSettingKeyEnum.REGISTER_ENABLED.getCode(), "false"),
                OPERATOR_ID, OPERATOR_NAME, OPERATOR_IP);

        ArgumentCaptor<PlatformSetting> captor = ArgumentCaptor.forClass(PlatformSetting.class);
        verify(platformSettingRepository).save(captor.capture());
        assertEquals("false", captor.getValue().getSettingValue());
        verify(platformSettingRepository, never()).update(any());
        verify(platformSettingRepository, never()).removeByKey(any());
        assertAuditMessage("更新平台全局配置1项（覆盖1项，恢复默认0项）");
    }

    @Test
    void saveUpdatesWhenOverrideExists() {
        when(platformSettingRepository.findByKey(PlatformSettingKeyEnum.MAX_FAMILY_COUNT.getCode()))
                .thenReturn(existing(PlatformSettingKeyEnum.MAX_FAMILY_COUNT.getCode(), "300"));

        service().savePlatformSettings(dto(PlatformSettingKeyEnum.MAX_FAMILY_COUNT.getCode(), "800"),
                OPERATOR_ID, OPERATOR_NAME, OPERATOR_IP);

        ArgumentCaptor<PlatformSetting> captor = ArgumentCaptor.forClass(PlatformSetting.class);
        verify(platformSettingRepository).update(captor.capture());
        assertEquals("800", captor.getValue().getSettingValue());
        verify(platformSettingRepository, never()).save(any());
    }

    @Test
    void saveWithDefaultValueRemovesOverrideInsteadOfWritingIt() {
        String key = PlatformSettingKeyEnum.INVITE_JOIN_ENABLED.getCode();
        when(platformSettingRepository.findByKey(key)).thenReturn(existing(key, "false"));

        service().savePlatformSettings(dto(key, "true"), OPERATOR_ID, OPERATOR_NAME, OPERATOR_IP);

        verify(platformSettingRepository).removeByKey(key);
        verify(platformSettingRepository, never()).save(any());
        verify(platformSettingRepository, never()).update(any());
        assertAuditMessage("更新平台全局配置1项（覆盖0项，恢复默认1项）");
    }

    @Test
    void saveWithDefaultValueWithoutOverrideWritesNothing() {
        String key = PlatformSettingKeyEnum.INVITE_JOIN_ENABLED.getCode();
        when(platformSettingRepository.findByKey(key)).thenReturn(null);

        service().savePlatformSettings(dto(key, "true"), OPERATOR_ID, OPERATOR_NAME, OPERATOR_IP);

        verify(platformSettingRepository, never()).save(any());
        verify(platformSettingRepository, never()).update(any());
        verify(platformSettingRepository, never()).removeByKey(any());
        assertAuditMessage("更新平台全局配置1项（覆盖0项，恢复默认0项）");
    }

    @Test
    void saveRejectsIllegalKeyBeforeAnyWrite() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service().savePlatformSettings(
                dto("NOT_A_KEY", "true"), OPERATOR_ID, OPERATOR_NAME, OPERATOR_IP));

        assertEquals("不支持的配置项：NOT_A_KEY", ex.getMessage());
        verify(platformSettingRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void assertEnabledRejectsClosedSwitchWithContactHint() {
        when(platformSettingRepository.findAll()).thenReturn(
                List.of(existing(PlatformSettingKeyEnum.REGISTER_ENABLED.getCode(), "false")));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().assertEnabled(PlatformSettingKeyEnum.REGISTER_ENABLED));

        assertEquals("开放注册已被平台管理员关闭，请联系平台管理员", ex.getMessage());
    }

    @Test
    void assertEnabledPassesByDefault() {
        when(platformSettingRepository.findAll()).thenReturn(List.of());

        service().assertEnabled(PlatformSettingKeyEnum.INVITE_JOIN_ENABLED);
    }

    @Test
    void familyQuotaRejectsWhenCountReachesPlatformLimit() {
        when(platformSettingRepository.findAll()).thenReturn(
                List.of(existing(PlatformSettingKeyEnum.MAX_FAMILY_COUNT.getCode(), "3")));
        when(familyRepository.countAll()).thenReturn(3L);

        BusinessException ex = assertThrows(BusinessException.class, service()::assertFamilyQuotaAvailable);

        assertTrue(ex.getMessage().contains("上限3"));
    }

    @Test
    void familyQuotaAllowsBelowPlatformLimit() {
        when(platformSettingRepository.findAll()).thenReturn(
                List.of(existing(PlatformSettingKeyEnum.MAX_FAMILY_COUNT.getCode(), "3")));
        when(familyRepository.countAll()).thenReturn(2L);

        service().assertFamilyQuotaAvailable();
    }

    @Test
    void familyScopedDefaultsExcludePlatformOnlyKeys() {
        when(platformSettingRepository.findAll()).thenReturn(
                List.of(existing(FamilySettingKeyEnum.MAX_NODE_COUNT.getCode(), "6000")));

        Map<String, String> familyDefaults = service().familyScopedDefaults();

        assertEquals("6000", familyDefaults.get(FamilySettingKeyEnum.MAX_NODE_COUNT.getCode()));
        assertFalse(familyDefaults.containsKey(PlatformSettingKeyEnum.REGISTER_ENABLED.getCode()));
    }

    private void assertAuditMessage(String expectedDesc) {
        ArgumentCaptor<OperationPerformedEvent> captor = ArgumentCaptor.forClass(OperationPerformedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        OperationPerformedEvent event = captor.getValue();
        assertEquals("PLATFORM_GLOBAL_SETTING_UPDATE", event.operationType());
        assertEquals(expectedDesc, event.operationDesc());
        assertEquals("platform_setting", event.targetType());
        assertEquals(OPERATOR_ID, event.userId());
        assertEquals(OPERATOR_NAME, event.username());
        assertEquals(OPERATOR_IP, event.ipAddress());
        assertNull(event.familyId());
    }
}
