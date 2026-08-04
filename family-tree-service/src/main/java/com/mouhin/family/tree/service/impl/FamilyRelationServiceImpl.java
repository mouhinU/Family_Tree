package com.mouhin.family.tree.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mouhin.family.tree.common.dto.FamilyRelationDTO;
import com.mouhin.family.tree.common.enums.RelationTypeEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.persistence.entity.FamilyNodeDO;
import com.mouhin.family.tree.persistence.entity.FamilyRelationDO;
import com.mouhin.family.tree.persistence.mapper.FamilyNodeMapper;
import com.mouhin.family.tree.persistence.mapper.FamilyRelationMapper;
import com.mouhin.family.tree.service.FamilyNodeService;
import com.mouhin.family.tree.service.FamilyRelationService;
import com.mouhin.family.tree.service.FamilyTreeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 族谱关系服务实现类
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@Service
public class FamilyRelationServiceImpl implements FamilyRelationService {

    private static final Logger logger = LoggerFactory.getLogger(FamilyRelationServiceImpl.class);

    private final FamilyRelationMapper familyRelationMapper;
    private final FamilyNodeMapper familyNodeMapper;
    private final FamilyNodeService familyNodeService;
    private final FamilyTreeService familyTreeService;

    public FamilyRelationServiceImpl(FamilyRelationMapper familyRelationMapper,
                                     FamilyNodeMapper familyNodeMapper,
                                     @Lazy FamilyNodeService familyNodeService,
                                     FamilyTreeService familyTreeService) {
        this.familyRelationMapper = familyRelationMapper;
        this.familyNodeMapper = familyNodeMapper;
        this.familyNodeService = familyNodeService;
        this.familyTreeService = familyTreeService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRelation(Long familyId, Long userId, FamilyRelationDTO dto) {
        checkNodesBelongToFamily(familyId, dto.getFromNodeId(), dto.getToNodeId());

        // 自身校验：不允许自己与自己建立关系
        if (Objects.equals(dto.getFromNodeId(), dto.getToNodeId())) {
            throw new BusinessException("不能与自身建立关系");
        }

        if (Objects.equals(dto.getRelationType(), RelationTypeEnum.SPOUSE.getCode())) {
            validateSpouseRelation(familyId, dto.getFromNodeId(), dto.getToNodeId());
        }

        // 检查是否已存在相同关系（含反向，防止 A→B 与 B→A 同时存在）
        LambdaQueryWrapper<FamilyRelationDO> existQuery = new LambdaQueryWrapper<>();
        existQuery.eq(FamilyRelationDO::getFamilyId, familyId)
                .eq(FamilyRelationDO::getRelationType, dto.getRelationType())
                .and(w -> w.and(w1 -> w1.eq(FamilyRelationDO::getFromNodeId, dto.getFromNodeId())
                                .eq(FamilyRelationDO::getToNodeId, dto.getToNodeId()))
                        .or(w2 -> w2.eq(FamilyRelationDO::getFromNodeId, dto.getToNodeId())
                                .eq(FamilyRelationDO::getToNodeId, dto.getFromNodeId())));
        if (familyRelationMapper.selectCount(existQuery) > 0) {
            throw new BusinessException("该关系已存在");
        }

        // 婚离日期顺序校验
        if (dto.getMarriageDate() != null && dto.getDivorceDate() != null
                && dto.getDivorceDate().isBefore(dto.getMarriageDate())) {
            throw new BusinessException("离异日期不能早于结婚日期");
        }

        // 亲子关系需更新子节点的 generation，并递归同步其后代
        if (Objects.equals(dto.getRelationType(), RelationTypeEnum.PARENT_CHILD.getCode())) {
            FamilyNodeDO parent = familyNodeMapper.selectById(dto.getFromNodeId());
            FamilyNodeDO child = familyNodeMapper.selectById(dto.getToNodeId());
            if (parent != null && child != null) {
                int childGen = parent.getGeneration() + 1;
                child.setGeneration(childGen);
                child.setUpdateTime(LocalDateTime.now());
                familyNodeMapper.updateById(child);
                familyNodeService.syncDescendantGenerations(familyId, child.getId(), childGen);
            }
        }

        // 过继/收养关系：与亲子关系类似，更新养子女世代
        if (Objects.equals(dto.getRelationType(), RelationTypeEnum.ADOPTION.getCode())) {
            FamilyNodeDO parent = familyNodeMapper.selectById(dto.getFromNodeId());
            FamilyNodeDO child = familyNodeMapper.selectById(dto.getToNodeId());
            if (parent != null && child != null) {
                int childGen = parent.getGeneration() + 1;
                child.setGeneration(childGen);
                child.setUpdateTime(LocalDateTime.now());
                familyNodeMapper.updateById(child);
                familyNodeService.syncDescendantGenerations(familyId, child.getId(), childGen);
            }
        }

        // 夫妻关系需同步双方世代
        if (Objects.equals(dto.getRelationType(), RelationTypeEnum.SPOUSE.getCode())) {
            FamilyNodeDO fromNode = familyNodeMapper.selectById(dto.getFromNodeId());
            FamilyNodeDO toNode = familyNodeMapper.selectById(dto.getToNodeId());
            if (fromNode != null && toNode != null
                    && !Objects.equals(fromNode.getGeneration(), toNode.getGeneration())) {
                toNode.setGeneration(fromNode.getGeneration());
                toNode.setUpdateTime(LocalDateTime.now());
                familyNodeMapper.updateById(toNode);
            }
        }

        FamilyRelationDO relation = new FamilyRelationDO();
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
        familyRelationMapper.insert(relation);

        logger.info("Created relation id={} type={} from={} to={} for family={} by user={}",
                relation.getId(), dto.getRelationType(), dto.getFromNodeId(), dto.getToNodeId(), familyId, userId);

        familyTreeService.evictFamilyTree(familyId);

        return relation.getId();
    }

    @Override
    public void deleteRelation(Long familyId, Long relationId) {
        FamilyRelationDO relation = familyRelationMapper.selectById(relationId);
        if (relation == null || !Objects.equals(relation.getFamilyId(), familyId)) {
            throw new BusinessException("关系不存在或无权操作");
        }
        familyRelationMapper.deleteById(relationId);
        logger.info("Deleted relation id={} for family={}", relationId, familyId);

        familyTreeService.evictFamilyTree(familyId);
    }

    @Override
    public void updateRelation(Long familyId, FamilyRelationDTO dto) {
        FamilyRelationDO relation = familyRelationMapper.selectById(dto.getId());
        if (relation == null || !Objects.equals(relation.getFamilyId(), familyId)) {
            throw new BusinessException("关系不存在或无权操作");
        }
        // 婚离日期顺序校验
        if (dto.getMarriageDate() != null && dto.getDivorceDate() != null
                && dto.getDivorceDate().isBefore(dto.getMarriageDate())) {
            throw new BusinessException("离异日期不能早于结婚日期");
        }
        LambdaUpdateWrapper<FamilyRelationDO> update = new LambdaUpdateWrapper<>();
        update.eq(FamilyRelationDO::getId, dto.getId())
                .set(FamilyRelationDO::getMarriageDate, dto.getMarriageDate())
                .set(FamilyRelationDO::getDivorceDate, dto.getDivorceDate())
                .set(FamilyRelationDO::getUpdateTime, LocalDateTime.now());
        if (dto.getDivorced() != null) {
            update.set(FamilyRelationDO::getDivorced, dto.getDivorced());
        }
        if (dto.getWidowed() != null) {
            update.set(FamilyRelationDO::getWidowed, dto.getWidowed());
        }
        if (dto.getMarriageOrder() != null) {
            update.set(FamilyRelationDO::getMarriageOrder, dto.getMarriageOrder());
        }
        if (dto.getEndType() != null) {
            update.set(FamilyRelationDO::getEndType, dto.getEndType());
        }
        familyRelationMapper.update(null, update);
        logger.info("Updated relation id={} divorced={} widowed={} for family={}",
                dto.getId(), dto.getDivorced(), dto.getWidowed(), familyId);

        familyTreeService.evictFamilyTree(familyId);
    }

    @Override
    public List<FamilyRelationDTO> listRelationsByNode(Long familyId, Long nodeId) {
        LambdaQueryWrapper<FamilyRelationDO> query = new LambdaQueryWrapper<>();
        query.eq(FamilyRelationDO::getFamilyId, familyId)
                .and(w -> w.eq(FamilyRelationDO::getFromNodeId, nodeId)
                        .or().eq(FamilyRelationDO::getToNodeId, nodeId));
        return familyRelationMapper.selectList(query).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<FamilyRelationDTO> listAllRelations(Long familyId) {
        LambdaQueryWrapper<FamilyRelationDO> query = new LambdaQueryWrapper<>();
        query.eq(FamilyRelationDO::getFamilyId, familyId);
        return familyRelationMapper.selectList(query).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 校验两个节点是否都属于指定家族
     */
    private void checkNodesBelongToFamily(Long familyId, Long fromNodeId, Long toNodeId) {
        FamilyNodeDO fromNode = familyNodeMapper.selectById(fromNodeId);
        FamilyNodeDO toNode = familyNodeMapper.selectById(toNodeId);
        if (fromNode == null || !Objects.equals(fromNode.getFamilyId(), familyId)) {
            throw new BusinessException("起始节点不存在或无权操作");
        }
        if (toNode == null || !Objects.equals(toNode.getFamilyId(), familyId)) {
            throw new BusinessException("目标节点不存在或无权操作");
        }
    }

    /**
     * 夫妻关系合法性校验。
     * 允许表（堂）兄妹等旁系血亲结婚；禁止自身、重复（含反向）、直系血亲与同胞。
     */
    @Override
    public void validateSpouseRelation(Long familyId, Long fromNodeId, Long toNodeId) {
        if (Objects.equals(fromNodeId, toNodeId)) {
            throw new BusinessException("不能与自身建立夫妻关系");
        }

        // 重复校验（含反向）
        LambdaQueryWrapper<FamilyRelationDO> duplicateQuery = new LambdaQueryWrapper<>();
        duplicateQuery.eq(FamilyRelationDO::getFamilyId, familyId)
                .eq(FamilyRelationDO::getRelationType, RelationTypeEnum.SPOUSE.getCode())
                .and(w -> w.and(w1 -> w1.eq(FamilyRelationDO::getFromNodeId, fromNodeId)
                                .eq(FamilyRelationDO::getToNodeId, toNodeId))
                        .or(w2 -> w2.eq(FamilyRelationDO::getFromNodeId, toNodeId)
                                .eq(FamilyRelationDO::getToNodeId, fromNodeId)));
        if (familyRelationMapper.selectCount(duplicateQuery) > 0) {
            throw new BusinessException("该夫妻关系已存在");
        }

        // 直系血亲禁止结婚
        LambdaQueryWrapper<FamilyRelationDO> directQuery = new LambdaQueryWrapper<>();
        directQuery.eq(FamilyRelationDO::getFamilyId, familyId)
                .eq(FamilyRelationDO::getRelationType, RelationTypeEnum.PARENT_CHILD.getCode())
                .and(w -> w.and(w1 -> w1.eq(FamilyRelationDO::getFromNodeId, fromNodeId)
                                .eq(FamilyRelationDO::getToNodeId, toNodeId))
                        .or(w2 -> w2.eq(FamilyRelationDO::getFromNodeId, toNodeId)
                                .eq(FamilyRelationDO::getToNodeId, fromNodeId)));
        if (familyRelationMapper.selectCount(directQuery) > 0) {
            throw new BusinessException("直系血亲不能建立夫妻关系");
        }

        // 同胞（存在共同父或母）禁止结婚
        Set<Long> fromParents = listParentIds(familyId, fromNodeId);
        Set<Long> toParents = listParentIds(familyId, toNodeId);
        fromParents.retainAll(toParents);
        if (!fromParents.isEmpty()) {
            throw new BusinessException("同胞兄妹不能建立夫妻关系");
        }

        // 重婚校验：双方均不得已有未终止的婚姻（离异或丧偶视为已终止）
        LambdaQueryWrapper<FamilyRelationDO> activeMarriageQuery = new LambdaQueryWrapper<>();
        activeMarriageQuery.eq(FamilyRelationDO::getFamilyId, familyId)
                .eq(FamilyRelationDO::getRelationType, RelationTypeEnum.SPOUSE.getCode())
                .and(w -> w.eq(FamilyRelationDO::getFromNodeId, fromNodeId)
                        .or().eq(FamilyRelationDO::getToNodeId, fromNodeId))
                .and(w -> w.ne(FamilyRelationDO::getDivorced, Boolean.TRUE)
                        .and(w2 -> w2.ne(FamilyRelationDO::getWidowed, Boolean.TRUE)));
        if (familyRelationMapper.selectCount(activeMarriageQuery) > 0) {
            throw new BusinessException("该成员已有在婚配偶，不能重复建立夫妻关系");
        }
        LambdaQueryWrapper<FamilyRelationDO> activeMarriageQuery2 = new LambdaQueryWrapper<>();
        activeMarriageQuery2.eq(FamilyRelationDO::getFamilyId, familyId)
                .eq(FamilyRelationDO::getRelationType, RelationTypeEnum.SPOUSE.getCode())
                .and(w -> w.eq(FamilyRelationDO::getFromNodeId, toNodeId)
                        .or().eq(FamilyRelationDO::getToNodeId, toNodeId))
                .and(w -> w.ne(FamilyRelationDO::getDivorced, Boolean.TRUE)
                        .and(w2 -> w2.ne(FamilyRelationDO::getWidowed, Boolean.TRUE)));
        if (familyRelationMapper.selectCount(activeMarriageQuery2) > 0) {
            throw new BusinessException("该成员已有在婚配偶，不能重复建立夫妻关系");
        }
    }

    private Set<Long> listParentIds(Long familyId, Long nodeId) {
        LambdaQueryWrapper<FamilyRelationDO> query = new LambdaQueryWrapper<>();
        query.eq(FamilyRelationDO::getFamilyId, familyId)
                .eq(FamilyRelationDO::getRelationType, RelationTypeEnum.PARENT_CHILD.getCode())
                .eq(FamilyRelationDO::getToNodeId, nodeId);
        return familyRelationMapper.selectList(query).stream()
                .map(FamilyRelationDO::getFromNodeId)
                .collect(Collectors.toSet());
    }

    private FamilyRelationDTO toDTO(FamilyRelationDO entity) {
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
