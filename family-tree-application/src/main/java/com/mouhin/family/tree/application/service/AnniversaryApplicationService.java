package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.AnniversaryDTO;
import com.mouhin.family.tree.common.dto.AnniversaryVO;
import com.mouhin.family.tree.common.enums.AnniversaryCategoryEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilyAnniversary;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.repository.AnniversaryRepository;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 家族纪念日应用服务
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Service
public class AnniversaryApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(AnniversaryApplicationService.class);

    private final AnniversaryRepository anniversaryRepository;
    private final FamilyNodeRepository familyNodeRepository;

    public AnniversaryApplicationService(AnniversaryRepository anniversaryRepository,
                                         FamilyNodeRepository familyNodeRepository) {
        this.anniversaryRepository = anniversaryRepository;
        this.familyNodeRepository = familyNodeRepository;
    }

    /**
     * 新增纪念日
     *
     * @param familyId 家族ID
     * @param userId   创建用户ID
     * @param dto      纪念日内容
     * @return 纪念日ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createAnniversary(Long familyId, Long userId, AnniversaryDTO dto) {
        FamilyAnniversary anniversary = new FamilyAnniversary();
        anniversary.setFamilyId(familyId);
        anniversary.setUserId(userId);
        anniversary.setNodeId(checkNode(dto.getNodeId(), familyId));
        anniversary.setTitle(dto.getTitle() != null ? dto.getTitle().trim() : null);
        anniversary.setCategory(checkCategory(dto.getCategory()));
        anniversary.setAnniversaryDate(parseDate(dto.getAnniversaryDate()));
        anniversary.setRemark(dto.getRemark());
        anniversary.setCreateTime(LocalDateTime.now());
        anniversary.setUpdateTime(LocalDateTime.now());
        anniversary.validateForCreate();
        anniversaryRepository.save(anniversary);
        logger.info("用户 {} 在家族 {} 新增纪念日: id={}", userId, familyId, anniversary.getId());
        return anniversary.getId();
    }

    /**
     * 查询家族纪念日列表（含周数与距下次纪念日天数）
     *
     * @param familyId      家族ID
     * @param currentUserId 当前用户ID
     * @return 纪念日列表（按距下次纪念日天数升序）
     */
    public List<AnniversaryVO> listAnniversaries(Long familyId, Long currentUserId) {
        List<FamilyAnniversary> anniversaries = anniversaryRepository.findByFamilyId(familyId);
        if (anniversaries.isEmpty()) {
            return List.of();
        }
        // 批量解析关联节点名称
        List<Long> nodeIds = anniversaries.stream()
                .map(FamilyAnniversary::getNodeId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> nodeNameMap = new HashMap<>((int) (nodeIds.size() / 0.75) + 1);
        if (!nodeIds.isEmpty()) {
            familyNodeRepository.findByIds(nodeIds)
                    .forEach(node -> nodeNameMap.put(node.getId(), node.getName()));
        }
        return anniversaries.stream()
                .map(a -> toVO(a, currentUserId, nodeNameMap))
                .sorted(Comparator.comparingInt(AnniversaryVO::getDaysUntil))
                .collect(Collectors.toList());
    }

    /**
     * 更新纪念日（仅创建者可修改）
     *
     * @param familyId 家族ID
     * @param id       纪念日ID
     * @param userId   当前用户ID
     * @param dto      纪念日内容
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateAnniversary(Long familyId, Long id, Long userId, AnniversaryDTO dto) {
        FamilyAnniversary anniversary = getAnniversaryChecked(familyId, id);
        if (!Objects.equals(anniversary.getUserId(), userId)) {
            throw new BusinessException("只能修改自己创建的纪念日");
        }
        anniversary.setNodeId(checkNode(dto.getNodeId(), familyId));
        if (dto.getTitle() != null && !dto.getTitle().isBlank()) {
            anniversary.setTitle(dto.getTitle().trim());
        }
        if (dto.getCategory() != null && !dto.getCategory().isBlank()) {
            anniversary.setCategory(checkCategory(dto.getCategory()));
        }
        if (dto.getAnniversaryDate() != null && !dto.getAnniversaryDate().isBlank()) {
            anniversary.setAnniversaryDate(parseDate(dto.getAnniversaryDate()));
        }
        anniversary.setRemark(dto.getRemark());
        anniversary.setUpdateTime(LocalDateTime.now());
        anniversary.validateForCreate();
        anniversaryRepository.update(anniversary);
        logger.info("用户 {} 更新纪念日: id={}", userId, id);
    }

    /**
     * 删除纪念日（仅创建者可删除）
     *
     * @param familyId 家族ID
     * @param id       纪念日ID
     * @param userId   当前用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteAnniversary(Long familyId, Long id, Long userId) {
        FamilyAnniversary anniversary = getAnniversaryChecked(familyId, id);
        if (!Objects.equals(anniversary.getUserId(), userId)) {
            throw new BusinessException("只能删除自己创建的纪念日");
        }
        anniversaryRepository.removeById(id);
        logger.info("用户 {} 删除纪念日: id={}", userId, id);
    }

    private FamilyAnniversary getAnniversaryChecked(Long familyId, Long id) {
        FamilyAnniversary anniversary = anniversaryRepository.findById(id);
        if (anniversary == null || !Objects.equals(anniversary.getFamilyId(), familyId)) {
            throw new BusinessException("纪念日不存在");
        }
        return anniversary;
    }

    /**
     * 校验关联节点（可空），必须属于当前家族
     */
    private Long checkNode(Long nodeId, Long familyId) {
        if (nodeId == null) {
            return null;
        }
        FamilyNode node = familyNodeRepository.findById(nodeId);
        if (node == null || !Objects.equals(node.getFamilyId(), familyId)) {
            throw new BusinessException("关联的家族成员不存在");
        }
        return nodeId;
    }

    private String checkCategory(String category) {
        if (category == null || category.isBlank()) {
            return AnniversaryCategoryEnum.OTHER.getCode();
        }
        return AnniversaryCategoryEnum.fromCode(category).getCode();
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            throw new BusinessException("请填写纪念日日期");
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException("日期格式错误，请使用 yyyy-MM-dd 格式");
        }
    }

    private AnniversaryVO toVO(FamilyAnniversary anniversary, Long currentUserId,
                               Map<Long, String> nodeNameMap) {
        AnniversaryVO vo = new AnniversaryVO();
        vo.setId(anniversary.getId());
        vo.setNodeId(anniversary.getNodeId());
        vo.setNodeName(anniversary.getNodeId() != null
                ? nodeNameMap.get(anniversary.getNodeId()) : null);
        vo.setTitle(anniversary.getTitle());
        vo.setCategory(anniversary.getCategory());
        vo.setCategoryDesc(getCategoryDesc(anniversary.getCategory()));
        vo.setAnniversaryDate(anniversary.getAnniversaryDate());
        vo.setRemark(anniversary.getRemark());
        vo.setCreateTime(anniversary.getCreateTime());
        vo.setOwn(Objects.equals(anniversary.getUserId(), currentUserId));

        LocalDate date = anniversary.getAnniversaryDate();
        LocalDate today = LocalDate.now();
        vo.setYears(today.getYear() - date.getYear());
        vo.setDaysUntil(calculateDaysUntil(date, today));
        return vo;
    }

    /**
     * 计算距下一次纪念日的天数（今年已过则算明年）
     */
    private int calculateDaysUntil(LocalDate date, LocalDate today) {
        try {
            LocalDate next = date.withYear(today.getYear());
            if (next.isBefore(today)) {
                next = date.withYear(today.getYear() + 1);
            }
            return (int) ChronoUnit.DAYS.between(today, next);
        } catch (RuntimeException e) {
            // 2月29日等特殊情况，直接按原始日期差值取绝对值估算
            return (int) Math.abs(ChronoUnit.DAYS.between(today, date));
        }
    }

    private String getCategoryDesc(String category) {
        for (AnniversaryCategoryEnum categoryEnum : AnniversaryCategoryEnum.values()) {
            if (categoryEnum.getCode().equals(category)) {
                return categoryEnum.getDescription();
            }
        }
        return category;
    }
}
