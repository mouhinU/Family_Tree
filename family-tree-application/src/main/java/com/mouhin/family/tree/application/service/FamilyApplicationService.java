package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.FamilyCreateDTO;
import com.mouhin.family.tree.common.dto.FamilyDTO;
import com.mouhin.family.tree.common.dto.FamilyJoinDTO;
import com.mouhin.family.tree.common.dto.FamilyMemberDTO;
import com.mouhin.family.tree.common.enums.FamilyMemberRoleEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.Family;
import com.mouhin.family.tree.domain.entity.FamilyMember;
import com.mouhin.family.tree.domain.entity.User;
import com.mouhin.family.tree.domain.event.FamilyCreatedEvent;
import com.mouhin.family.tree.domain.event.MemberJoinedEvent;
import com.mouhin.family.tree.domain.event.MemberRemovedEvent;
import com.mouhin.family.tree.domain.event.MemberRoleChangedEvent;
import com.mouhin.family.tree.domain.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 家族管理应用服务
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Service
public class FamilyApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(FamilyApplicationService.class);

    /**
     * 邀请码字符集（去除易混淆字符 0/1/O/I/L）
     */
    private static final String INVITE_CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    /**
     * 邀请码生成最大重试次数（防止极端情况下死循环）
     */
    private static final int INVITE_CODE_MAX_RETRIES = 10;

    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final FamilyNodeRepository familyNodeRepository;
    private final FamilyRelationRepository familyRelationRepository;
    private final FamilyGenerationRepository familyGenerationRepository;
    private final FamilyOfferingRepository familyOfferingRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SecureRandom secureRandom = new SecureRandom();

    public FamilyApplicationService(FamilyRepository familyRepository,
                                    FamilyMemberRepository familyMemberRepository,
                                    FamilyNodeRepository familyNodeRepository,
                                    FamilyRelationRepository familyRelationRepository,
                                    FamilyGenerationRepository familyGenerationRepository,
                                    FamilyOfferingRepository familyOfferingRepository,
                                    UserRepository userRepository,
                                    ApplicationEventPublisher eventPublisher) {
        this.familyRepository = familyRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.familyNodeRepository = familyNodeRepository;
        this.familyRelationRepository = familyRelationRepository;
        this.familyGenerationRepository = familyGenerationRepository;
        this.familyOfferingRepository = familyOfferingRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 创建家族
     *
     * @param userId 操作者用户ID
     * @param dto    创建请求
     * @return 新家族ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createFamily(Long userId, FamilyCreateDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessException("家族名称不能为空");
        }
        String familyName = dto.getName().trim();
        if (familyName.length() > 100) {
            throw new BusinessException("家族名称不能超过100个字符");
        }

        // 检查用户是否已属于某个家族
        if (!familyMemberRepository.findByUserId(userId).isEmpty()) {
            throw new BusinessException("您已属于某个家族，请先退出后再创建");
        }

        // 创建家族
        Family family = new Family();
        family.setName(familyName);
        family.setInviteCode(generateUniqueInviteCode());
        family.setCreatorId(userId);
        family.setCreateTime(LocalDateTime.now());
        family.setUpdateTime(LocalDateTime.now());
        familyRepository.save(family);

        // 创建者成为族长
        FamilyMember member = new FamilyMember();
        member.setFamilyId(family.getId());
        member.setUserId(userId);
        member.setRole(FamilyMemberRoleEnum.OWNER.getCode());
        member.setJoinedTime(LocalDateTime.now());
        member.setCreateTime(LocalDateTime.now());
        member.setUpdateTime(LocalDateTime.now());
        familyMemberRepository.save(member);

        // 将该用户已有的领域数据迁移到新家族
        migrateUserDataToFamily(userId, family.getId());

        logger.info("Created family id={} name={} by user={}", family.getId(), familyName, userId);
        eventPublisher.publishEvent(FamilyCreatedEvent.of(family.getId(), userId, familyName));
        return family.getId();
    }

    /**
     * 加入家族
     *
     * @param userId 操作者用户ID
     * @param dto    加入请求（含邀请码）
     */
    @Transactional(rollbackFor = Exception.class)
    public void joinFamily(Long userId, FamilyJoinDTO dto) {
        if (dto.getInviteCode() == null || dto.getInviteCode().isBlank()) {
            throw new BusinessException("邀请码不能为空");
        }

        // 检查用户是否已属于某个家族
        if (!familyMemberRepository.findByUserId(userId).isEmpty()) {
            throw new BusinessException("您已属于某个家族，请先退出后再加入其他家族");
        }

        // 查找邀请码对应的家族（大小写不敏感）
        Family family = familyRepository.findByInviteCode(dto.getInviteCode().trim().toUpperCase());
        if (family == null) {
            throw new BusinessException("邀请码无效");
        }

        // 检查成员数上限
        long memberCount = familyMemberRepository.countByFamilyId(family.getId());
        if (memberCount >= FamilyTreeConsts.MAX_FAMILY_MEMBERS) {
            throw new BusinessException("家族成员数已达上限（"
                    + FamilyTreeConsts.MAX_FAMILY_MEMBERS + "人）");
        }

        // 加入家族
        FamilyMember member = new FamilyMember();
        member.setFamilyId(family.getId());
        member.setUserId(userId);
        member.setRole(FamilyMemberRoleEnum.MEMBER.getCode());
        member.setJoinedTime(LocalDateTime.now());
        member.setCreateTime(LocalDateTime.now());
        member.setUpdateTime(LocalDateTime.now());
        familyMemberRepository.save(member);

        // 将该用户已有的领域数据迁移到家族
        migrateUserDataToFamily(userId, family.getId());

        logger.info("User={} joined family id={} name={}", userId, family.getId(), family.getName());
        eventPublisher.publishEvent(MemberJoinedEvent.of(family.getId(), userId, null));
    }

    /**
     * 获取用户当前所属家族
     *
     * @param userId 用户ID
     * @return 家族信息，未加入返回 null
     */
    public FamilyDTO getCurrentFamily(Long userId) {
        List<FamilyMember> memberRecords = familyMemberRepository.findByUserId(userId);
        if (memberRecords.isEmpty()) {
            return null;
        }
        FamilyMember memberRecord = memberRecords.get(0);
        Family family = familyRepository.findById(memberRecord.getFamilyId());
        if (family == null) {
            return null;
        }
        return toDTO(family, memberRecord.getRole());
    }

    /**
     * 根据ID获取家族信息
     *
     * @param familyId 家族ID
     * @return 家族信息，不存在返回 null
     */
    public FamilyDTO getFamilyById(Long familyId) {
        Family family = familyRepository.findById(familyId);
        if (family == null) {
            return null;
        }
        return toDTO(family, null);
    }

    /**
     * 列出家族所有成员
     *
     * @param familyId 家族ID
     * @return 成员列表
     */
    public List<FamilyMemberDTO> listMembers(Long familyId) {
        List<FamilyMember> members = familyMemberRepository.findByFamilyId(familyId);

        // 批量查询用户昵称
        List<Long> userIds = members.stream()
                .map(FamilyMember::getUserId)
                .collect(Collectors.toList());
        Map<Long, User> userMap = userIds.isEmpty()
                ? Map.of()
                : userRepository.findByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return members.stream().map(m -> {
            FamilyMemberDTO memberDTO = new FamilyMemberDTO();
            memberDTO.setUserId(m.getUserId());
            memberDTO.setRole(m.getRole());
            memberDTO.setJoinedTime(m.getJoinedTime());
            User user = userMap.get(m.getUserId());
            if (user != null) {
                memberDTO.setNickname(user.getNickname());
            }
            return memberDTO;
        }).collect(Collectors.toList());
    }

    /**
     * 移除家族成员
     *
     * @param familyId       家族ID
     * @param operatorUserId 操作者用户ID
     * @param targetUserId   被移除用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long familyId, Long operatorUserId, Long targetUserId) {
        checkManager(familyId, operatorUserId);

        if (Objects.equals(operatorUserId, targetUserId)) {
            throw new BusinessException("不能移除自己");
        }

        FamilyMember targetMember = familyMemberRepository.findByFamilyIdAndUserId(familyId, targetUserId);
        if (targetMember == null) {
            throw new BusinessException("该用户不属于此家族");
        }

        // 管理员不能移除族长或其他管理员
        FamilyMember operatorMember = familyMemberRepository.findByFamilyIdAndUserId(familyId, operatorUserId);
        if (operatorMember != null
                && Objects.equals(operatorMember.getRole(), FamilyMemberRoleEnum.ADMIN.getCode())
                && !Objects.equals(targetMember.getRole(), FamilyMemberRoleEnum.MEMBER.getCode())) {
            throw new BusinessException("管理员只能移除普通成员");
        }

        familyMemberRepository.removeById(targetMember.getId());
        logger.info("Removed user={} from family={} by operator={}",
                targetUserId, familyId, operatorUserId);
        eventPublisher.publishEvent(MemberRemovedEvent.of(familyId, targetUserId));
    }

    /**
     * 刷新家族邀请码
     *
     * @param familyId       家族ID
     * @param operatorUserId 操作者用户ID
     * @return 新邀请码
     */
    @Transactional(rollbackFor = Exception.class)
    public String refreshInviteCode(Long familyId, Long operatorUserId) {
        checkManager(familyId, operatorUserId);

        String newCode = generateUniqueInviteCode();
        Family family = familyRepository.findById(familyId);
        if (family == null) {
            throw new BusinessException("家族不存在");
        }
        family.setInviteCode(newCode);
        family.setUpdateTime(LocalDateTime.now());
        familyRepository.update(family);

        logger.info("Refreshed invite code for family={} by user={}", familyId, operatorUserId);
        return newCode;
    }

    /**
     * 设置成员角色
     *
     * @param familyId       家族ID
     * @param operatorUserId 操作者用户ID
     * @param targetUserId   目标用户ID
     * @param role           目标角色编码
     */
    @Transactional(rollbackFor = Exception.class)
    public void setMemberRole(Long familyId, Long operatorUserId, Long targetUserId, String role) {
        // 仅族长可设置角色
        checkOwner(familyId, operatorUserId);

        // 校验目标角色合法性
        FamilyMemberRoleEnum targetRole;
        try {
            targetRole = FamilyMemberRoleEnum.fromCode(role);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("无效的角色：" + role);
        }
        if (targetRole == FamilyMemberRoleEnum.OWNER) {
            throw new BusinessException("不能将成员设为族长");
        }

        // 不能修改族长角色
        FamilyMember targetMember = familyMemberRepository.findByFamilyIdAndUserId(familyId, targetUserId);
        if (targetMember == null) {
            throw new BusinessException("该用户不属于此家族");
        }
        if (Objects.equals(targetMember.getRole(), FamilyMemberRoleEnum.OWNER.getCode())) {
            throw new BusinessException("不能修改族长角色");
        }

        // 角色未变化则跳过
        if (Objects.equals(targetMember.getRole(), targetRole.getCode())) {
            return;
        }

        targetMember.setRole(targetRole.getCode());
        targetMember.setUpdateTime(LocalDateTime.now());
        familyMemberRepository.update(targetMember);

        logger.info("Set role={} for user={} in family={} by operator={}",
                targetRole.getCode(), targetUserId, familyId, operatorUserId);
        eventPublisher.publishEvent(
                MemberRoleChangedEvent.of(familyId, targetUserId, targetRole.getCode()));
    }

    /**
     * 切换用户当前家族
     *
     * @param userId   用户ID
     * @param familyId 目标家族ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void switchFamily(Long userId, Long familyId) {
        FamilyMember member = familyMemberRepository.findByFamilyIdAndUserId(familyId, userId);
        if (member == null) {
            throw new BusinessException("您不属于该家族");
        }

        User user = userRepository.findById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setCurrentFamilyId(familyId);
        user.setUpdateTime(LocalDateTime.now());
        userRepository.update(user);

        logger.info("User {} switched to family {}", userId, familyId);
    }

    /**
     * 列出用户所属的所有家族
     *
     * @param userId 用户ID
     * @return 家族列表
     */
    public List<FamilyDTO> listMyFamilies(Long userId) {
        List<FamilyMember> memberships = familyMemberRepository.findByUserId(userId);

        List<FamilyDTO> result = new ArrayList<>();
        for (FamilyMember member : memberships) {
            Family family = familyRepository.findById(member.getFamilyId());
            if (family != null) {
                result.add(toDTO(family, member.getRole()));
            }
        }
        return result;
    }

    /**
     * 更新家族信息（堂号、祖籍）
     *
     * @param familyId       家族ID
     * @param operatorUserId 操作者用户ID
     * @param hallName       堂号
     * @param ancestralHome  祖籍
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateFamilyInfo(Long familyId, Long operatorUserId,
                                 String hallName, String ancestralHome) {
        checkManager(familyId, operatorUserId);

        Family family = familyRepository.findById(familyId);
        if (family == null) {
            throw new BusinessException("家族不存在");
        }
        family.updateHallInfo(hallName, ancestralHome);
        family.setUpdateTime(LocalDateTime.now());
        familyRepository.update(family);

        logger.info("Updated family info for family={} by user={}", familyId, operatorUserId);
    }

    /**
     * 更新辈分管理布局（行列数）
     *
     * @param familyId       家族ID
     * @param operatorUserId 操作者用户ID
     * @param cols           列数
     * @param rows           行数
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateGenerationLayout(Long familyId, Long operatorUserId,
                                       Integer cols, Integer rows) {
        checkManager(familyId, operatorUserId);

        Family family = familyRepository.findById(familyId);
        if (family == null) {
            throw new BusinessException("家族不存在");
        }
        family.setGenerationCols(cols);
        family.setGenerationRows(rows);
        family.setUpdateTime(LocalDateTime.now());
        familyRepository.update(family);

        logger.info("Updated generation layout for family={} cols={} rows={} by user={}",
                familyId, cols, rows, operatorUserId);
    }

    /**
     * 校验操作者是否为族长
     */
    private void checkOwner(Long familyId, Long operatorUserId) {
        FamilyMember member = familyMemberRepository.findByFamilyIdAndUserId(familyId, operatorUserId);
        if (member == null || !Objects.equals(member.getRole(), FamilyMemberRoleEnum.OWNER.getCode())) {
            throw new BusinessException("仅族长可执行此操作");
        }
    }

    /**
     * 校验操作者是否为族长或管理员
     */
    private void checkManager(Long familyId, Long operatorUserId) {
        FamilyMember member = familyMemberRepository.findByFamilyIdAndUserId(familyId, operatorUserId);
        if (member == null) {
            throw new BusinessException("您不属于此家族");
        }
        String role = member.getRole();
        if (!Objects.equals(role, FamilyMemberRoleEnum.OWNER.getCode())
                && !Objects.equals(role, FamilyMemberRoleEnum.ADMIN.getCode())) {
            throw new BusinessException("仅管理员可执行此操作");
        }
    }

    /**
     * 生成随机邀请码（不检查唯一性）
     */
    private String generateInviteCode() {
        StringBuilder sb = new StringBuilder(FamilyTreeConsts.INVITE_CODE_LENGTH);
        for (int i = 0; i < FamilyTreeConsts.INVITE_CODE_LENGTH; i++) {
            sb.append(INVITE_CODE_CHARS.charAt(
                    secureRandom.nextInt(INVITE_CODE_CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * 生成全局唯一的邀请码，碰撞时自动重试
     *
     * @return 未使用的邀请码
     * @throws BusinessException 超过最大重试次数时抛出
     */
    private String generateUniqueInviteCode() {
        for (int i = 0; i < INVITE_CODE_MAX_RETRIES; i++) {
            String code = generateInviteCode();
            if (familyRepository.findByInviteCode(code) == null) {
                return code;
            }
            logger.warn("Invite code collision detected, retrying ({}/{})",
                    i + 1, INVITE_CODE_MAX_RETRIES);
        }
        throw new BusinessException("邀请码生成失败，请稍后重试");
    }

    /**
     * 将用户已有的领域数据迁移到指定家族。
     * 更新各 domain 表中 user_id 对应记录的 family_id 字段。
     */
    private void migrateUserDataToFamily(Long userId, Long familyId) {
        familyNodeRepository.updateFamilyIdByUserId(userId, familyId);
        familyRelationRepository.updateFamilyIdByUserId(userId, familyId);
        familyGenerationRepository.updateFamilyIdByUserId(userId, familyId);
        familyOfferingRepository.updateFamilyIdByUserId(userId, familyId);
    }

    private FamilyDTO toDTO(Family family, String currentRole) {
        FamilyDTO dto = new FamilyDTO();
        dto.setId(family.getId());
        dto.setName(family.getName());
        dto.setInviteCode(family.getInviteCode());
        dto.setCreatorId(family.getCreatorId());
        dto.setCurrentRole(currentRole);
        dto.setCreateTime(family.getCreateTime());
        dto.setHallName(family.getHallName());
        dto.setAncestralHome(family.getAncestralHome());
        dto.setGenerationCols(family.getGenerationCols() != null ? family.getGenerationCols() : 5);
        dto.setGenerationRows(family.getGenerationRows() != null ? family.getGenerationRows() : 5);
        return dto;
    }
}
