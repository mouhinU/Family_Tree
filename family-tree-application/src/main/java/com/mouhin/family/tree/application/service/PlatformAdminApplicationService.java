package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.FamilyAdminDetailVO;
import com.mouhin.family.tree.common.dto.FamilyAdminRowVO;
import com.mouhin.family.tree.common.dto.FamilySettingSaveDTO;
import com.mouhin.family.tree.common.dto.FamilySettingVO;
import com.mouhin.family.tree.common.dto.GenerationRepairSummaryVO;
import com.mouhin.family.tree.common.dto.OperationLogDTO;
import com.mouhin.family.tree.common.dto.PageResult;
import com.mouhin.family.tree.common.dto.PlatformOperationLogVO;
import com.mouhin.family.tree.common.dto.PlatformOverviewVO;
import com.mouhin.family.tree.common.dto.UserAdminDetailVO;
import com.mouhin.family.tree.common.dto.UserAdminFamilyVO;
import com.mouhin.family.tree.common.dto.UserAdminRowVO;
import com.mouhin.family.tree.common.dto.UserPasswordResetVO;
import com.mouhin.family.tree.common.enums.FamilyMemberRoleEnum;
import com.mouhin.family.tree.common.enums.FamilySettingKeyEnum;
import com.mouhin.family.tree.common.enums.FamilyStatusEnum;
import com.mouhin.family.tree.common.enums.UserAccountTypeEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.common.util.DesensitizeUtils;
import com.mouhin.family.tree.domain.entity.Family;
import com.mouhin.family.tree.domain.entity.FamilyMember;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilySetting;
import com.mouhin.family.tree.domain.entity.FamilyUserNode;
import com.mouhin.family.tree.domain.entity.OperationLog;
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
import com.mouhin.family.tree.domain.repository.SnapshotRepository;
import com.mouhin.family.tree.domain.repository.UserRepository;
import com.mouhin.family.tree.domain.service.FamilySettingDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 平台（系统）管理员应用服务：跨家族总览、家族概况分页与家族级配置读写。
 *
 * <p>该服务的身份判定独立于家族角色：白名单来自配置项
 * {@code family-tree.platform.admin-usernames}（逗号分隔的用户名），族长 / 管理员不因此获得平台权限。
 * 联系方式在平台视图中一律脱敏，原始值仅在其所属家族内按成员权限展示。</p>
 *
 * @author Family-Tree
 * @date 2026-11-15
 */
@Service
public class PlatformAdminApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(PlatformAdminApplicationService.class);

    /**
     * 家族列表默认每页大小
     */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * 家族列表最大每页大小，同时约束分组统计 IN 条件的元素数量
     */
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 家族详情中携带的最近操作条数
     */
    private static final int RECENT_OPERATION_LIMIT = 10;

    /**
     * "近期新建家族" 的统计窗口天数
     */
    private static final int RECENT_FAMILY_WINDOW_DAYS = 30;

    /**
     * 白名单配置的分隔符
     */
    private static final String ADMIN_USERNAMES_SEPARATOR = ",";

    /**
     * 平台操作日志类型：家族级配置更新
     */
    private static final String OPERATION_TYPE_SETTING_UPDATE = "PLATFORM_SETTING_UPDATE";

    /**
     * 平台操作日志类型：账号口令重置
     */
    private static final String OPERATION_TYPE_PASSWORD_RESET = "PLATFORM_PASSWORD_RESET";

    /**
     * 平台操作日志类型：账号解锁
     */
    private static final String OPERATION_TYPE_ACCOUNT_UNLOCK = "PLATFORM_ACCOUNT_UNLOCK";

    /**
     * 平台操作日志类型：家族改名
     */
    private static final String OPERATION_TYPE_FAMILY_RENAME = "PLATFORM_FAMILY_RENAME";

    /**
     * 平台操作日志类型：家族状态变更
     */
    private static final String OPERATION_TYPE_FAMILY_STATUS = "PLATFORM_FAMILY_STATUS";

    /**
     * 平台操作日志类型：平台转让族长
     */
    private static final String OPERATION_TYPE_FAMILY_OWNER_TRANSFER = "PLATFORM_FAMILY_OWNER_TRANSFER";

    /**
     * 平台操作日志类型：全平台世代修复
     */
    private static final String OPERATION_TYPE_GENERATION_REPAIR = "PLATFORM_GEN_REPAIR";

    /**
     * 全平台世代修复遍历家族时的分页大小
     */
    private static final int REPAIR_SCAN_PAGE_SIZE = 200;

    /**
     * 全平台世代修复摘要中保留的失败样例上限
     */
    private static final int REPAIR_FAILURE_SAMPLE_LIMIT = 10;

    /**
     * 平台操作日志的对象类型：账号
     */
    private static final String TARGET_TYPE_USER = "user";

    /**
     * 平台操作日志的对象类型：家族
     */
    private static final String TARGET_TYPE_FAMILY = "family";

    private final FamilyRepository familyRepository;
    private final FamilyNodeRepository familyNodeRepository;
    private final FamilyRelationRepository familyRelationRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final FamilyUserNodeRepository familyUserNodeRepository;
    private final FamilyPhotoRepository familyPhotoRepository;
    private final FamilySettingRepository familySettingRepository;
    private final SnapshotRepository snapshotRepository;
    private final UserRepository userRepository;
    private final OperationLogRepository operationLogRepository;
    private final PlatformSettingApplicationService platformSettingService;
    private final FamilySettingDomainService settingDomainService;
    private final OperationLogApplicationService operationLogService;
    private final UserAuthApplicationService userAuthApplicationService;
    private final LoginAttemptService loginAttemptService;
    private final GenerationRepairApplicationService generationRepairService;
    private final ApplicationEventPublisher eventPublisher;
    private final Set<String> platformAdminUsernames;

    public PlatformAdminApplicationService(FamilyRepository familyRepository,
                                           FamilyNodeRepository familyNodeRepository,
                                           FamilyRelationRepository familyRelationRepository,
                                           FamilyMemberRepository familyMemberRepository,
                                           FamilyUserNodeRepository familyUserNodeRepository,
                                           FamilyPhotoRepository familyPhotoRepository,
                                           FamilySettingRepository familySettingRepository,
                                           SnapshotRepository snapshotRepository,
                                           UserRepository userRepository,
                                           OperationLogRepository operationLogRepository,
                                           PlatformSettingApplicationService platformSettingService,
                                           FamilySettingDomainService settingDomainService,
                                           OperationLogApplicationService operationLogService,
                                           UserAuthApplicationService userAuthApplicationService,
                                           LoginAttemptService loginAttemptService,
                                           GenerationRepairApplicationService generationRepairService,
                                           ApplicationEventPublisher eventPublisher,
                                           @Value("${family-tree.platform.admin-usernames:}")
                                           String adminUsernames) {
        this.familyRepository = familyRepository;
        this.familyNodeRepository = familyNodeRepository;
        this.familyRelationRepository = familyRelationRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.familyUserNodeRepository = familyUserNodeRepository;
        this.familyPhotoRepository = familyPhotoRepository;
        this.familySettingRepository = familySettingRepository;
        this.snapshotRepository = snapshotRepository;
        this.userRepository = userRepository;
        this.operationLogRepository = operationLogRepository;
        this.platformSettingService = platformSettingService;
        this.settingDomainService = settingDomainService;
        this.operationLogService = operationLogService;
        this.userAuthApplicationService = userAuthApplicationService;
        this.loginAttemptService = loginAttemptService;
        this.generationRepairService = generationRepairService;
        this.eventPublisher = eventPublisher;
        this.platformAdminUsernames = parseAdminUsernames(adminUsernames);
        logger.info("Platform admin whitelist loaded, configured size: {}", platformAdminUsernames.size());
    }

    /**
     * 判断用户是否在平台管理员白名单内
     *
     * @param userId 用户ID
     * @return 是否具备平台管理权限
     */
    public boolean isPlatformAdmin(Long userId) {
        if (userId == null || platformAdminUsernames.isEmpty()) {
            return false;
        }
        User user = userRepository.findById(userId);
        return user != null && platformAdminUsernames.contains(user.getUsername());
    }

    /**
     * 平台总览统计
     *
     * @return 总览视图对象
     */
    public PlatformOverviewVO getOverview() {
        PlatformOverviewVO vo = new PlatformOverviewVO();
        vo.setFamilyCount(familyRepository.countAll());
        vo.setUserCount(userRepository.countAll());
        vo.setAutoAccountCount(userRepository.countAutoAccounts());
        vo.setNodeCount(familyNodeRepository.countAll());
        vo.setRelationCount(familyRelationRepository.countAll());
        vo.setRecentWindowDays(RECENT_FAMILY_WINDOW_DAYS);
        vo.setRecentFamilyCount(familyRepository.countCreatedAfter(
                LocalDateTime.now().minusDays(RECENT_FAMILY_WINDOW_DAYS)));
        return vo;
    }

    /**
     * 分页查询家族概况
     *
     * @param keyword 名称 / 堂号前缀或邀请码精确词，可为空
     * @param page    页码（从 1 开始）
     * @param size    每页大小，超过上限时按上限截断
     * @return 家族概况分页结果
     */
    public PageResult<FamilyAdminRowVO> listFamilies(String keyword, int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = normalizePageSize(size);
        long total = familyRepository.countByKeyword(keyword);
        if (total == 0) {
            return new PageResult<>(List.of(), 0L, normalizedPage, normalizedSize);
        }
        int offset = (normalizedPage - 1) * normalizedSize;
        List<Family> families = familyRepository.listPaged(keyword, offset, normalizedSize);
        return new PageResult<>(buildRows(families), total, normalizedPage, normalizedSize);
    }

    /**
     * 分页查询平台用户
     *
     * @param keyword     用户名 / 昵称前缀或手机号邮箱精确词，可为空
     * @param accountType 账号类型筛选，null 或 ALL 表示不过滤
     * @param page        页码（从 1 开始）
     * @param size        每页大小，超过上限时按上限截断
     * @return 用户分页结果
     */
    public PageResult<UserAdminRowVO> listUsers(String keyword, UserAccountTypeEnum accountType,
                                                int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = normalizePageSize(size);
        Boolean autoAccount = toAutoAccountFilter(accountType);
        long total = userRepository.countByFilter(keyword, autoAccount);
        if (total == 0) {
            return new PageResult<>(List.of(), 0L, normalizedPage, normalizedSize);
        }
        int offset = (normalizedPage - 1) * normalizedSize;
        List<User> users = userRepository.listPaged(keyword, autoAccount, offset, normalizedSize);
        return new PageResult<>(buildUserRows(users), total, normalizedPage, normalizedSize);
    }

    /**
     * 平台级检索跨家族的操作日志
     *
     * <p>日志是只读留痕：家族被删除后其历史日志仍可检索，只是家族名称回落为 null；
     * 时间区间为闭区间，由调用方把"某一天"换算成当天的起止时刻。</p>
     *
     * @param familyId      家族ID，null 表示全部家族
     * @param operationType 操作类型编码，空白表示全部
     * @param keyword       用户名 / 操作描述前缀，空白表示不过滤
     * @param startTime     操作时间下界（含），null 表示不限
     * @param endTime       操作时间上界（含），null 表示不限
     * @param page          页码（从 1 开始）
     * @param size          每页大小，超过上限时按上限截断
     * @return 日志分页结果，按操作时间倒序
     */
    public PageResult<PlatformOperationLogVO> listOperationLogs(Long familyId, String operationType, String keyword,
                                                                LocalDateTime startTime, LocalDateTime endTime,
                                                                int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = normalizePageSize(size);
        String trimmedType = operationType == null || operationType.isBlank() ? null : operationType.trim();
        String trimmedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        assertTimeRangeOrdered(startTime, endTime);

        long total = operationLogRepository.countByFilter(
                familyId, trimmedType, trimmedKeyword, startTime, endTime);
        if (total == 0) {
            return new PageResult<>(List.of(), 0L, normalizedPage, normalizedSize);
        }
        int offset = (normalizedPage - 1) * normalizedSize;
        List<OperationLog> logs = operationLogRepository.listByFilter(
                familyId, trimmedType, trimmedKeyword, startTime, endTime, offset, normalizedSize);
        return new PageResult<>(buildLogRows(logs), total, normalizedPage, normalizedSize);
    }

    /**
     * 列出平台日志检索可选的操作类型（含家族与平台两侧全部留痕类型）
     *
     * @return 去重并按字典序排列的操作类型编码
     */
    public List<String> listOperationTypes() {
        return operationLogRepository.listDistinctOperationTypes();
    }

    /**
     * 查询单个账号的平台侧概况：基础信息、加入的家族与节点标记情况
     *
     * @param userId 用户ID
     * @return 用户详情视图对象
     */
    public UserAdminDetailVO getUserDetail(Long userId) {
        User user = requireUser(userId);
        UserAdminDetailVO detail = new UserAdminDetailVO();
        detail.setUser(buildUserRows(Collections.singletonList(user)).get(0));
        detail.setFamilies(buildUserFamilies(user));
        return detail;
    }

    /**
     * 平台侧重置账号口令：换为随机一次性口令并强制首登改密，同时解除登录失败锁定
     *
     * <p>重置只影响下一次登录，已登录的会话不会失效。</p>
     *
     * @param userId       目标用户ID
     * @param operatorId   操作人用户ID
     * @param operatorName 操作人名称
     * @param ipAddress    操作来源IP
     * @return 重置结果，一次性口令仅在此处返回一次
     */
    @Transactional(rollbackFor = Exception.class)
    public UserPasswordResetVO resetPassword(Long userId, Long operatorId, String operatorName, String ipAddress) {
        User user = requireUser(userId);
        String oneTimePassword = userAuthApplicationService.resetPasswordByPlatform(userId);

        UserPasswordResetVO vo = new UserPasswordResetVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setNewPassword(oneTimePassword);
        vo.setMustChangePassword(Boolean.TRUE);

        eventPublisher.publishEvent(OperationPerformedEvent.of(operatorId, operatorName,
                OPERATION_TYPE_PASSWORD_RESET, "重置账号 " + user.getUsername() + " 的登录口令",
                TARGET_TYPE_USER, user.getId(), user.getCurrentFamilyId(), ipAddress));
        return vo;
    }

    /**
     * 平台侧解除账号锁定：清零登录失败计数，使被锁账号可立即重试登录
     *
     * @param userId       目标用户ID
     * @param operatorId   操作人用户ID
     * @param operatorName 操作人名称
     * @param ipAddress    操作来源IP
     */
    @Transactional(rollbackFor = Exception.class)
    public void unlockUser(Long userId, Long operatorId, String operatorName, String ipAddress) {
        User user = requireUser(userId);
        loginAttemptService.clearAttempts(user.getUsername());
        eventPublisher.publishEvent(OperationPerformedEvent.of(operatorId, operatorName,
                OPERATION_TYPE_ACCOUNT_UNLOCK, "解除账号 " + user.getUsername() + " 的登录锁定",
                TARGET_TYPE_USER, user.getId(), user.getCurrentFamilyId(), ipAddress));
    }

    /**
     * 查询单个家族的概况详情
     *
     * @param familyId 家族ID
     * @return 详情视图对象
     */
    public FamilyAdminDetailVO getFamilyDetail(Long familyId) {
        Family family = requireFamily(familyId);
        FamilyAdminDetailVO detail = new FamilyAdminDetailVO();
        detail.setFamily(buildRows(Collections.singletonList(family)).get(0));
        detail.setSnapshotCount(toInt(snapshotRepository.countByFamilyId(familyId)));
        detail.setPhotoCount(toInt(familyPhotoRepository.countByFamilyId(familyId)));

        PageResult<OperationLogDTO> logs =
                operationLogService.listLogs(familyId, null, 1, RECENT_OPERATION_LIMIT);
        List<OperationLogDTO> records = logs.getRecords();
        detail.setRecentOperations(records);
        detail.setLastOperationTime(records.isEmpty() ? null : records.get(0).getCreateTime());
        detail.setSettings(listSettings(familyId));
        return detail;
    }

    /**
     * 查询家族级配置（家族覆盖 → 平台覆盖 → 代码默认 三级合并后的生效值）
     *
     * @param familyId 家族ID
     * @return 全部已登记配置项的视图列表
     */
    public List<FamilySettingVO> listSettings(Long familyId) {
        requireFamily(familyId);
        Map<String, String> overrides = loadOverrides(familyId);
        Map<String, String> platformDefaults = platformSettingService.familyScopedDefaults();
        Map<String, String> effective = settingDomainService.mergeWithDefaults(overrides, platformDefaults);
        List<FamilySettingKeyEnum> keys = FamilySettingKeyEnum.listAll();
        List<FamilySettingVO> voList = new ArrayList<>(keys.size());
        for (FamilySettingKeyEnum key : keys) {
            FamilySettingVO vo = new FamilySettingVO();
            vo.setKey(key.getCode());
            vo.setLabel(key.getLabel());
            vo.setValueType(key.getValueType().getCode());
            vo.setValue(effective.get(key.getCode()));
            vo.setDefaultValue(platformDefaults.get(key.getCode()));
            vo.setOverridden(overrides.containsKey(key.getCode()));
            vo.setMinValue(key.getMinValue());
            vo.setMaxValue(key.getMaxValue());
            vo.setMaxLength(key.getMaxLength());
            vo.setDescription(key.getDescription());
            voList.add(vo);
        }
        return voList;
    }

    /**
     * 保存家族级配置覆盖值，未提交的配置项保持原状。
     *
     * <p>取值与上层（平台层）生效值一致时不写覆盖行，并删除已有覆盖行，等同于恢复默认。</p>
     *
     * @param familyId      家族ID
     * @param dto           配置提交对象
     * @param operatorId    操作人用户ID
     * @param operatorName  操作人名称
     * @param ipAddress     操作来源IP
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveSettings(Long familyId, FamilySettingSaveDTO dto, Long operatorId,
                             String operatorName, String ipAddress) {
        requireFamily(familyId);
        List<FamilySetting> validated = settingDomainService.toValidatedEntities(
                familyId, dto == null ? null : dto.getSettings());
        Map<String, String> platformDefaults = platformSettingService.familyScopedDefaults();
        int written = 0;
        int resetToDefault = 0;
        for (FamilySetting setting : validated) {
            FamilySetting existing = familySettingRepository.findByFamilyIdAndKey(
                    familyId, setting.getSettingKey());
            if (settingDomainService.isDefaultValue(setting, platformDefaults)) {
                if (existing != null) {
                    familySettingRepository.removeByKey(familyId, setting.getSettingKey());
                    resetToDefault++;
                }
                continue;
            }
            if (existing == null) {
                familySettingRepository.save(setting);
            } else {
                existing.setSettingValue(setting.getSettingValue());
                familySettingRepository.update(existing);
            }
            written++;
        }
        logger.info("Family settings saved for familyId: {}, written: {}, resetToDefault: {}",
                familyId, written, resetToDefault);
        eventPublisher.publishEvent(OperationPerformedEvent.of(operatorId, operatorName,
                OPERATION_TYPE_SETTING_UPDATE,
                "更新家族平台配置" + validated.size() + "项（覆盖" + written + "项，恢复默认" + resetToDefault + "项）",
                TARGET_TYPE_FAMILY, familyId, familyId, ipAddress));
    }

    /**
     * 平台侧重命名家族
     *
     * @param familyId      家族ID
     * @param name          新名称
     * @param operatorId    操作人用户ID
     * @param operatorName  操作人名称
     * @param ipAddress     操作来源IP
     * @return 变更后的家族概况行
     */
    @Transactional(rollbackFor = Exception.class)
    public FamilyAdminRowVO renameFamily(Long familyId, String name, Long operatorId,
                                         String operatorName, String ipAddress) {
        Family family = requireFamily(familyId);
        String oldName = family.getName();
        family.setName(name == null ? null : name.trim());
        family.validateName();
        family.setUpdateTime(LocalDateTime.now());
        familyRepository.update(family);

        logger.info("Family renamed, familyId: {}, oldName: {}, newName: {}", familyId, oldName, family.getName());
        publishFamilyOperation(operatorId, operatorName, OPERATION_TYPE_FAMILY_RENAME,
                "重命名家族：" + oldName + " -> " + family.getName(), familyId, ipAddress);
        return toRow(family);
    }

    /**
     * 平台侧变更家族状态：冻结后家族只读，归档后对普通成员整体下线
     *
     * @param familyId      家族ID
     * @param statusCode    目标状态编码
     * @param operatorId    操作人用户ID
     * @param operatorName  操作人名称
     * @param ipAddress     操作来源IP
     * @return 变更后的家族概况行
     */
    @Transactional(rollbackFor = Exception.class)
    public FamilyAdminRowVO updateFamilyStatus(Long familyId, String statusCode, Long operatorId,
                                               String operatorName, String ipAddress) {
        Family family = requireFamily(familyId);
        FamilyStatusEnum targetStatus = parseStatus(statusCode);
        FamilyStatusEnum currentStatus = family.statusOrDefault();
        if (currentStatus == targetStatus) {
            return toRow(family);
        }

        family.changeStatus(targetStatus);
        family.setUpdateTime(LocalDateTime.now());
        familyRepository.update(family);

        logger.info("Family status changed, familyId: {}, {} -> {}", familyId, currentStatus.getCode(),
                targetStatus.getCode());
        publishFamilyOperation(operatorId, operatorName, OPERATION_TYPE_FAMILY_STATUS,
                "家族状态变更：" + currentStatus.getLabel() + " -> " + targetStatus.getLabel(), familyId, ipAddress);
        return toRow(family);
    }

    /**
     * 平台侧转让族长：新族长升为 OWNER，原族长降为 MEMBER，家族创建者指向新族长
     *
     * <p>用于原族长账号注销、长期失联等场景；成员关系与节点绑定保持不变。</p>
     *
     * @param familyId      家族ID
     * @param targetUserId  新族长用户ID，须为该家族成员
     * @param operatorId    操作人用户ID
     * @param operatorName  操作人名称
     * @param ipAddress     操作来源IP
     * @return 变更后的家族概况行
     */
    @Transactional(rollbackFor = Exception.class)
    public FamilyAdminRowVO transferOwner(Long familyId, Long targetUserId, Long operatorId,
                                          String operatorName, String ipAddress) {
        Family family = requireFamily(familyId);
        User targetUser = requireUser(targetUserId);
        FamilyMember targetMember = familyMemberRepository.findByFamilyIdAndUserId(familyId, targetUserId);
        if (targetMember == null) {
            throw new BusinessException("该用户不属于此家族");
        }
        if (targetMember.isOwner()) {
            throw new BusinessException("该用户已是族长");
        }

        LocalDateTime now = LocalDateTime.now();
        FamilyMember previousOwner = null;
        for (FamilyMember member : familyMemberRepository.findByFamilyId(familyId)) {
            if (member.isOwner()) {
                member.setRole(FamilyMemberRoleEnum.MEMBER.getCode());
                member.setUpdateTime(now);
                familyMemberRepository.update(member);
                previousOwner = member;
            }
        }

        targetMember.setRole(FamilyMemberRoleEnum.OWNER.getCode());
        targetMember.setUpdateTime(now);
        familyMemberRepository.update(targetMember);
        eventPublisher.publishEvent(MemberRoleChangedEvent.of(familyId, targetUserId,
                FamilyMemberRoleEnum.OWNER.getCode()));

        family.setCreatorId(targetUserId);
        family.setUpdateTime(now);
        familyRepository.update(family);

        logger.info("Family owner transferred, familyId: {}, from: {}, to: {}", familyId,
                previousOwner == null ? null : previousOwner.getUserId(), targetUserId);
        if (previousOwner != null) {
            eventPublisher.publishEvent(MemberRoleChangedEvent.of(familyId, previousOwner.getUserId(),
                    FamilyMemberRoleEnum.MEMBER.getCode()));
        }
        publishFamilyOperation(operatorId, operatorName, OPERATION_TYPE_FAMILY_OWNER_TRANSFER,
                "转让族长给 " + targetUser.getUsername(), familyId, ipAddress);
        return toRow(family);
    }

    /**
     * 平台侧一键全量修复世代：未维护辈分表的家族按树深度从根节点逐级重算并落库
     *
     * <p>已维护辈分表的家族以现有辈分为准，直接跳过不覆盖。方法自身不开启事务，
     * 每个家族在修复服务内各起一个独立事务：单家族失败只计入失败清单，不回滚、不阻断其余家族。</p>
     *
     * @param operatorId   操作人用户ID
     * @param operatorName 操作人名称
     * @param ipAddress    操作来源IP
     * @return 修复结果摘要
     */
    public GenerationRepairSummaryVO repairAllFamilies(Long operatorId, String operatorName, String ipAddress) {
        int scanned = 0;
        int repaired = 0;
        int skippedManaged = 0;
        int unchanged = 0;
        int failed = 0;
        int updatedNodes = 0;
        List<String> failures = new ArrayList<>();
        int offset = 0;
        while (true) {
            List<Family> page = familyRepository.listPaged(null, offset, REPAIR_SCAN_PAGE_SIZE);
            for (Family family : page) {
                scanned++;
                try {
                    GenerationRepairApplicationService.RepairResult result =
                            generationRepairService.repairIfUnmanaged(family.getId());
                    if (result.generationManaged()) {
                        skippedManaged++;
                    } else if (result.updatedNodes() > 0) {
                        repaired++;
                        updatedNodes += result.updatedNodes();
                    } else {
                        unchanged++;
                    }
                } catch (Exception ex) {
                    failed++;
                    if (failures.size() < REPAIR_FAILURE_SAMPLE_LIMIT) {
                        failures.add("家族 " + family.getId() + "(" + family.getName() + ")：" + ex.getMessage());
                    }
                    logger.error("Generation repair failed for familyId: " + family.getId(), ex);
                }
            }
            if (page.size() < REPAIR_SCAN_PAGE_SIZE) {
                break;
            }
            offset += REPAIR_SCAN_PAGE_SIZE;
        }

        GenerationRepairSummaryVO summary = new GenerationRepairSummaryVO();
        summary.setScannedFamilies(scanned);
        summary.setRepairedFamilies(repaired);
        summary.setSkippedManagedFamilies(skippedManaged);
        summary.setUnchangedFamilies(unchanged);
        summary.setFailedFamilies(failed);
        summary.setUpdatedNodes(updatedNodes);
        summary.setFailures(failures);

        String detail = "全平台世代修复：扫描 " + scanned + " 个家族，修复 " + repaired
                + " 个家族 / " + updatedNodes + " 个节点，跳过已维护辈分 " + skippedManaged
                + " 个，无变化 " + unchanged + " 个，失败 " + failed + " 个";
        logger.info(detail);
        eventPublisher.publishEvent(OperationPerformedEvent.of(operatorId, operatorName,
                OPERATION_TYPE_GENERATION_REPAIR, detail, TARGET_TYPE_FAMILY, null, null, ipAddress));
        return summary;
    }

    /**
     * 批量组装日志检索行：家族名称按本页去重后一次取回，家族已删除时名称留空
     */
    private List<PlatformOperationLogVO> buildLogRows(List<OperationLog> logs) {
        if (logs.isEmpty()) {
            return List.of();
        }
        Set<Long> distinctFamilyIds = new HashSet<>(logs.size());
        for (OperationLog logEntry : logs) {
            if (logEntry.getFamilyId() != null) {
                distinctFamilyIds.add(logEntry.getFamilyId());
            }
        }
        Map<Long, String> familyNames = new LinkedHashMap<>(Math.max(distinctFamilyIds.size(), 1));
        if (!distinctFamilyIds.isEmpty()) {
            for (Family family : familyRepository.findByIds(new ArrayList<>(distinctFamilyIds))) {
                familyNames.put(family.getId(), family.getName());
            }
        }

        List<PlatformOperationLogVO> rows = new ArrayList<>(logs.size());
        for (OperationLog logEntry : logs) {
            PlatformOperationLogVO row = new PlatformOperationLogVO();
            row.setId(logEntry.getId());
            row.setUserId(logEntry.getUserId());
            row.setUsername(logEntry.getUsername());
            row.setOperationType(logEntry.getOperationType());
            row.setOperationDesc(logEntry.getOperationDesc());
            row.setTargetType(logEntry.getTargetType());
            row.setTargetId(logEntry.getTargetId());
            row.setFamilyId(logEntry.getFamilyId());
            row.setFamilyName(familyNames.get(logEntry.getFamilyId()));
            row.setIpAddress(logEntry.getIpAddress());
            row.setCreateTime(logEntry.getCreateTime());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 校验时间区间顺序，颠倒的起止会被静默查成空集，必须显式拒绝
     */
    private void assertTimeRangeOrdered(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new BusinessException("开始时间不能晚于结束时间");
        }
    }

    /**
     * 批量组装家族概况行，分组统计一次取回，避免逐家族往返
     */
    private List<FamilyAdminRowVO> buildRows(List<Family> families) {
        if (families.isEmpty()) {
            return List.of();
        }
        List<Long> familyIds = new ArrayList<>(families.size());
        for (Family family : families) {
            familyIds.add(family.getId());
        }
        Map<Long, Long> memberCounts = familyMemberRepository.countGroupedByFamilyIds(familyIds);
        Map<Long, Long> nodeCounts = familyNodeRepository.countGroupedByFamilyIds(familyIds);
        Map<Long, Long> deceasedCounts = familyNodeRepository.countDeceasedGroupedByFamilyIds(familyIds);
        Map<Long, Long> relationCounts = familyRelationRepository.countGroupedByFamilyIds(familyIds);
        Map<Long, User> creators = loadCreators(families);

        List<FamilyAdminRowVO> rows = new ArrayList<>(families.size());
        for (Family family : families) {
            Long id = family.getId();
            FamilyAdminRowVO row = new FamilyAdminRowVO();
            row.setId(id);
            row.setName(family.getName());
            row.setHallName(family.getHallName());
            row.setAncestralHome(family.getAncestralHome());
            row.setInviteCode(family.getInviteCode());
            row.setCreatorId(family.getCreatorId());
            User creator = creators.get(family.getCreatorId());
            if (creator != null) {
                row.setCreatorNickname(creator.getNickname() == null
                        ? creator.getUsername() : creator.getNickname());
                row.setCreatorPhone(DesensitizeUtils.maskPhone(creator.getPhone()));
            }
            row.setMemberCount(toInt(memberCounts.get(id)));
            row.setNodeCount(toInt(nodeCounts.get(id)));
            row.setDeceasedNodeCount(toInt(deceasedCounts.get(id)));
            row.setRelationCount(toInt(relationCounts.get(id)));
            row.setGenerationCols(family.getGenerationCols());
            row.setGenerationRows(family.getGenerationRows());
            FamilyStatusEnum familyStatus = family.statusOrDefault();
            row.setStatus(familyStatus.getCode());
            row.setStatusLabel(familyStatus.getLabel());
            row.setStatusDescription(familyStatus.getDescription());
            row.setCreateTime(family.getCreateTime());
            row.setUpdateTime(family.getUpdateTime());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 一次取回当页家族的创建者信息
     */
    private Map<Long, User> loadCreators(List<Family> families) {
        Set<Long> creatorIds = new HashSet<>(families.size() * 2);
        for (Family family : families) {
            if (family.getCreatorId() != null) {
                creatorIds.add(family.getCreatorId());
            }
        }
        if (creatorIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<User> users = userRepository.findByIds(new ArrayList<>(creatorIds));
        Map<Long, User> result = new LinkedHashMap<>(users.size() * 2);
        for (User user : users) {
            result.put(user.getId(), user);
        }
        return result;
    }

    /**
     * 账号类型筛选转为自动账号标记：ALL 不过滤，AUTO 仅 true，REAL 含存量未标记数据
     */
    private Boolean toAutoAccountFilter(UserAccountTypeEnum accountType) {
        if (accountType == null || accountType == UserAccountTypeEnum.ALL) {
            return null;
        }
        return accountType == UserAccountTypeEnum.AUTO;
    }

    /**
     * 批量组装用户列表行：加入家族数一次分组统计，当前家族名一次批量取回
     */
    private List<UserAdminRowVO> buildUserRows(List<User> users) {
        if (users.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = new ArrayList<>(users.size());
        Set<Long> familyIds = new HashSet<>(users.size() * 2);
        for (User user : users) {
            userIds.add(user.getId());
            if (user.getCurrentFamilyId() != null) {
                familyIds.add(user.getCurrentFamilyId());
            }
        }
        Map<Long, Long> familyCounts = familyMemberRepository.countGroupedByUserIds(userIds);
        Map<Long, Family> currentFamilies = loadFamiliesByIds(new ArrayList<>(familyIds));

        List<UserAdminRowVO> rows = new ArrayList<>(users.size());
        for (User user : users) {
            UserAdminRowVO row = new UserAdminRowVO();
            row.setId(user.getId());
            row.setUsername(user.getUsername());
            row.setNickname(user.getNickname());
            row.setPhone(DesensitizeUtils.maskPhone(user.getPhone()));
            row.setEmail(DesensitizeUtils.maskEmail(user.getEmail()));
            row.setAutoAccount(Boolean.TRUE.equals(user.getAutoAccount()));
            row.setMustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()));
            row.setFamilyCount(toInt(familyCounts.get(user.getId())));
            row.setCurrentFamilyId(user.getCurrentFamilyId());
            Family current = currentFamilies.get(user.getCurrentFamilyId());
            row.setCurrentFamilyName(current == null ? null : current.getName());
            row.setCreateTime(user.getCreateTime());
            row.setUpdateTime(user.getUpdateTime());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 组装账号加入的家族行，按加入时间正序；节点标记逐家族查询，单账号家族数量很小
     */
    private List<UserAdminFamilyVO> buildUserFamilies(User user) {
        List<FamilyMember> members = familyMemberRepository.findByUserId(user.getId());
        if (members.isEmpty()) {
            return List.of();
        }
        List<Long> memberFamilyIds = new ArrayList<>(members.size());
        for (FamilyMember member : members) {
            memberFamilyIds.add(member.getFamilyId());
        }
        Map<Long, Family> families = loadFamiliesByIds(memberFamilyIds);

        List<UserAdminFamilyVO> rows = new ArrayList<>(members.size());
        for (FamilyMember member : members) {
            UserAdminFamilyVO row = new UserAdminFamilyVO();
            Family family = families.get(member.getFamilyId());
            row.setFamilyId(member.getFamilyId());
            row.setFamilyName(family == null ? null : family.getName());
            row.setRole(member.getRole());
            row.setRoleLabel(roleLabel(member.getRole()));
            row.setCurrent(Objects.equals(member.getFamilyId(), user.getCurrentFamilyId()));
            row.setJoinedTime(member.getJoinedTime());
            FamilyUserNode binding = familyUserNodeRepository
                    .findByFamilyIdAndUserId(member.getFamilyId(), user.getId());
            if (binding != null && binding.getNodeId() != null) {
                row.setBoundNodeId(binding.getNodeId());
                FamilyNode node = familyNodeRepository.findById(binding.getNodeId());
                row.setBoundNodeName(node == null ? null : node.getName());
            }
            rows.add(row);
        }
        rows.sort(Comparator.comparing(UserAdminFamilyVO::getJoinedTime,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return rows;
    }

    private Map<Long, Family> loadFamiliesByIds(List<Long> familyIds) {
        if (familyIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Family> families = familyRepository.findByIds(familyIds);
        Map<Long, Family> result = new LinkedHashMap<>(families.size() * 2);
        for (Family family : families) {
            result.put(family.getId(), family);
        }
        return result;
    }

    /**
     * 角色编码转中文名，未知编码原样返回以免历史数据打断整个列表
     */
    private String roleLabel(String roleCode) {
        for (FamilyMemberRoleEnum role : FamilyMemberRoleEnum.values()) {
            if (role.getCode().equals(roleCode)) {
                return role.getDescription();
            }
        }
        return roleCode;
    }

    private Map<String, String> loadOverrides(Long familyId) {
        List<FamilySetting> settings = familySettingRepository.findByFamilyId(familyId);
        Map<String, String> overrides = new LinkedHashMap<>(settings.size() * 2);
        for (FamilySetting setting : settings) {
            overrides.put(setting.getSettingKey(), setting.getSettingValue());
        }
        return overrides;
    }

    /**
     * 单个家族组装概况行，供治理操作回传给前端就地刷新
     */
    private FamilyAdminRowVO toRow(Family family) {
        return buildRows(Collections.singletonList(family)).get(0);
    }

    /**
     * 解析状态编码，非法值转为业务异常
     */
    private FamilyStatusEnum parseStatus(String statusCode) {
        try {
            return FamilyStatusEnum.fromCode(statusCode);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("无效的家族状态：" + statusCode);
        }
    }

    private void publishFamilyOperation(Long operatorId, String operatorName, String operationType,
                                        String detail, Long familyId, String ipAddress) {
        eventPublisher.publishEvent(OperationPerformedEvent.of(operatorId, operatorName,
                operationType, detail, TARGET_TYPE_FAMILY, familyId, familyId, ipAddress));
    }

    private Family requireFamily(Long familyId) {
        if (familyId == null) {
            throw new BusinessException("缺少家族ID");
        }
        Family family = familyRepository.findById(familyId);
        if (family == null) {
            throw new BusinessException("家族不存在");
        }
        return family;
    }

    private User requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException("缺少用户ID");
        }
        User user = userRepository.findById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private int normalizePageSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private Integer toInt(Long value) {
        return value == null ? Integer.valueOf(0) : Integer.valueOf(Math.toIntExact(value));
    }

    private Integer toInt(long value) {
        return Integer.valueOf(Math.toIntExact(value));
    }

    private static Set<String> parseAdminUsernames(String configured) {
        if (configured == null || configured.isBlank()) {
            return Collections.emptySet();
        }
        String[] parts = configured.split(ADMIN_USERNAMES_SEPARATOR);
        Set<String> usernames = new HashSet<String>((int) (parts.length / 0.75) + 1);
        for (String part : parts) {
            String username = part.trim();
            if (!username.isEmpty()) {
                usernames.add(username);
            }
        }
        return Collections.unmodifiableSet(usernames);
    }
}
