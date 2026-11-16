package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.FamilyAdminDetailVO;
import com.mouhin.family.tree.common.dto.FamilyAdminRowVO;
import com.mouhin.family.tree.common.dto.FamilySettingSaveDTO;
import com.mouhin.family.tree.common.dto.FamilySettingVO;
import com.mouhin.family.tree.common.dto.OperationLogDTO;
import com.mouhin.family.tree.common.dto.PageResult;
import com.mouhin.family.tree.common.dto.PlatformOperationLogVO;
import com.mouhin.family.tree.common.dto.PlatformOverviewVO;
import com.mouhin.family.tree.common.dto.UserAdminDetailVO;
import com.mouhin.family.tree.common.dto.UserAdminFamilyVO;
import com.mouhin.family.tree.common.dto.UserAdminRowVO;
import com.mouhin.family.tree.common.dto.UserPasswordResetVO;
import com.mouhin.family.tree.common.enums.FamilySettingKeyEnum;
import com.mouhin.family.tree.common.enums.UserAccountTypeEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.Family;
import com.mouhin.family.tree.domain.entity.FamilyMember;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyUserNode;
import com.mouhin.family.tree.domain.entity.OperationLog;
import com.mouhin.family.tree.domain.entity.FamilySetting;
import com.mouhin.family.tree.domain.entity.User;
import com.mouhin.family.tree.domain.event.MemberRoleChangedEvent;
import com.mouhin.family.tree.domain.event.OperationPerformedEvent;
import com.mouhin.family.tree.domain.repository.FamilyMemberRepository;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.FamilyPhotoRepository;
import com.mouhin.family.tree.domain.repository.FamilyRelationRepository;
import com.mouhin.family.tree.domain.repository.FamilyRepository;
import com.mouhin.family.tree.domain.repository.FamilySettingRepository;
import com.mouhin.family.tree.domain.repository.FamilyUserNodeRepository;
import com.mouhin.family.tree.domain.repository.OperationLogRepository;
import com.mouhin.family.tree.domain.repository.PlatformSettingRepository;
import com.mouhin.family.tree.domain.repository.SnapshotRepository;
import com.mouhin.family.tree.domain.repository.UserRepository;
import com.mouhin.family.tree.domain.service.FamilySettingDomainService;
import com.mouhin.family.tree.domain.service.PlatformSettingDomainService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 平台管理员应用服务单元测试：覆盖白名单裁决、总览统计、家族分页与分组计数、
 * 详情拼装（含联系方式脱敏与配置覆盖标记）以及配置写入的插入 / 更新与失败关闭。
 *
 * @author Family-Tree
 * @date 2026-11-15
 */
@ExtendWith(MockitoExtension.class)
class PlatformAdminApplicationServiceTest {

    private static final Long FAMILY_ID = 100L;
    private static final Long CREATOR_ID = 9L;
    private static final Long ADMIN_USER_ID = 1L;
    private static final Long USER_ID = 7L;
    private static final Long SECOND_FAMILY_ID = 200L;
    private static final Long NODE_ID = 55L;
    private static final String RAW_EMAIL = "meng@example.com";
    private static final String MASKED_EMAIL = "m****@example.com";
    private static final int MAX_PAGE_SIZE = 100;
    private static final String RAW_PHONE = "15926330615";
    private static final String MASKED_PHONE = "159****0615";
    private static final String ONE_TIME_PASSWORD = "kq7zmb3twp";

    @Mock
    private FamilyRepository familyRepository;

    @Mock
    private FamilyNodeRepository familyNodeRepository;

    @Mock
    private FamilyRelationRepository familyRelationRepository;

    @Mock
    private FamilyMemberRepository familyMemberRepository;

    @Mock
    private FamilyPhotoRepository familyPhotoRepository;

    @Mock
    private FamilySettingRepository familySettingRepository;

    @Mock
    private FamilyUserNodeRepository familyUserNodeRepository;

    @Mock
    private SnapshotRepository snapshotRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OperationLogRepository operationLogRepository;

    @Mock
    private PlatformSettingRepository platformSettingRepository;

    @Mock
    private OperationLogApplicationService operationLogService;

    @Mock
    private UserAuthApplicationService userAuthApplicationService;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PlatformAdminApplicationService newService(String adminUsernames) {
        PlatformSettingApplicationService platformSettingService = new PlatformSettingApplicationService(
                platformSettingRepository, new PlatformSettingDomainService(), familyRepository, eventPublisher);
        return new PlatformAdminApplicationService(familyRepository, familyNodeRepository,
                familyRelationRepository, familyMemberRepository, familyUserNodeRepository,
                familyPhotoRepository,
                familySettingRepository, snapshotRepository, userRepository, operationLogRepository,
                platformSettingService,
                new FamilySettingDomainService(), operationLogService, userAuthApplicationService,
                loginAttemptService, eventPublisher, adminUsernames);
    }

    private Family family() {
        Family family = new Family();
        family.setId(FAMILY_ID);
        family.setName("蒙氏家族");
        family.setHallName("衍泽堂");
        family.setAncestralHome("浙江杭州");
        family.setInviteCode("ABCD1234");
        family.setCreatorId(CREATOR_ID);
        family.setGenerationCols(5);
        family.setGenerationRows(5);
        family.setCreateTime(LocalDateTime.of(2026, 8, 1, 10, 0));
        family.setUpdateTime(LocalDateTime.of(2026, 11, 15, 10, 0));
        return family;
    }

    private User creator() {
        User user = new User();
        user.setId(CREATOR_ID);
        user.setUsername("mouhin");
        user.setNickname("蒙斌");
        user.setPhone(RAW_PHONE);
        return user;
    }

    private User otherUser() {
        User user = new User();
        user.setId(2L);
        user.setUsername("other");
        user.setNickname("外人");
        return user;
    }

    // ========== 白名单裁决 ==========

    @Test
    void blankWhitelistDeniesEveryoneWithoutLookup() {
        PlatformAdminApplicationService service = newService("   ");

        assertFalse(service.isPlatformAdmin(ADMIN_USER_ID));
        verify(userRepository, never()).findById(ADMIN_USER_ID);
    }

    @Test
    void nullUserIsDeniedWithoutLookup() {
        PlatformAdminApplicationService service = newService("mouhin");

        assertFalse(service.isPlatformAdmin(null));
        verify(userRepository, never()).findById(any());
    }

    @Test
    void whitelistTrimsEntriesAndMatchesUsernameExactly() {
        PlatformAdminApplicationService service = newService(" mouhin , ,ROOT");
        when(userRepository.findById(ADMIN_USER_ID)).thenReturn(creator());
        when(userRepository.findById(2L)).thenReturn(otherUser());

        assertTrue(service.isPlatformAdmin(ADMIN_USER_ID));
        assertFalse(service.isPlatformAdmin(2L));
    }

    @Test
    void missingUserIsDenied() {
        PlatformAdminApplicationService service = newService("mouhin");
        when(userRepository.findById(ADMIN_USER_ID)).thenReturn(null);

        assertFalse(service.isPlatformAdmin(ADMIN_USER_ID));
    }

    // ========== 总览 ==========

    @Test
    void overviewAggregatesPlatformCounters() {
        PlatformAdminApplicationService service = newService("mouhin");
        when(familyRepository.countAll()).thenReturn(12L);
        when(userRepository.countAll()).thenReturn(88L);
        when(userRepository.countAutoAccounts()).thenReturn(50L);
        when(familyNodeRepository.countAll()).thenReturn(600L);
        when(familyRelationRepository.countAll()).thenReturn(900L);
        when(familyRepository.countCreatedAfter(any())).thenReturn(3L);

        PlatformOverviewVO vo = service.getOverview();

        assertEquals(12L, vo.getFamilyCount().longValue());
        assertEquals(88L, vo.getUserCount().longValue());
        assertEquals(50L, vo.getAutoAccountCount().longValue());
        assertEquals(600L, vo.getNodeCount().longValue());
        assertEquals(900L, vo.getRelationCount().longValue());
        assertEquals(3L, vo.getRecentFamilyCount().longValue());
        assertEquals(30, vo.getRecentWindowDays().intValue());
    }

    // ========== 家族分页 ==========

    @Test
    void emptySearchSkipsRowAssembly() {
        PlatformAdminApplicationService service = newService("mouhin");
        when(familyRepository.countByKeyword("无名")).thenReturn(0L);

        PageResult<FamilyAdminRowVO> page = service.listFamilies("无名", 1, 20);

        assertEquals(0L, page.getTotal());
        assertTrue(page.getRecords().isEmpty());
        verify(familyRepository, never()).listPaged(any(), anyInt(), anyInt());
    }

    @Test
    void oversizedPageSizeIsCappedAndOffsetIsZeroBased() {
        PlatformAdminApplicationService service = newService("mouhin");
        when(familyRepository.countByKeyword(null)).thenReturn(5L);
        when(familyRepository.listPaged(null, MAX_PAGE_SIZE, MAX_PAGE_SIZE)).thenReturn(List.of());

        PageResult<FamilyAdminRowVO> page = service.listFamilies(null, 2, 5000);

        assertEquals(2, page.getPage());
        assertEquals(MAX_PAGE_SIZE, page.getSize());
        verify(familyRepository).listPaged(null, MAX_PAGE_SIZE, MAX_PAGE_SIZE);
    }

    @Test
    void rowsUseGroupedCountsAndMaskedCreatorContact() {
        PlatformAdminApplicationService service = newService("mouhin");
        stubPageRowCounts();
        when(userRepository.findByIds(anyList())).thenReturn(List.of(creator()));

        PageResult<FamilyAdminRowVO> page = service.listFamilies("蒙", 1, 20);

        FamilyAdminRowVO row = page.getRecords().get(0);
        assertEquals(FAMILY_ID, row.getId());
        assertEquals("衍泽堂", row.getHallName());
        assertEquals("蒙斌", row.getCreatorNickname());
        assertEquals(MASKED_PHONE, row.getCreatorPhone());
        assertEquals(3, row.getMemberCount().intValue());
        assertEquals(12, row.getNodeCount().intValue());
        assertEquals(4, row.getDeceasedNodeCount().intValue());
        assertEquals(20, row.getRelationCount().intValue());
        // 历史家族未落状态列时回落 NORMAL，治理标记不得反向打断正常读写
        assertEquals("NORMAL", row.getStatus());
        assertEquals("正常", row.getStatusLabel());
    }

    private void stubPageRowCounts() {
        when(familyRepository.countByKeyword("蒙")).thenReturn(1L);
        when(familyRepository.listPaged("蒙", 0, 20)).thenReturn(List.of(family()));
        when(familyMemberRepository.countGroupedByFamilyIds(anyList()))
                .thenReturn(Map.of(FAMILY_ID, 3L));
        when(familyNodeRepository.countGroupedByFamilyIds(anyList()))
                .thenReturn(Map.of(FAMILY_ID, 12L));
        when(familyNodeRepository.countDeceasedGroupedByFamilyIds(anyList()))
                .thenReturn(Map.of(FAMILY_ID, 4L));
        when(familyRelationRepository.countGroupedByFamilyIds(anyList()))
                .thenReturn(Map.of(FAMILY_ID, 20L));
    }

    // ========== 家族详情 ==========

    @Test
    void detailMergesCountsRecentOperationsAndSettingOverrides() {
        PlatformAdminApplicationService service = newService("mouhin");
        when(familyRepository.findById(FAMILY_ID)).thenReturn(family());
        when(userRepository.findByIds(anyList())).thenReturn(List.of(creator()));
        when(familyMemberRepository.countGroupedByFamilyIds(anyList())).thenReturn(Map.of(FAMILY_ID, 2L));
        when(familyNodeRepository.countGroupedByFamilyIds(anyList())).thenReturn(Map.of(FAMILY_ID, 8L));
        when(familyNodeRepository.countDeceasedGroupedByFamilyIds(anyList())).thenReturn(Map.of(FAMILY_ID, 1L));
        when(familyRelationRepository.countGroupedByFamilyIds(anyList())).thenReturn(Map.of(FAMILY_ID, 15L));
        when(snapshotRepository.countByFamilyId(FAMILY_ID)).thenReturn(6L);
        when(familyPhotoRepository.countByFamilyId(FAMILY_ID)).thenReturn(11L);
        when(operationLogService.listLogs(FAMILY_ID, null, 1, 10))
                .thenReturn(new PageResult<>(List.of(operationLog()), 1L, 1, 10));
        when(familySettingRepository.findByFamilyId(FAMILY_ID))
                .thenReturn(List.of(setting(FamilySettingKeyEnum.AI_FEATURE_ENABLED.getCode(), "false")));

        FamilyAdminDetailVO detail = service.getFamilyDetail(FAMILY_ID);

        assertEquals(MASKED_PHONE, detail.getFamily().getCreatorPhone());
        assertEquals(6, detail.getSnapshotCount().intValue());
        assertEquals(11, detail.getPhotoCount().intValue());
        assertEquals(1, detail.getRecentOperations().size());
        assertEquals(4, detail.getSettings().size());
        FamilySettingVO ai = findSetting(detail.getSettings(), FamilySettingKeyEnum.AI_FEATURE_ENABLED.getCode());
        assertEquals("false", ai.getValue());
        assertTrue(ai.getOverridden());
        FamilySettingVO quota = findSetting(detail.getSettings(), FamilySettingKeyEnum.MAX_NODE_COUNT.getCode());
        assertEquals(FamilySettingKeyEnum.MAX_NODE_COUNT.getDefaultValue(), quota.getValue());
        assertFalse(quota.getOverridden());
    }

    private OperationLogDTO operationLog() {
        OperationLogDTO log = new OperationLogDTO();
        log.setId(1L);
        log.setUsername("蒙斌");
        log.setOperationType("NODE_CREATE");
        log.setOperationDesc("新增节点");
        log.setCreateTime(LocalDateTime.of(2026, 11, 14, 9, 30));
        return log;
    }

    private FamilySettingVO findSetting(List<FamilySettingVO> settings, String key) {
        return settings.stream().filter(item -> key.equals(item.getKey())).findFirst().orElseThrow();
    }

    @Test
    void unknownFamilyIsRejected() {
        PlatformAdminApplicationService service = newService("mouhin");
        when(familyRepository.findById(FAMILY_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getFamilyDetail(FAMILY_ID));

        assertEquals("家族不存在", ex.getMessage());
    }

    @Test
    void missingFamilyIdIsRejected() {
        PlatformAdminApplicationService service = newService("mouhin");

        assertEquals("缺少家族ID", assertThrows(BusinessException.class,
                () -> service.getFamilyDetail(null)).getMessage());
    }

    // ========== 配置写入 ==========

    @Test
    void saveInsertsMissingKeysAndUpdatesExistingOnes() {
        PlatformAdminApplicationService service = newService("mouhin");
        when(familyRepository.findById(FAMILY_ID)).thenReturn(family());
        FamilySetting existing = setting(FamilySettingKeyEnum.AI_FEATURE_ENABLED.getCode(), "true");
        when(familySettingRepository.findByFamilyIdAndKey(FAMILY_ID,
                FamilySettingKeyEnum.AI_FEATURE_ENABLED.getCode())).thenReturn(existing);
        when(familySettingRepository.findByFamilyIdAndKey(FAMILY_ID,
                FamilySettingKeyEnum.MAX_NODE_COUNT.getCode())).thenReturn(null);

        service.saveSettings(FAMILY_ID, saveDto(Map.of(
                FamilySettingKeyEnum.AI_FEATURE_ENABLED.getCode(), "false",
                FamilySettingKeyEnum.MAX_NODE_COUNT.getCode(), "8000")), ADMIN_USER_ID, "蒙斌", "127.0.0.1");

        assertEquals("false", existing.getSettingValue());
        verify(familySettingRepository).update(existing);
        ArgumentCaptor<FamilySetting> saved = ArgumentCaptor.forClass(FamilySetting.class);
        verify(familySettingRepository).save(saved.capture());
        assertEquals(FamilySettingKeyEnum.MAX_NODE_COUNT.getCode(), saved.getValue().getSettingKey());
        assertEquals("8000", saved.getValue().getSettingValue());
        assertEquals(FAMILY_ID, saved.getValue().getFamilyId());

        ArgumentCaptor<OperationPerformedEvent> event = ArgumentCaptor.forClass(OperationPerformedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals("PLATFORM_SETTING_UPDATE", event.getValue().operationType());
        assertTrue(event.getValue().operationDesc().contains("2"));
        assertEquals(FAMILY_ID, event.getValue().familyId());
        assertEquals("127.0.0.1", event.getValue().ipAddress());
    }

    private FamilySettingSaveDTO saveDto(Map<String, String> settings) {
        FamilySettingSaveDTO dto = new FamilySettingSaveDTO();
        dto.setSettings(new LinkedHashMap<>(settings));
        return dto;
    }

    @Test
    void invalidSettingIsRejectedBeforeAnyWrite() {
        PlatformAdminApplicationService service = newService("mouhin");
        when(familyRepository.findById(FAMILY_ID)).thenReturn(family());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.saveSettings(FAMILY_ID,
                saveDto(Map.of(FamilySettingKeyEnum.MAX_NODE_COUNT.getCode(), "9000000")),
                ADMIN_USER_ID, "蒙斌", "127.0.0.1"));

        assertTrue(ex.getMessage().contains("须在"));
        verify(familySettingRepository, never()).save(any());
        verify(familySettingRepository, never()).update(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void emptySettingSubmissionIsRejected() {
        PlatformAdminApplicationService service = newService("mouhin");
        when(familyRepository.findById(FAMILY_ID)).thenReturn(family());

        assertEquals("请至少提交一项配置", assertThrows(BusinessException.class,
                () -> service.saveSettings(FAMILY_ID, new FamilySettingSaveDTO(),
                        ADMIN_USER_ID, "蒙斌", "127.0.0.1")).getMessage());
    }

    @Test
    void settingsListingTreatsUnknownFamilyAsError() {
        PlatformAdminApplicationService service = newService("mouhin");
        when(familyRepository.findById(FAMILY_ID)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.listSettings(FAMILY_ID));
        verify(familySettingRepository, never()).findByFamilyId(FAMILY_ID);
    }

    @Test
    void settingsListingKeepsBlankOverrideButShowsDefaultValue() {
        PlatformAdminApplicationService service = newService("mouhin");
        when(familyRepository.findById(FAMILY_ID)).thenReturn(family());
        when(familySettingRepository.findByFamilyId(FAMILY_ID))
                .thenReturn(List.of(setting(FamilySettingKeyEnum.DATA_EXPORT_ENABLED.getCode(), " ")));

        List<FamilySettingVO> settings = service.listSettings(FAMILY_ID);

        FamilySettingVO export = findSetting(settings, FamilySettingKeyEnum.DATA_EXPORT_ENABLED.getCode());
        assertEquals(FamilySettingKeyEnum.DATA_EXPORT_ENABLED.getDefaultValue(), export.getValue());
        assertTrue(export.getOverridden());
        assertNull(findSetting(settings, FamilySettingKeyEnum.GEDCOM_IMPORT_ENABLED.getCode()).getMinValue());
    }

    // ========== 用户分页 ==========

    @Test
    void accountTypeSelectsAutoAccountFilter() {
        PlatformAdminApplicationService service = newService("mouhin");
        when(userRepository.countByFilter(null, null)).thenReturn(0L);
        when(userRepository.countByFilter(null, Boolean.TRUE)).thenReturn(0L);
        when(userRepository.countByFilter(null, Boolean.FALSE)).thenReturn(0L);

        service.listUsers(null, UserAccountTypeEnum.ALL, 1, 20);
        service.listUsers(null, UserAccountTypeEnum.AUTO, 1, 20);
        service.listUsers(null, UserAccountTypeEnum.REAL, 1, 20);

        verify(userRepository).countByFilter(null, null);
        verify(userRepository).countByFilter(null, Boolean.TRUE);
        verify(userRepository).countByFilter(null, Boolean.FALSE);
    }

    @Test
    void emptyUserSearchSkipsRowAssembly() {
        PlatformAdminApplicationService service = newService("mouhin");
        when(userRepository.countByFilter("无名", Boolean.TRUE)).thenReturn(0L);

        PageResult<UserAdminRowVO> page = service.listUsers("无名", UserAccountTypeEnum.AUTO, 1, 20);

        assertEquals(0L, page.getTotal());
        assertTrue(page.getRecords().isEmpty());
        verify(userRepository, never()).listPaged(any(), any(), anyInt(), anyInt());
    }

    @Test
    void userRowsMaskContactsAndAttachCurrentFamilyName() {
        PlatformAdminApplicationService service = newService("mouhin");
        when(userRepository.countByFilter("蒙", null)).thenReturn(1L);
        when(userRepository.listPaged("蒙", null, 0, 20)).thenReturn(List.of(platformUser()));
        when(familyMemberRepository.countGroupedByUserIds(anyList())).thenReturn(Map.of(USER_ID, 2L));
        when(familyRepository.findByIds(anyList())).thenReturn(List.of(family()));

        PageResult<UserAdminRowVO> page = service.listUsers("蒙", UserAccountTypeEnum.ALL, 1, 20);

        UserAdminRowVO row = page.getRecords().get(0);
        assertEquals(USER_ID, row.getId());
        assertEquals("蒙子羽", row.getNickname());
        assertEquals(MASKED_PHONE, row.getPhone());
        assertEquals(MASKED_EMAIL, row.getEmail());
        assertTrue(row.getAutoAccount());
        assertFalse(row.getMustChangePassword());
        assertEquals(2, row.getFamilyCount().intValue());
        assertEquals("蒙氏家族", row.getCurrentFamilyName());
    }

    @Test
    void userWithoutCurrentFamilyKeepsFamilyNameEmpty() {
        PlatformAdminApplicationService service = newService("mouhin");
        User lone = platformUser();
        lone.setCurrentFamilyId(null);
        when(userRepository.countByFilter(null, Boolean.FALSE)).thenReturn(1L);
        when(userRepository.listPaged(null, Boolean.FALSE, 0, 20)).thenReturn(List.of(lone));
        when(familyMemberRepository.countGroupedByUserIds(anyList())).thenReturn(Map.of());

        UserAdminRowVO row = service.listUsers(null, UserAccountTypeEnum.REAL, 1, 20).getRecords().get(0);

        assertEquals(0, row.getFamilyCount().intValue());
        assertNull(row.getCurrentFamilyName());
        verify(familyRepository, never()).findByIds(anyList());
    }

    // ========== 用户详情 ==========

    @Test
    void missingUserIdIsRejected() {
        PlatformAdminApplicationService service = newService("mouhin");

        assertEquals("缺少用户ID", assertThrows(BusinessException.class,
                () -> service.getUserDetail(null)).getMessage());
        verify(userRepository, never()).findById(any());
    }

    @Test
    void unknownUserDetailIsRejected() {
        PlatformAdminApplicationService service = newService("mouhin");
        when(userRepository.findById(USER_ID)).thenReturn(null);

        assertEquals("用户不存在", assertThrows(BusinessException.class,
                () -> service.getUserDetail(USER_ID)).getMessage());
    }

    @Test
    void userDetailMarksCurrentFamilyAndBoundNodeInJoinOrder() {
        PlatformAdminApplicationService service = newService("mouhin");
        when(userRepository.findById(USER_ID)).thenReturn(platformUser());
        when(familyMemberRepository.countGroupedByUserIds(anyList())).thenReturn(Map.of(USER_ID, 2L));
        when(familyRepository.findByIds(anyList())).thenReturn(List.of(family(), secondFamily()));
        when(familyMemberRepository.findByUserId(USER_ID)).thenReturn(List.of(
                member(SECOND_FAMILY_ID, "MEMBER", LocalDateTime.of(2026, 10, 1, 9, 0)),
                member(FAMILY_ID, "OWNER", LocalDateTime.of(2026, 8, 1, 9, 0))));
        when(familyUserNodeRepository.findByFamilyIdAndUserId(FAMILY_ID, USER_ID))
                .thenReturn(binding(NODE_ID));
        when(familyUserNodeRepository.findByFamilyIdAndUserId(SECOND_FAMILY_ID, USER_ID)).thenReturn(null);
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node());

        UserAdminDetailVO detail = service.getUserDetail(USER_ID);

        assertEquals(MASKED_PHONE, detail.getUser().getPhone());
        List<UserAdminFamilyVO> families = detail.getFamilies();
        assertEquals(2, families.size());
        UserAdminFamilyVO first = families.get(0);
        assertEquals(FAMILY_ID, first.getFamilyId());
        assertEquals("族长", first.getRoleLabel());
        assertTrue(first.getCurrent());
        assertEquals(NODE_ID, first.getBoundNodeId());
        assertEquals("蒙爷爷", first.getBoundNodeName());
        UserAdminFamilyVO second = families.get(1);
        assertEquals("旁支陈氏", second.getFamilyName());
        assertEquals("成员", second.getRoleLabel());
        assertFalse(second.getCurrent());
        assertNull(second.getBoundNodeId());
    }

    @Test
    void platformResetPasswordReturnsOneTimePasswordAndAudits() {
        PlatformAdminApplicationService service = newService("mouhin");
        when(userRepository.findById(USER_ID)).thenReturn(platformUser());
        when(userAuthApplicationService.resetPasswordByPlatform(USER_ID)).thenReturn(ONE_TIME_PASSWORD);

        UserPasswordResetVO result = service.resetPassword(USER_ID, ADMIN_USER_ID, "mouhin", "127.0.0.1");

        assertEquals(USER_ID, result.getUserId());
        assertEquals("mengziyu", result.getUsername());
        assertEquals(ONE_TIME_PASSWORD, result.getNewPassword());
        assertTrue(result.getMustChangePassword());
        // 一次性口令不得进入日志：VO 的 toString 必须略去该字段
        assertFalse(result.toString().contains(ONE_TIME_PASSWORD));

        ArgumentCaptor<OperationPerformedEvent> event = ArgumentCaptor.forClass(OperationPerformedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals("PLATFORM_PASSWORD_RESET", event.getValue().operationType());
        assertEquals("mouhin", event.getValue().username());
        assertEquals(USER_ID, event.getValue().targetId());
        assertEquals(FAMILY_ID, event.getValue().familyId());
    }

    @Test
    void resetPasswordOfUnknownUserIsRejectedWithoutDelegation() {
        PlatformAdminApplicationService service = newService("mouhin");
        when(userRepository.findById(USER_ID)).thenReturn(null);

        assertEquals("用户不存在", assertThrows(BusinessException.class,
                () -> service.resetPassword(USER_ID, ADMIN_USER_ID, "mouhin", "127.0.0.1")).getMessage());
        verify(userAuthApplicationService, never()).resetPasswordByPlatform(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void unlockClearsLoginAttemptsOfAccountOwner() {
        PlatformAdminApplicationService service = newService("mouhin");
        when(userRepository.findById(USER_ID)).thenReturn(platformUser());

        service.unlockUser(USER_ID, ADMIN_USER_ID, "mouhin", "127.0.0.1");

        verify(loginAttemptService).clearAttempts("mengziyu");
        ArgumentCaptor<OperationPerformedEvent> event = ArgumentCaptor.forClass(OperationPerformedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals("PLATFORM_ACCOUNT_UNLOCK", event.getValue().operationType());
        assertEquals(ADMIN_USER_ID, event.getValue().userId());
        assertEquals("mouhin", event.getValue().username());
        assertEquals(USER_ID, event.getValue().targetId());
        assertTrue(event.getValue().operationDesc().contains("mengziyu"), "审计描述应点名被解锁账号");
    }

    @Test
    void unlockWithoutUserIdIsRejected() {
        PlatformAdminApplicationService service = newService("mouhin");

        assertEquals("缺少用户ID", assertThrows(BusinessException.class,
                () -> service.unlockUser(null, ADMIN_USER_ID, "mouhin", "127.0.0.1")).getMessage());
        verify(loginAttemptService, never()).clearAttempts(any());
    }

    // ========== 家族治理 ==========

    @Test
    void renameTrimsNameAndAuditsBothNames() {
        PlatformAdminApplicationService service = newService("mouhin");
        when(familyRepository.findById(FAMILY_ID)).thenReturn(family());

        FamilyAdminRowVO row = service.renameFamily(FAMILY_ID, "  蒙氏衍泽堂  ", ADMIN_USER_ID, "mouhin", "127.0.0.1");

        assertEquals("蒙氏衍泽堂", row.getName());
        verify(familyRepository).update(any(Family.class));
        ArgumentCaptor<OperationPerformedEvent> event = ArgumentCaptor.forClass(OperationPerformedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals("PLATFORM_FAMILY_RENAME", event.getValue().operationType());
        assertEquals("重命名家族：蒙氏家族 -> 蒙氏衍泽堂", event.getValue().operationDesc());
        assertEquals(FAMILY_ID, event.getValue().targetId());
    }

    @Test
    void blankRenameIsRejectedBeforeAnyWrite() {
        PlatformAdminApplicationService service = newService("mouhin");
        when(familyRepository.findById(FAMILY_ID)).thenReturn(family());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.renameFamily(FAMILY_ID, "   ", ADMIN_USER_ID, "mouhin", "127.0.0.1"));

        assertEquals("家族名称不能为空", ex.getMessage());
        verify(familyRepository, never()).update(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void oversizedRenameIsRejectedBeforeAnyWrite() {
        PlatformAdminApplicationService service = newService("mouhin");
        when(familyRepository.findById(FAMILY_ID)).thenReturn(family());
        String tooLong = "蒙".repeat(FamilyTreeConsts.MAX_FAMILY_NAME_LENGTH + 1);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.renameFamily(FAMILY_ID, tooLong, ADMIN_USER_ID, "mouhin", "127.0.0.1"));

        assertTrue(ex.getMessage().contains("个字符"));
        verify(familyRepository, never()).update(any());
    }

    @Test
    void freezeFamilyMarksReadonlyAndAudits() {
        PlatformAdminApplicationService service = newService("mouhin");
        when(familyRepository.findById(FAMILY_ID)).thenReturn(family());

        FamilyAdminRowVO row = service.updateFamilyStatus(FAMILY_ID, "FROZEN", ADMIN_USER_ID, "mouhin", "127.0.0.1");

        assertEquals("FROZEN", row.getStatus());
        assertEquals("冻结", row.getStatusLabel());
        ArgumentCaptor<OperationPerformedEvent> event = ArgumentCaptor.forClass(OperationPerformedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals("PLATFORM_FAMILY_STATUS", event.getValue().operationType());
        assertEquals("家族状态变更：正常 -> 冻结", event.getValue().operationDesc());
    }

    @Test
    void repeatedStatusChangeWritesNothing() {
        PlatformAdminApplicationService service = newService("mouhin");
        Family frozen = family();
        frozen.setStatus("FROZEN");
        when(familyRepository.findById(FAMILY_ID)).thenReturn(frozen);

        FamilyAdminRowVO row = service.updateFamilyStatus(FAMILY_ID, "FROZEN", ADMIN_USER_ID, "mouhin", "127.0.0.1");

        assertEquals("FROZEN", row.getStatus());
        verify(familyRepository, never()).update(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void unknownStatusCodeIsRejected() {
        PlatformAdminApplicationService service = newService("mouhin");
        when(familyRepository.findById(FAMILY_ID)).thenReturn(family());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateFamilyStatus(FAMILY_ID, "BANNED", ADMIN_USER_ID, "mouhin", "127.0.0.1"));

        assertEquals("无效的家族状态：BANNED", ex.getMessage());
        verify(familyRepository, never()).update(any());
    }

    @Test
    void transferPromotesTargetDemotesOwnerAndRepointsCreator() {
        PlatformAdminApplicationService service = newService("mouhin");
        Family family = family();
        when(familyRepository.findById(FAMILY_ID)).thenReturn(family);
        when(userRepository.findById(USER_ID)).thenReturn(platformUser());
        FamilyMember target = memberOf(FAMILY_ID, USER_ID, "MEMBER");
        FamilyMember previousOwner = memberOf(FAMILY_ID, CREATOR_ID, "OWNER");
        when(familyMemberRepository.findByFamilyIdAndUserId(FAMILY_ID, USER_ID)).thenReturn(target);
        when(familyMemberRepository.findByFamilyId(FAMILY_ID)).thenReturn(List.of(previousOwner, target));

        FamilyAdminRowVO row = service.transferOwner(FAMILY_ID, USER_ID, ADMIN_USER_ID, "mouhin", "127.0.0.1");

        assertEquals("OWNER", target.getRole());
        assertEquals("MEMBER", previousOwner.getRole());
        // 家族通知按 creatorId 路由，转让必须同步改写，否则新族长收不到本族通知
        assertEquals(USER_ID, family.getCreatorId());
        assertEquals(USER_ID, row.getCreatorId());
        verify(familyMemberRepository, times(2)).update(any(FamilyMember.class));

        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(3)).publishEvent(events.capture());
        List<Object> published = events.getAllValues();
        MemberRoleChangedEvent promoted = (MemberRoleChangedEvent) published.get(0);
        assertEquals(USER_ID, promoted.userId());
        assertEquals("OWNER", promoted.newRole());
        MemberRoleChangedEvent demoted = (MemberRoleChangedEvent) published.get(1);
        assertEquals(CREATOR_ID, demoted.userId());
        assertEquals("MEMBER", demoted.newRole());
        OperationPerformedEvent audit = (OperationPerformedEvent) published.get(2);
        assertEquals("PLATFORM_FAMILY_OWNER_TRANSFER", audit.operationType());
        assertTrue(audit.operationDesc().contains("mengziyu"), "审计描述应点名新族长账号");
        assertEquals(FAMILY_ID, audit.familyId());
    }

    @Test
    void transferToOutsiderIsRejected() {
        PlatformAdminApplicationService service = newService("mouhin");
        when(familyRepository.findById(FAMILY_ID)).thenReturn(family());
        when(userRepository.findById(USER_ID)).thenReturn(platformUser());
        when(familyMemberRepository.findByFamilyIdAndUserId(FAMILY_ID, USER_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.transferOwner(FAMILY_ID, USER_ID, ADMIN_USER_ID, "mouhin", "127.0.0.1"));

        assertEquals("该用户不属于此家族", ex.getMessage());
        verify(familyMemberRepository, never()).update(any());
        verify(familyRepository, never()).update(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void transferToCurrentOwnerIsRejected() {
        PlatformAdminApplicationService service = newService("mouhin");
        when(familyRepository.findById(FAMILY_ID)).thenReturn(family());
        when(userRepository.findById(CREATOR_ID)).thenReturn(creator());
        when(familyMemberRepository.findByFamilyIdAndUserId(FAMILY_ID, CREATOR_ID))
                .thenReturn(memberOf(FAMILY_ID, CREATOR_ID, "OWNER"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.transferOwner(FAMILY_ID, CREATOR_ID, ADMIN_USER_ID, "mouhin", "127.0.0.1"));

        assertEquals("该用户已是族长", ex.getMessage());
        verify(familyRepository, never()).update(any());
    }

    @Test
    void governanceWithoutFamilyIdIsRejected() {
        PlatformAdminApplicationService service = newService("mouhin");

        assertEquals("缺少家族ID", assertThrows(BusinessException.class,
                () -> service.renameFamily(null, "新名", ADMIN_USER_ID, "mouhin", "127.0.0.1")).getMessage());
        assertEquals("缺少家族ID", assertThrows(BusinessException.class,
                () -> service.updateFamilyStatus(null, "FROZEN", ADMIN_USER_ID, "mouhin", "127.0.0.1")).getMessage());
        assertEquals("缺少家族ID", assertThrows(BusinessException.class,
                () -> service.transferOwner(null, USER_ID, ADMIN_USER_ID, "mouhin", "127.0.0.1")).getMessage());
        verify(familyRepository, never()).update(any());
    }

    // ========== 日志检索 ==========
    @Test
    void logSearchWithoutFiltersReadsAllFamiliesAndFillsFamilyNames() {
        PlatformAdminApplicationService service = newService("mouhin");
        when(operationLogRepository.countByFilter(null, null, null, null, null)).thenReturn(2L);
        when(operationLogRepository.listByFilter(null, null, null, null, null, 0, 20))
                .thenReturn(List.of(operationLog(1L, FAMILY_ID, "NODE_CREATE"),
                        operationLog(2L, SECOND_FAMILY_ID, "LOGIN")));
        Family second = family();
        second.setId(SECOND_FAMILY_ID);
        second.setName("李氏家族");
        when(familyRepository.findByIds(anyList())).thenReturn(List.of(family(), second));

        PageResult<PlatformOperationLogVO> result = service.listOperationLogs(
                null, null, null, null, null, 1, 20);

        assertEquals(2L, result.getTotal());
        assertEquals("蒙氏家族", result.getRecords().get(0).getFamilyName());
        assertEquals("李氏家族", result.getRecords().get(1).getFamilyName());
        assertEquals(1, result.getRecords().get(0).getId());
    }

    @Test
    void logSearchShortCircuitsWhenNothingMatches() {
        PlatformAdminApplicationService service = newService("mouhin");
        when(operationLogRepository.countByFilter(FAMILY_ID, "LOGIN", "meng", null, null)).thenReturn(0L);

        PageResult<PlatformOperationLogVO> result = service.listOperationLogs(
                FAMILY_ID, "LOGIN", "meng", null, null, 1, 20);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
        verify(operationLogRepository, never())
                .listByFilter(any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void logSearchTrimsFiltersAndConvertsPageToOffset() {
        PlatformAdminApplicationService service = newService("mouhin");
        LocalDateTime start = LocalDateTime.of(2026, 11, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 16, 23, 59, 59);
        when(operationLogRepository.countByFilter(FAMILY_ID, "LOGIN", "meng", start, end)).thenReturn(30L);
        when(operationLogRepository.listByFilter(FAMILY_ID, "LOGIN", "meng", start, end, 40, 20))
                .thenReturn(List.of(operationLog(3L, null, "LOGIN")));

        PageResult<PlatformOperationLogVO> result = service.listOperationLogs(
                FAMILY_ID, "  LOGIN  ", "  meng  ", start, end, 3, 20);

        // 家族无关日志（登录）没有家族名可回填，但行本身仍要留痕
        assertNull(result.getRecords().get(0).getFamilyName());
        verify(familyRepository, never()).findByIds(anyList());
    }

    @Test
    void logSearchCapsPageSizeAndFloorPage() {
        PlatformAdminApplicationService service = newService("mouhin");
        when(operationLogRepository.countByFilter(null, null, null, null, null)).thenReturn(1L);
        when(operationLogRepository.listByFilter(null, null, null, null, null, 0, 100))
                .thenReturn(List.of(operationLog(4L, FAMILY_ID, "LOGIN")));
        when(familyRepository.findByIds(anyList())).thenReturn(List.of(family()));

        PageResult<PlatformOperationLogVO> oversized = service.listOperationLogs(
                null, null, null, null, null, 0, 500);
        PageResult<PlatformOperationLogVO> blankFilters = service.listOperationLogs(
                null, "   ", "   ", null, null, 1, 100);

        assertEquals(100, oversized.getSize());
        assertEquals(1, oversized.getPage());
        assertEquals(blankFilters.getSize(), oversized.getSize());
    }

    @Test
    void reversedTimeRangeIsRejectedBeforeAnyQuery() {
        PlatformAdminApplicationService service = newService("mouhin");
        LocalDateTime start = LocalDateTime.of(2026, 11, 16, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 1, 0, 0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.listOperationLogs(null, null, null, start, end, 1, 20));

        assertEquals("开始时间不能晚于结束时间", ex.getMessage());
        verify(operationLogRepository, never())
                .countByFilter(any(), any(), any(), any(), any());
    }

    @Test
    void logSearchLeavesFamilyNameEmptyWhenFamilyDeleted() {
        PlatformAdminApplicationService service = newService("mouhin");
        when(operationLogRepository.countByFilter(null, null, null, null, null)).thenReturn(1L);
        when(operationLogRepository.listByFilter(null, null, null, null, null, 0, 20))
                .thenReturn(List.of(operationLog(5L, FAMILY_ID, "NODE_DELETE")));
        when(familyRepository.findByIds(anyList())).thenReturn(List.of());

        PageResult<PlatformOperationLogVO> result = service.listOperationLogs(
                null, null, null, null, null, 1, 20);

        assertEquals(FAMILY_ID, result.getRecords().get(0).getFamilyId());
        assertNull(result.getRecords().get(0).getFamilyName());
    }

    @Test
    void operationTypesComeFromRepository() {
        PlatformAdminApplicationService service = newService("mouhin");
        when(operationLogRepository.listDistinctOperationTypes()).thenReturn(List.of("LOGIN", "NODE_CREATE"));

        assertEquals(List.of("LOGIN", "NODE_CREATE"), service.listOperationTypes());
    }

    private User platformUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("mengziyu");
        user.setNickname("蒙子羽");
        user.setPhone(RAW_PHONE);
        user.setEmail(RAW_EMAIL);
        user.setAutoAccount(true);
        user.setCurrentFamilyId(FAMILY_ID);
        user.setCreateTime(LocalDateTime.of(2026, 8, 1, 10, 0));
        user.setUpdateTime(LocalDateTime.of(2026, 11, 15, 10, 0));
        return user;
    }

    private Family secondFamily() {
        Family family = new Family();
        family.setId(SECOND_FAMILY_ID);
        family.setName("旁支陈氏");
        family.setInviteCode("ZZZZ9999");
        family.setCreatorId(CREATOR_ID);
        return family;
    }

    private FamilyMember member(Long familyId, String role, LocalDateTime joinedTime) {
        FamilyMember member = new FamilyMember();
        member.setFamilyId(familyId);
        member.setUserId(USER_ID);
        member.setRole(role);
        member.setJoinedTime(joinedTime);
        return member;
    }

    private FamilyMember memberOf(Long familyId, Long userId, String role) {
        FamilyMember member = member(familyId, role, LocalDateTime.of(2026, 8, 1, 9, 0));
        member.setUserId(userId);
        return member;
    }

    private FamilyUserNode binding(Long nodeId) {
        FamilyUserNode binding = new FamilyUserNode();
        binding.setFamilyId(FAMILY_ID);
        binding.setUserId(USER_ID);
        binding.setNodeId(nodeId);
        return binding;
    }

    private FamilyNode node() {
        FamilyNode node = new FamilyNode();
        node.setId(NODE_ID);
        node.setName("蒙爷爷");
        return node;
    }

    private OperationLog operationLog(Long id, Long familyId, String operationType) {
        OperationLog logEntry = new OperationLog();
        logEntry.setId(id);
        logEntry.setUserId(USER_ID);
        logEntry.setUsername("mengziyu");
        logEntry.setOperationType(operationType);
        logEntry.setOperationDesc("测试日志");
        logEntry.setTargetType("node");
        logEntry.setTargetId(NODE_ID);
        logEntry.setFamilyId(familyId);
        logEntry.setIpAddress("127.0.0.1");
        logEntry.setCreateTime(LocalDateTime.of(2026, 11, 16, 10, 0));
        return logEntry;
    }

    private FamilySetting setting(String key, String value) {
        FamilySetting setting = new FamilySetting();
        setting.setId(1L);
        setting.setFamilyId(FAMILY_ID);
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        return setting;
    }
}
