package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.FamilyNodeDTO;
import com.mouhin.family.tree.common.dto.NodeCreateDTO;
import com.mouhin.family.tree.common.enums.ColorLabelEnum;
import com.mouhin.family.tree.common.enums.RelationTypeEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyRelation;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.FamilyRelationRepository;
import com.mouhin.family.tree.domain.event.FamilyTreeUpdatedEvent;
import com.mouhin.family.tree.domain.event.NodeCreatedEvent;
import com.mouhin.family.tree.domain.service.FamilyNodeDomainService;
import com.mouhin.family.tree.domain.service.RelationValidationDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 族谱节点应用服务
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Service
public class FamilyNodeApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(FamilyNodeApplicationService.class);

    private final FamilyNodeRepository familyNodeRepository;
    private final FamilyRelationRepository familyRelationRepository;
    private final RelationValidationDomainService relationValidationDomainService;
    private final FamilyNodeDomainService familyNodeDomainService;
    private final FamilyTreeApplicationService familyTreeApplicationService;
    private final ApplicationEventPublisher eventPublisher;

    public FamilyNodeApplicationService(FamilyNodeRepository familyNodeRepository,
                                        FamilyRelationRepository familyRelationRepository,
                                        RelationValidationDomainService relationValidationDomainService,
                                        FamilyNodeDomainService familyNodeDomainService,
                                        FamilyTreeApplicationService familyTreeApplicationService,
                                        ApplicationEventPublisher eventPublisher) {
        this.familyNodeRepository = familyNodeRepository;
        this.familyRelationRepository = familyRelationRepository;
        this.relationValidationDomainService = relationValidationDomainService;
        this.familyNodeDomainService = familyNodeDomainService;
        this.familyTreeApplicationService = familyTreeApplicationService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 创建族谱节点
     *
     * @param familyId 家族ID
     * @param userId   操作者用户ID
     * @param dto      创建请求
     * @return 新节点ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createNode(Long familyId, Long userId, NodeCreateDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessException("节点名称不能为空");
        }
        if (dto.getName().trim().length() > FamilyTreeConsts.MAX_NAME_LENGTH) {
            throw new BusinessException("节点名称不能超过"
                    + FamilyTreeConsts.MAX_NAME_LENGTH + "个字符");
        }

        FamilyNode node = new FamilyNode();
        node.setUserId(userId);
        node.setFamilyId(familyId);
        node.setName(dto.getName().trim());
        node.setGender(dto.getGender() != null ? dto.getGender() : 0);
        if (node.getGender() < 0 || node.getGender() > 2) {
            throw new BusinessException("性别值无效，应为 0（未知）、1（男）或 2（女）");
        }
        node.setBirthDate(parseDate(dto.getBirthDate()));
        node.setDeathDate(parseDate(dto.getDeathDate()));

        // 生卒日期顺序校验
        if (node.getBirthDate() != null && node.getDeathDate() != null
                && node.getDeathDate().isBefore(node.getBirthDate())) {
            throw new BusinessException("去世日期不能早于出生日期");
        }

        if (dto.getColorLabel() != null) {
            ColorLabelEnum.validateCode(dto.getColorLabel());
            node.setColorLabel(dto.getColorLabel());
        } else {
            node.setColorLabel(ColorLabelEnum.DEFAULT.getCode());
        }
        node.setAvatar(dto.getAvatar());
        if (dto.getRemark() != null && dto.getRemark().length() > FamilyTreeConsts.MAX_REMARK_LENGTH) {
            throw new BusinessException("备注不能超过"
                    + FamilyTreeConsts.MAX_REMARK_LENGTH + "个字符");
        }
        node.setRemark(dto.getRemark());
        node.setLunarBirthDate(dto.getLunarBirthDate());
        node.setLunarDeathDate(dto.getLunarDeathDate());
        node.setZi(dto.getZi());
        node.setHao(dto.getHao());
        node.setHui(dto.getHui());
        if (dto.getGraveLocation() != null) {
            node.setGraveLocation(dto.getGraveLocation());
        }
        node.setSpouseName(dto.getSpouseName());
        node.setSpouseOriginFamily(dto.getSpouseOriginFamily());
        node.setCreateTime(LocalDateTime.now());
        node.setUpdateTime(LocalDateTime.now());

        // 计算世代层级
        if (dto.getParentNodeId() != null) {
            FamilyNode parent = checkNodeBelongsToFamily(familyId, dto.getParentNodeId());
            node.setGeneration(parent.getGeneration() + 1);
        } else if (dto.getChildNodeId() != null) {
            FamilyNode child = checkNodeBelongsToFamily(familyId, dto.getChildNodeId());
            node.setGeneration(Math.max(child.getGeneration() - 1, 1));
        } else if (dto.getSpouseNodeId() != null) {
            FamilyNode spouse = checkNodeBelongsToFamily(familyId, dto.getSpouseNodeId());
            node.setGeneration(spouse.getGeneration());
        } else {
            node.setGeneration(FamilyTreeConsts.DEFAULT_GENERATION);
        }

        if (node.getGeneration() != null
                && node.getGeneration() > FamilyTreeConsts.MAX_GENERATION_DEPTH) {
            throw new BusinessException("世代层级不能超过"
                    + FamilyTreeConsts.MAX_GENERATION_DEPTH + "世");
        }

        // 同胞排次：优先使用传入值；新增子节点且未传时自动追加为末位
        if (dto.getBirthOrder() != null) {
            if (dto.getBirthOrder() < 1) {
                throw new BusinessException("排次不能小于1");
            }
            node.setBirthOrder(dto.getBirthOrder());
        } else if (dto.getParentNodeId() != null) {
            node.setBirthOrder(countChildren(familyId, dto.getParentNodeId()) + 1);
        }

        familyNodeRepository.save(node);
        logger.info("Created family node id={} name={} for family={} by user={}",
                node.getId(), node.getName(), familyId, userId);

        // 建立亲子关系
        if (dto.getParentNodeId() != null) {
            FamilyRelation relation = new FamilyRelation();
            relation.setUserId(userId);
            relation.setFamilyId(familyId);
            relation.setFromNodeId(dto.getParentNodeId());
            relation.setToNodeId(node.getId());
            relation.setRelationType(RelationTypeEnum.PARENT_CHILD.getCode());
            relation.setCreateTime(LocalDateTime.now());
            relation.setUpdateTime(LocalDateTime.now());
            familyRelationRepository.save(relation);
        }

        // 建立夫妻关系（需先校验：禁止自身、重复、直系血亲、同胞）
        if (dto.getSpouseNodeId() != null) {
            List<FamilyRelation> existingRelations = familyRelationRepository.findByNodeId(
                    familyId, node.getId());
            List<FamilyRelation> allRelations = familyRelationRepository.findByFamilyId(familyId);
            relationValidationDomainService.validateSpouseRelation(
                    familyId, dto.getSpouseNodeId(), node.getId(),
                    existingRelations, allRelations);
            FamilyRelation relation = new FamilyRelation();
            relation.setUserId(userId);
            relation.setFamilyId(familyId);
            relation.setFromNodeId(dto.getSpouseNodeId());
            relation.setToNodeId(node.getId());
            relation.setRelationType(RelationTypeEnum.SPOUSE.getCode());
            relation.setCreateTime(LocalDateTime.now());
            relation.setUpdateTime(LocalDateTime.now());
            familyRelationRepository.save(relation);
        }

        // 新增父节点：当前节点为父，childNodeId 为子
        if (dto.getChildNodeId() != null) {
            FamilyRelation relation = new FamilyRelation();
            relation.setUserId(userId);
            relation.setFamilyId(familyId);
            relation.setFromNodeId(node.getId());
            relation.setToNodeId(dto.getChildNodeId());
            relation.setRelationType(RelationTypeEnum.PARENT_CHILD.getCode());
            relation.setCreateTime(LocalDateTime.now());
            relation.setUpdateTime(LocalDateTime.now());
            familyRelationRepository.save(relation);
        }

        // 树结构已变更，失效该家族的族谱树缓存（事务提交后生效）
        eventPublisher.publishEvent(NodeCreatedEvent.of(familyId, node.getId(), node.getName(), userId));
        eventPublisher.publishEvent(FamilyTreeUpdatedEvent.of(familyId));

        return node.getId();
    }

    /**
     * 更新族谱节点
     *
     * @param familyId 家族ID
     * @param dto      更新请求
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateNode(Long familyId, FamilyNodeDTO dto) {
        FamilyNode existing = checkNodeBelongsToFamily(familyId, dto.getId());

        if (dto.getName() != null && !dto.getName().isBlank()) {
            if (dto.getName().trim().length() > FamilyTreeConsts.MAX_NAME_LENGTH) {
                throw new BusinessException("节点名称不能超过" + FamilyTreeConsts.MAX_NAME_LENGTH + "个字符");
            }
            existing.setName(dto.getName().trim());
        }
        if (dto.getGender() != null) {
            if (dto.getGender() < 0 || dto.getGender() > 2) {
                throw new BusinessException("性别值无效，应为 0（未知）、1（男）或 2（女）");
            }
            existing.setGender(dto.getGender());
        }
        if (dto.getColorLabel() != null) {
            ColorLabelEnum.validateCode(dto.getColorLabel());
            existing.setColorLabel(dto.getColorLabel());
        }
        if (dto.getBirthOrder() != null) {
            if (dto.getBirthOrder() < 1) {
                throw new BusinessException("排次不能小于1");
            }
            existing.setBirthOrder(dto.getBirthOrder());
        }
        if (dto.getGeneration() != null) {
            boolean generationChanged = !dto.getGeneration().equals(existing.getGeneration());
            existing.setGeneration(dto.getGeneration());
            if (generationChanged) {
                syncSpouseGeneration(familyId, existing.getId(), dto.getGeneration());
                syncDescendantGenerations(familyId, existing.getId(), dto.getGeneration());
            }
        }
        if (dto.getAvatar() != null) {
            existing.setAvatar(dto.getAvatar());
        }
        if (dto.getRemark() != null) {
            if (dto.getRemark().length() > FamilyTreeConsts.MAX_REMARK_LENGTH) {
                throw new BusinessException("备注不能超过"
                        + FamilyTreeConsts.MAX_REMARK_LENGTH + "个字符");
            }
            existing.setRemark(dto.getRemark());
        }

        // 传统族谱字段更新
        if (dto.getLunarBirthDate() != null) {
            existing.setLunarBirthDate(dto.getLunarBirthDate());
        }
        if (dto.getLunarDeathDate() != null) {
            existing.setLunarDeathDate(dto.getLunarDeathDate());
        }
        if (dto.getZi() != null) {
            existing.setZi(dto.getZi());
        }
        if (dto.getHao() != null) {
            existing.setHao(dto.getHao());
        }
        if (dto.getHui() != null) {
            existing.setHui(dto.getHui());
        }
        if (dto.getGraveLocation() != null) {
            existing.setGraveLocation(dto.getGraveLocation());
        }
        if (dto.getSpouseName() != null) {
            existing.setSpouseName(dto.getSpouseName());
        }
        if (dto.getSpouseOriginFamily() != null) {
            existing.setSpouseOriginFamily(dto.getSpouseOriginFamily());
        }

        // 日期字段需支持显式清除（传空字符串表示清空）
        if (dto.getBirthDate() != null) {
            existing.setBirthDate(
                    dto.getBirthDate().isBlank() ? null : parseDate(dto.getBirthDate()));
        }
        if (dto.getDeathDate() != null) {
            existing.setDeathDate(
                    dto.getDeathDate().isBlank() ? null : parseDate(dto.getDeathDate()));
        }

        existing.setUpdateTime(LocalDateTime.now());
        familyNodeRepository.update(existing);

        // 生卒日期顺序校验（取更新后的有效值）
        LocalDate effectiveBirth = existing.getBirthDate();
        LocalDate effectiveDeath = existing.getDeathDate();
        if (effectiveBirth != null && effectiveDeath != null
                && effectiveDeath.isBefore(effectiveBirth)) {
            throw new BusinessException("去世日期不能早于出生日期");
        }

        eventPublisher.publishEvent(FamilyTreeUpdatedEvent.of(familyId));
    }

    /**
     * 删除族谱节点
     *
     * @param familyId 家族ID
     * @param nodeId   节点ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteNode(Long familyId, Long nodeId) {
        checkNodeBelongsToFamily(familyId, nodeId);

        familyRelationRepository.removeByNodeId(familyId, nodeId);
        familyNodeRepository.removeById(nodeId);
        logger.info("Deleted family node id={} for family={}", nodeId, familyId);

        eventPublisher.publishEvent(FamilyTreeUpdatedEvent.of(familyId));
    }

    /**
     * 获取单个节点
     *
     * @param familyId 家族ID
     * @param nodeId   节点ID
     * @return 节点 DTO
     */
    public FamilyNodeDTO getNode(Long familyId, Long nodeId) {
        FamilyNode node = checkNodeBelongsToFamily(familyId, nodeId);
        return toDTO(node);
    }

    /**
     * 列出家族所有节点
     *
     * @param familyId 家族ID
     * @return 节点列表
     */
    public List<FamilyNodeDTO> listNodes(Long familyId) {
        List<FamilyNode> nodes = familyNodeRepository.findByFamilyId(familyId);
        return nodes.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 搜索节点
     *
     * @param familyId 家族ID
     * @param keyword  搜索关键词
     * @return 匹配的节点列表
     */
    public List<FamilyNodeDTO> searchNodes(Long familyId, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        List<FamilyNode> nodes = familyNodeRepository.findByFamilyIdAndNameContaining(familyId, keyword.trim());
        return nodes.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 批量更新节点颜色
     *
     * @param familyId   家族ID
     * @param nodeIds    节点ID列表
     * @param colorLabel 颜色标签
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateColor(Long familyId, List<Long> nodeIds, String colorLabel) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            throw new BusinessException("节点ID列表不能为空");
        }
        ColorLabelEnum.validateCode(colorLabel);

        // 批量校验归属
        List<FamilyNode> nodes = familyNodeRepository.findByIds(nodeIds);
        long validCount = nodes.stream()
                .filter(n -> Objects.equals(n.getFamilyId(), familyId))
                .count();
        if (validCount != nodeIds.size()) {
            throw new BusinessException("节点不存在或无权操作");
        }

        familyNodeRepository.updateColorLabel(familyId, nodeIds, colorLabel);
        logger.info("Updated colorLabel={} for {} nodes for family={}",
                colorLabel, nodeIds.size(), familyId);

        eventPublisher.publishEvent(FamilyTreeUpdatedEvent.of(familyId));
    }

    /**
     * 同步后代节点世代层级
     *
     * @param familyId   家族ID
     * @param nodeId     起始节点ID
     * @param generation 目标世代
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncDescendantGenerations(Long familyId, Long nodeId, Integer generation) {
        List<FamilyNode> allNodes = familyNodeRepository.findByFamilyId(familyId);
        List<FamilyRelation> allRelations = familyRelationRepository.findByFamilyId(familyId);
        List<FamilyNode> updatedNodes = familyNodeDomainService.syncDescendantGenerations(
                nodeId, generation, allNodes, allRelations);
        for (FamilyNode node : updatedNodes) {
            familyNodeRepository.update(node);
        }
        logger.info("Synced descendant generations from node={} gen={} for family={} ({} nodes updated)",
                nodeId, generation, familyId, updatedNodes.size());

        eventPublisher.publishEvent(FamilyTreeUpdatedEvent.of(familyId));
    }

    /**
     * 将指定节点的所有配偶的辈分同步为与该节点一致
     */
    private void syncSpouseGeneration(Long familyId, Long nodeId, Integer generation) {
        List<FamilyRelation> spouseRelations = familyRelationRepository.findSpouseRelations(
                familyId, nodeId);
        for (FamilyRelation relation : spouseRelations) {
            Long spouseId = Objects.equals(relation.getFromNodeId(), nodeId)
                    ? relation.getToNodeId() : relation.getFromNodeId();
            FamilyNode spouse = familyNodeRepository.findById(spouseId);
            if (spouse != null && Objects.equals(spouse.getFamilyId(), familyId)) {
                spouse.setGeneration(generation);
                spouse.setUpdateTime(LocalDateTime.now());
                familyNodeRepository.update(spouse);
            }
        }
        if (!spouseRelations.isEmpty()) {
            logger.info("Synced generation={} to {} spouse(s) of node={} for family={}",
                    generation, spouseRelations.size(), nodeId, familyId);
        }
    }

    /**
     * 校验节点存在且属于指定家族
     */
    private FamilyNode checkNodeBelongsToFamily(Long familyId, Long nodeId) {
        FamilyNode node = familyNodeRepository.findById(nodeId);
        if (node == null || !Objects.equals(node.getFamilyId(), familyId)) {
            throw new BusinessException("节点不存在或无权操作");
        }
        return node;
    }

    private int countChildren(Long familyId, Long parentNodeId) {
        return (int) familyRelationRepository.countChildren(familyId, parentNodeId);
    }

    private FamilyNodeDTO toDTO(FamilyNode entity) {
        FamilyNodeDTO dto = new FamilyNodeDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setGender(entity.getGender());
        dto.setBirthDate(entity.getBirthDate() != null ? entity.getBirthDate().toString() : null);
        dto.setDeathDate(entity.getDeathDate() != null ? entity.getDeathDate().toString() : null);
        dto.setGeneration(entity.getGeneration());
        dto.setBirthOrder(entity.getBirthOrder());
        dto.setColorLabel(entity.getColorLabel());
        dto.setAvatar(entity.getAvatar());
        dto.setRemark(entity.getRemark());
        dto.setLunarBirthDate(entity.getLunarBirthDate());
        dto.setLunarDeathDate(entity.getLunarDeathDate());
        dto.setZi(entity.getZi());
        dto.setHao(entity.getHao());
        dto.setHui(entity.getHui());
        dto.setGraveLocation(entity.getGraveLocation());
        dto.setSpouseName(entity.getSpouseName());
        dto.setSpouseOriginFamily(entity.getSpouseOriginFamily());
        return dto;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            throw new BusinessException("日期格式错误，应为 yyyy-MM-dd");
        }
    }
}
