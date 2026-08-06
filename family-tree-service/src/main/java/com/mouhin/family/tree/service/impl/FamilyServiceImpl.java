package com.mouhin.family.tree.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.FamilyCreateDTO;
import com.mouhin.family.tree.common.dto.FamilyDTO;
import com.mouhin.family.tree.common.dto.FamilyJoinDTO;
import com.mouhin.family.tree.common.dto.FamilyMemberDTO;
import com.mouhin.family.tree.common.enums.FamilyMemberRoleEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.persistence.entity.FamilyDO;
import com.mouhin.family.tree.persistence.entity.FamilyGenerationDO;
import com.mouhin.family.tree.persistence.entity.FamilyMemberDO;
import com.mouhin.family.tree.persistence.entity.FamilyNodeDO;
import com.mouhin.family.tree.persistence.entity.FamilyOfferingDO;
import com.mouhin.family.tree.persistence.entity.FamilyRelationDO;
import com.mouhin.family.tree.persistence.entity.SysUserDO;
import com.mouhin.family.tree.persistence.mapper.FamilyGenerationMapper;
import com.mouhin.family.tree.persistence.mapper.FamilyMapper;
import com.mouhin.family.tree.persistence.mapper.FamilyMemberMapper;
import com.mouhin.family.tree.persistence.mapper.FamilyNodeMapper;
import com.mouhin.family.tree.persistence.mapper.FamilyOfferingMapper;
import com.mouhin.family.tree.persistence.mapper.FamilyRelationMapper;
import com.mouhin.family.tree.persistence.mapper.SysUserMapper;
import com.mouhin.family.tree.service.FamilyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 家族管理服务实现类
 *
 * @author Family-Tree
 * @date 2026-08-03
 */
@Service
public class FamilyServiceImpl implements FamilyService {

    private static final Logger logger = LoggerFactory.getLogger(FamilyServiceImpl.class);

    /** 邀请码字符集（去除易混淆字符 0/1/O/I/L） */
    private static final String INVITE_CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final FamilyMapper familyMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final FamilyNodeMapper familyNodeMapper;
    private final FamilyRelationMapper familyRelationMapper;
    private final FamilyGenerationMapper familyGenerationMapper;
    private final FamilyOfferingMapper familyOfferingMapper;
    private final SysUserMapper sysUserMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public FamilyServiceImpl(FamilyMapper familyMapper,
                             FamilyMemberMapper familyMemberMapper,
                             FamilyNodeMapper familyNodeMapper,
                             FamilyRelationMapper familyRelationMapper,
                             FamilyGenerationMapper familyGenerationMapper,
                             FamilyOfferingMapper familyOfferingMapper,
                             SysUserMapper sysUserMapper) {
        this.familyMapper = familyMapper;
        this.familyMemberMapper = familyMemberMapper;
        this.familyNodeMapper = familyNodeMapper;
        this.familyRelationMapper = familyRelationMapper;
        this.familyGenerationMapper = familyGenerationMapper;
        this.familyOfferingMapper = familyOfferingMapper;
        this.sysUserMapper = sysUserMapper;
    }

    @Override
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
        if (getMemberRecord(userId) != null) {
            throw new BusinessException("您已属于某个家族，请先退出后再创建");
        }

        // 创建家族
        FamilyDO family = new FamilyDO();
        family.setName(familyName);
        family.setInviteCode(generateUniqueInviteCode());
        family.setCreatorId(userId);
        family.setCreateTime(LocalDateTime.now());
        family.setUpdateTime(LocalDateTime.now());
        familyMapper.insert(family);

        // 创建者成为族长
        FamilyMemberDO member = new FamilyMemberDO();
        member.setFamilyId(family.getId());
        member.setUserId(userId);
        member.setRole(FamilyMemberRoleEnum.OWNER.getCode());
        member.setJoinedTime(LocalDateTime.now());
        member.setCreateTime(LocalDateTime.now());
        member.setUpdateTime(LocalDateTime.now());
        familyMemberMapper.insert(member);

        // 将该用户已有的领域数据迁移到新家族
        migrateUserDataToFamily(userId, family.getId());

        logger.info("Created family id={} name={} by user={}", family.getId(), familyName, userId);
        return family.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void joinFamily(Long userId, FamilyJoinDTO dto) {
        if (dto.getInviteCode() == null || dto.getInviteCode().isBlank()) {
            throw new BusinessException("邀请码不能为空");
        }

        // 检查用户是否已属于某个家族
        if (getMemberRecord(userId) != null) {
            throw new BusinessException("您已属于某个家族，请先退出后再加入其他家族");
        }

        // 查找邀请码对应的家族
        LambdaQueryWrapper<FamilyDO> familyQuery = new LambdaQueryWrapper<>();
        familyQuery.eq(FamilyDO::getInviteCode, dto.getInviteCode().trim().toUpperCase())
                .last("LIMIT 1");
        FamilyDO family = familyMapper.selectOne(familyQuery);
        if (family == null) {
            throw new BusinessException("邀请码无效");
        }

        // 检查成员数上限
        long memberCount = familyMemberMapper.selectCount(
                new LambdaQueryWrapper<FamilyMemberDO>()
                        .eq(FamilyMemberDO::getFamilyId, family.getId()));
        if (memberCount >= FamilyTreeConsts.MAX_FAMILY_MEMBERS) {
            throw new BusinessException("家族成员数已达上限（"
                    + FamilyTreeConsts.MAX_FAMILY_MEMBERS + "人）");
        }

        // 加入家族
        FamilyMemberDO member = new FamilyMemberDO();
        member.setFamilyId(family.getId());
        member.setUserId(userId);
        member.setRole(FamilyMemberRoleEnum.MEMBER.getCode());
        member.setJoinedTime(LocalDateTime.now());
        member.setCreateTime(LocalDateTime.now());
        member.setUpdateTime(LocalDateTime.now());
        familyMemberMapper.insert(member);

        // 将该用户已有的领域数据迁移到家族
        migrateUserDataToFamily(userId, family.getId());

        logger.info("User={} joined family id={} name={}", userId, family.getId(), family.getName());
    }

    @Override
    public FamilyDTO getCurrentFamily(Long userId) {
        FamilyMemberDO memberRecord = getMemberRecord(userId);
        if (memberRecord == null) {
            return null;
        }
        FamilyDO family = familyMapper.selectById(memberRecord.getFamilyId());
        if (family == null) {
            return null;
        }
        return toDTO(family, memberRecord.getRole());
    }

    @Override
    public FamilyDTO getFamilyById(Long familyId) {
        FamilyDO family = familyMapper.selectById(familyId);
        if (family == null) {
            return null;
        }
        return toDTO(family, null);
    }

    @Override
    public List<FamilyMemberDTO> listMembers(Long familyId) {
        LambdaQueryWrapper<FamilyMemberDO> query = new LambdaQueryWrapper<>();
        query.eq(FamilyMemberDO::getFamilyId, familyId)
                .orderByDesc(FamilyMemberDO::getRole)
                .orderByAsc(FamilyMemberDO::getJoinedTime);
        List<FamilyMemberDO> members = familyMemberMapper.selectList(query);

        // 批量查询用户昵称
        List<Long> userIds = members.stream()
                .map(FamilyMemberDO::getUserId)
                .collect(Collectors.toList());
        List<SysUserDO> users = userIds.isEmpty()
                ? List.of()
                : sysUserMapper.selectBatchIds(userIds);

        return members.stream().map(m -> {
            FamilyMemberDTO memberDTO = new FamilyMemberDTO();
            memberDTO.setUserId(m.getUserId());
            memberDTO.setRole(m.getRole());
            memberDTO.setJoinedTime(m.getJoinedTime());
            users.stream()
                    .filter(u -> Objects.equals(u.getId(), m.getUserId()))
                    .findFirst()
                    .ifPresent(u -> memberDTO.setNickname(u.getNickname()));
            return memberDTO;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long familyId, Long operatorUserId, Long targetUserId) {
        checkManager(familyId, operatorUserId);

        if (Objects.equals(operatorUserId, targetUserId)) {
            throw new BusinessException("不能移除自己");
        }

        FamilyMemberDO targetMember = getMemberRecordInFamily(familyId, targetUserId);
        if (targetMember == null) {
            throw new BusinessException("该用户不属于此家族");
        }
        // 管理员不能移除族长或其他管理员
        FamilyMemberDO operatorMember = getMemberRecordInFamily(familyId, operatorUserId);
        if (operatorMember != null
                && Objects.equals(operatorMember.getRole(), FamilyMemberRoleEnum.ADMIN.getCode())
                && !Objects.equals(targetMember.getRole(), FamilyMemberRoleEnum.MEMBER.getCode())) {
            throw new BusinessException("管理员只能移除普通成员");
        }
        familyMemberMapper.deleteById(targetMember.getId());
        logger.info("Removed user={} from family={} by operator={}",
                targetUserId, familyId, operatorUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String refreshInviteCode(Long familyId, Long operatorUserId) {
        checkManager(familyId, operatorUserId);

        String newCode = generateUniqueInviteCode();
        LambdaUpdateWrapper<FamilyDO> update = new LambdaUpdateWrapper<>();
        update.eq(FamilyDO::getId, familyId)
                .set(FamilyDO::getInviteCode, newCode)
                .set(FamilyDO::getUpdateTime, LocalDateTime.now());
        familyMapper.update(null, update);

        logger.info("Refreshed invite code for family={} by user={}", familyId, operatorUserId);
        return newCode;
    }

    @Override
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
        FamilyMemberDO targetMember = getMemberRecordInFamily(familyId, targetUserId);
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

        LambdaUpdateWrapper<FamilyMemberDO> update = new LambdaUpdateWrapper<>();
        update.eq(FamilyMemberDO::getId, targetMember.getId())
                .set(FamilyMemberDO::getRole, targetRole.getCode())
                .set(FamilyMemberDO::getUpdateTime, LocalDateTime.now());
        familyMemberMapper.update(null, update);

        logger.info("Set role={} for user={} in family={} by operator={}",
                targetRole.getCode(), targetUserId, familyId, operatorUserId);
    }

    /**
     * 查询用户所属的成员记录
     *
     * @param userId 用户ID
     * @return 成员记录，未加入任何家族返回 null
     */
    private FamilyMemberDO getMemberRecord(Long userId) {
        LambdaQueryWrapper<FamilyMemberDO> query = new LambdaQueryWrapper<>();
        query.eq(FamilyMemberDO::getUserId, userId)
                .last("LIMIT 1");
        return familyMemberMapper.selectOne(query);
    }

    /**
     * 查询用户在指定家族中的成员记录
     *
     * @param familyId 家族ID
     * @param userId   用户ID
     * @return 成员记录
     */
    private FamilyMemberDO getMemberRecordInFamily(Long familyId, Long userId) {
        LambdaQueryWrapper<FamilyMemberDO> query = new LambdaQueryWrapper<>();
        query.eq(FamilyMemberDO::getFamilyId, familyId)
                .eq(FamilyMemberDO::getUserId, userId)
                .last("LIMIT 1");
        return familyMemberMapper.selectOne(query);
    }

    /**
     * 校验操作者是否为族长
     *
     * @param familyId       家族ID
     * @param operatorUserId 操作者用户ID
     */
    private void checkOwner(Long familyId, Long operatorUserId) {
        FamilyMemberDO member = getMemberRecordInFamily(familyId, operatorUserId);
        if (member == null || !Objects.equals(member.getRole(), FamilyMemberRoleEnum.OWNER.getCode())) {
            throw new BusinessException("仅族长可执行此操作");
        }
    }

    /**
     * 校验操作者是否为族长或管理员
     *
     * @param familyId       家族ID
     * @param operatorUserId 操作者用户ID
     */
    private void checkManager(Long familyId, Long operatorUserId) {
        FamilyMemberDO member = getMemberRecordInFamily(familyId, operatorUserId);
        if (member == null) {
            throw new BusinessException("您不属于此家族");
        }
        String role = member.getRole();
        if (!Objects.equals(role, FamilyMemberRoleEnum.OWNER.getCode())
                && !Objects.equals(role, FamilyMemberRoleEnum.ADMIN.getCode())) {
            throw new BusinessException("仅管理员可执行此操作");
        }
    }

    /** 邀请码生成最大重试次数（防止极端情况下死循环） */
    private static final int INVITE_CODE_MAX_RETRIES = 10;

    /**
     * 生成随机邀请码（不检查唯一性）
     *
     * @return 邀请码字符串
     */
    private String generateInviteCode() {
        StringBuilder sb = new StringBuilder(FamilyTreeConsts.INVITE_CODE_LENGTH);
        for (int i = 0; i < FamilyTreeConsts.INVITE_CODE_LENGTH; i++) {
            sb.append(INVITE_CODE_CHARS.charAt(secureRandom.nextInt(INVITE_CODE_CHARS.length())));
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
            Long count = familyMapper.selectCount(
                    new LambdaQueryWrapper<FamilyDO>()
                            .eq(FamilyDO::getInviteCode, code));
            if (count == null || count == 0) {
                return code;
            }
            logger.warn("Invite code collision detected, retrying ({}/{})", i + 1, INVITE_CODE_MAX_RETRIES);
        }
        throw new BusinessException("邀请码生成失败，请稍后重试");
    }

    /**
     * 将用户已有的领域数据迁移到指定家族。
     * 更新各 domain 表中 user_id 对应记录的 family_id 字段。
     *
     * @param userId   用户ID
     * @param familyId 目标家族ID
     */
    private void migrateUserDataToFamily(Long userId, Long familyId) {
        // 节点表
        LambdaUpdateWrapper<FamilyNodeDO> nodeUpdate = new LambdaUpdateWrapper<>();
        nodeUpdate.eq(FamilyNodeDO::getUserId, userId)
                .set(FamilyNodeDO::getFamilyId, familyId);
        familyNodeMapper.update(null, nodeUpdate);

        // 关系表
        LambdaUpdateWrapper<FamilyRelationDO> relUpdate = new LambdaUpdateWrapper<>();
        relUpdate.eq(FamilyRelationDO::getUserId, userId)
                .set(FamilyRelationDO::getFamilyId, familyId);
        familyRelationMapper.update(null, relUpdate);

        // 辈分表
        LambdaUpdateWrapper<FamilyGenerationDO> genUpdate = new LambdaUpdateWrapper<>();
        genUpdate.eq(FamilyGenerationDO::getUserId, userId)
                .set(FamilyGenerationDO::getFamilyId, familyId);
        familyGenerationMapper.update(null, genUpdate);

        // 祭奠表
        LambdaUpdateWrapper<FamilyOfferingDO> offUpdate = new LambdaUpdateWrapper<>();
        offUpdate.eq(FamilyOfferingDO::getUserId, userId)
                .set(FamilyOfferingDO::getFamilyId, familyId);
        familyOfferingMapper.update(null, offUpdate);
    }

    private FamilyDTO toDTO(FamilyDO family, String currentRole) {
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

    @Override
    public void switchFamily(Long userId, Long familyId) {
        // 校验用户是否属于该家族
        LambdaQueryWrapper<FamilyMemberDO> query = new LambdaQueryWrapper<>();
        query.eq(FamilyMemberDO::getUserId, userId)
                .eq(FamilyMemberDO::getFamilyId, familyId);
        if (familyMemberMapper.selectCount(query) == 0) {
            throw new BusinessException("您不属于该家族");
        }
        // 更新用户的当前家族
        LambdaUpdateWrapper<SysUserDO> update = new LambdaUpdateWrapper<>();
        update.eq(SysUserDO::getId, userId)
                .set(SysUserDO::getCurrentFamilyId, familyId);
        sysUserMapper.update(null, update);
        logger.info("User {} switched to family {}", userId, familyId);
    }

    @Override
    public List<FamilyDTO> listMyFamilies(Long userId) {
        LambdaQueryWrapper<FamilyMemberDO> query = new LambdaQueryWrapper<>();
        query.eq(FamilyMemberDO::getUserId, userId);
        List<FamilyMemberDO> memberships = familyMemberMapper.selectList(query);

        List<FamilyDTO> result = new ArrayList<>();
        for (FamilyMemberDO member : memberships) {
            FamilyDO family = familyMapper.selectById(member.getFamilyId());
            if (family != null) {
                FamilyDTO dto = new FamilyDTO();
                dto.setId(family.getId());
                dto.setName(family.getName());
                dto.setInviteCode(family.getInviteCode());
                dto.setCreatorId(family.getCreatorId());
                dto.setCurrentRole(member.getRole());
                dto.setCreateTime(family.getCreateTime());
                dto.setHallName(family.getHallName());
                dto.setAncestralHome(family.getAncestralHome());
                dto.setGenerationCols(family.getGenerationCols() != null ? family.getGenerationCols() : 5);
                dto.setGenerationRows(family.getGenerationRows() != null ? family.getGenerationRows() : 5);
                result.add(dto);
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateFamilyInfo(Long familyId, Long operatorUserId, String hallName, String ancestralHome) {
        checkManager(familyId, operatorUserId);

        LambdaUpdateWrapper<FamilyDO> update = new LambdaUpdateWrapper<>();
        update.eq(FamilyDO::getId, familyId)
                .set(FamilyDO::getHallName, hallName)
                .set(FamilyDO::getAncestralHome, ancestralHome)
                .set(FamilyDO::getUpdateTime, LocalDateTime.now());
        familyMapper.update(null, update);

        logger.info("Updated family info for family={} by user={}", familyId, operatorUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateGenerationLayout(Long familyId, Long operatorUserId, Integer cols, Integer rows) {
        checkManager(familyId, operatorUserId);

        LambdaUpdateWrapper<FamilyDO> update = new LambdaUpdateWrapper<>();
        update.eq(FamilyDO::getId, familyId)
                .set(FamilyDO::getGenerationCols, cols)
                .set(FamilyDO::getGenerationRows, rows)
                .set(FamilyDO::getUpdateTime, LocalDateTime.now());
        familyMapper.update(null, update);

        logger.info("Updated generation layout for family={} cols={} rows={} by user={}",
                familyId, cols, rows, operatorUserId);
    }
}
