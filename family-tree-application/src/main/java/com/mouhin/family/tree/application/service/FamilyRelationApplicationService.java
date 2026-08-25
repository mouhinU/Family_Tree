package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.FamilyRelationDTO;
import com.mouhin.family.tree.common.enums.RelationTypeEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyRelation;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.FamilyRelationRepository;
import com.mouhin.family.tree.domain.service.RelationValidationDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 族谱关系应用服务
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Service
public class FamilyRelationApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(FamilyRelationApplicationService.class);

    private final FamilyRelationRepository familyRelationRepository;
    private final FamilyNodeRepository familyNodeRepository;
    private final RelationValidationDomainService relationValidationDomainService;
    private final FamilyNodeApplicationService familyNodeApplicationService;
    private final FamilyTreeApplicationService familyTreeApplicationService;

    public FamilyRelationApplicationService(FamilyRelationRepository familyRelationRepository,
                                            FamilyNodeRepository familyNodeRepository,
                                            RelationValidationDomainService relationValidationDomainService,
                                            FamilyNodeApplicationService familyNodeApplicationService,
                                            FamilyTreeApplicationService familyTreeApplicationService) {
        this.familyRelationRepository = familyRelationRepository;
        this.familyNodeRepository = familyNodeRepository;
        this.relationValidationDomainService = relationValidationDomainService;
        this.familyNodeApplicationService = familyNodeApplicationService;
        this.familyTreeApplicationService = familyTreeApplicationService;
    }

    /**
     * 创建关系
     *
     * @param familyId 家族ID
     * @param userId   操作者用户ID
     * @param dto      关系数据
     * @return 新关系ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createRelation(Long familyId, Long userId, FamilyRelationDTO dto) {
        checkNodesBelongToFamily(familyId, dto.getFromNodeId(), dto.getToNodeId());

        // 自身校验：不允许自己与自己建立关系
        if (Objects.equals(dto.getFromNodeId(), dto.getToNodeId())) {
            throw new BusinessException("不能与自身建立关系");
        }

        if (Objects.equals(dto.getRelationType(), RelationTypeEnum.SPOUSE.getCode())) {
            List<FamilyRelation> existingRelations = familyRelationRepository.findByNodeId(
                    familyId, dto.getFromNodeId());
            List<FamilyRelation> allRelations = familyRelationRepository.findByFamilyId(familyId);
            relationValidationDomainService.validateSpouseRelation(
                    familyId, dto.getFromNodeId(), dto.getToNodeId(),
                    existingRelations, allRelations);
        }

        // 检查是否已存在相同关系（含反向，防止 A->B 与 B->A 同时存在）
        if (familyRelationRepository.existsRelation(
                familyId, dto.getFromNodeId(), dto.getToNodeId(), dto.getRelationType())) {
            throw new BusinessException("该关系已存在");
        }

        // 婚离日期顺序校验
        if (dto.getMarriageDate() != null && dto.getDivorceDate() != null
                && dto.getDivorceDate().isBefore(dto.getMarriageDate())) {
            throw new BusinessException("离异日期不能早于结婚日期");
        }

        // 亲子关系需更新子节点的 generation，并递归同步其后代
        if (Objects.equals(dto.getRelationType(), RelationTypeEnum.PARENT_CHILD.getCode())) {
            syncChildGeneration(familyId, dto.getFromNodeId(), dto.getToNodeId());
        }

        // 过继/收养关系：与亲子关系类似，更新养子女世代
        if (Objects.equals(dto.getRelationType(), RelationTypeEnum.ADOPTION.getCode())) {
            syncChildGeneration(familyId, dto.getFromNodeId(), dto.getToNodeId());
        }

        // 夫妻关系需同步双方世代
        if (Objects.equals(dto.getRelationType(), RelationTypeEnum.SPOUSE.getCode())) {
            FamilyNode fromNode = familyNodeRepository.findById(dto.getFromNodeId());
            FamilyNode toNode = familyNodeRepository.findById(dto.getToNodeId());
            if (fromNode != null && toNode != null
                    && !Objects.equals(fromNode.getGeneration(), toNode.getGeneration())) {
                toNode.setGeneration(fromNode.getGeneration());
                toNode.setUpdateTime(LocalDateTime.now());
                familyNodeRepository.save(toNode);
            }
        }

        FamilyRelation relation = new FamilyRelation();
        relation.setUserId(userId);
        relation.setFamilyId(familyId);
        relation.setFromNodeId(dto.getFromNodeId());
        relation.setToNodeId(dto.getToNodeId());
        relation.setRelationType(dto.getRelationType());
        relation.setMarriageDate(dto.getMarriageDate());
        relation.setDivorceDate(dto.getDivorceDate());
        relation.setMarriageOrder(dto.getMarriageOrder());
        relation.setEndType(dto.getEndType());
        relation.setCreateTime(LocalDateTime.now());
        relation.setUpdateTime(LocalDateTime.now());
        familyRelationRepository.save(relation);

        logger.info("Created relation id={} type={} from={} to={} for family={} by user={}",
                relation.getId(), dto.getRelationType(),
                dto.getFromNodeId(), dto.getToNodeId(), familyId, userId);

        familyTreeApplicationService.evictFamilyTree(familyId);

        return relation.getId();
    }

    /**
     * 删除关系
     *
     * @param familyId   家族ID
     * @param relationId 关系ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRelation(Long familyId, Long relationId) {
        FamilyRelation relation = familyRelationRepository.findById(relationId);
        if (relation == null || !Objects.equals(relation.getFamilyId(), familyId)) {
            throw new BusinessException("关系不存在或无权操作");
        }
        familyRelationRepository.removeById(relationId);
        logger.info("Deleted relation id={} for family={}", relationId, familyId);

        familyTreeApplicationService.evictFamilyTree(familyId);
    }

    /**
     * 更新关系
     *
     * @param familyId 家族ID
     * @param dto      关系数据
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateRelation(Long familyId, FamilyRelationDTO dto) {
        FamilyRelation relation = familyRelationRepository.findById(dto.getId());
        if (relation == null || !Objects.equals(relation.getFamilyId(), familyId)) {
            throw new BusinessException("关系不存在或无权操作");
        }

        // 婚离日期顺序校验
        if (dto.getMarriageDate() != null && dto.getDivorceDate() != null
                && dto.getDivorceDate().isBefore(dto.getMarriageDate())) {
            throw new BusinessException("离异日期不能早于结婚日期");
        }

        if (dto.getMarriageDate() != null) {
            relation.setMarriageDate(dto.getMarriageDate());
        }
        if (dto.getDivorceDate() != null) {
            relation.setDivorceDate(dto.getDivorceDate());
        }
        if (dto.getDivorced() != null) {
            relation.setDivorced(dto.getDivorced());
        }
        if (dto.getWidowed() != null) {
            relation.setWidowed(dto.getWidowed());
        }
        if (dto.getMarriageOrder() != null) {
            relation.setMarriageOrder(dto.getMarriageOrder());
        }
        if (dto.getEndType() != null) {
            relation.setEndType(dto.getEndType());
        }
        relation.setUpdateTime(LocalDateTime.now());
        familyRelationRepository.save(relation);

        logger.info("Updated relation id={} divorced={} widowed={} for family={}",
                dto.getId(), dto.getDivorced(), dto.getWidowed(), familyId);

        familyTreeApplicationService.evictFamilyTree(familyId);
    }

    /**
     * 列出节点的所有关系
     *
     * @param familyId 家族ID
     * @param nodeId   节点ID
     * @return 关系列表
     */
    public List<FamilyRelationDTO> listRelationsByNode(Long familyId, Long nodeId) {
        List<FamilyRelation> relations = familyRelationRepository.findByNodeId(familyId, nodeId);
        return relations.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 列出家族所有关系
     *
     * @param familyId 家族ID
     * @return 关系列表
     */
    public List<FamilyRelationDTO> listAllRelations(Long familyId) {
        List<FamilyRelation> relations = familyRelationRepository.findByFamilyId(familyId);
        return relations.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 同步子节点世代（亲子关系/收养关系创建时调用）
     */
    private void syncChildGeneration(Long familyId, Long fromNodeId, Long toNodeId) {
        FamilyNode parent = familyNodeRepository.findById(fromNodeId);
        FamilyNode child = familyNodeRepository.findById(toNodeId);
        if (parent != null && child != null) {
            int childGen = parent.getGeneration() + 1;
            child.setGeneration(childGen);
            child.setUpdateTime(LocalDateTime.now());
            familyNodeRepository.save(child);
            familyNodeApplicationService.syncDescendantGenerations(
                    familyId, child.getId(), childGen);
        }
    }

    /**
     * 校验两个节点是否都属于指定家族
     */
    private void checkNodesBelongToFamily(Long familyId, Long fromNodeId, Long toNodeId) {
        FamilyNode fromNode = familyNodeRepository.findById(fromNodeId);
        FamilyNode toNode = familyNodeRepository.findById(toNodeId);
        if (fromNode == null || !Objects.equals(fromNode.getFamilyId(), familyId)) {
            throw new BusinessException("起始节点不存在或无权操作");
        }
        if (toNode == null || !Objects.equals(toNode.getFamilyId(), familyId)) {
            throw new BusinessException("目标节点不存在或无权操作");
        }
    }

    private FamilyRelationDTO toDTO(FamilyRelation entity) {
        FamilyRelationDTO dto = new FamilyRelationDTO();
        dto.setId(entity.getId());
        dto.setFromNodeId(entity.getFromNodeId());
        dto.setToNodeId(entity.getToNodeId());
        dto.setRelationType(entity.getRelationType());
        dto.setMarriageDate(entity.getMarriageDate());
        dto.setDivorceDate(entity.getDivorceDate());
        dto.setDivorced(entity.getDivorced());
        dto.setWidowed(entity.getWidowed());
        dto.setMarriageOrder(entity.getMarriageOrder());
        dto.setEndType(entity.getEndType());
        return dto;
    }
}
