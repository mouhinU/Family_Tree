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
    public Long createRelation(Long userId, FamilyRelationDTO dto) {
        validateNodesOwnership(userId, dto.getFromNodeId(), dto.getToNodeId());

        // 夫妻关系需额外校验：禁止自身、重复（含反向）、直系血亲与同胞
        if (Objects.equals(dto.getRelationType(), RelationTypeEnum.SPOUSE.getCode())) {
            validateSpouseRelation(userId, dto.getFromNodeId(), dto.getToNodeId());
        }

        // 检查是否已存在相同关系
        LambdaQueryWrapper<FamilyRelationDO> existQuery = new LambdaQueryWrapper<>();
        existQuery.eq(FamilyRelationDO::getUserId, userId)
                .eq(FamilyRelationDO::getFromNodeId, dto.getFromNodeId())
                .eq(FamilyRelationDO::getToNodeId, dto.getToNodeId())
                .eq(FamilyRelationDO::getRelationType, dto.getRelationType());
        if (familyRelationMapper.selectCount(existQuery) > 0) {
            throw new BusinessException("该关系已存在");
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
                // 递归同步子节点的所有后代世代
                familyNodeService.syncDescendantGenerations(userId, child.getId(), childGen);
            }
        }

        FamilyRelationDO relation = new FamilyRelationDO();
        relation.setUserId(userId);
        relation.setFromNodeId(dto.getFromNodeId());
        relation.setToNodeId(dto.getToNodeId());
        relation.setRelationType(dto.getRelationType());
        relation.setMarriageDate(dto.getMarriageDate());
        relation.setDivorceDate(dto.getDivorceDate());
        relation.setCreateTime(LocalDateTime.now());
        relation.setUpdateTime(LocalDateTime.now());
        familyRelationMapper.insert(relation);

        logger.info("Created relation id={} type={} from={} to={} for user={}",
                relation.getId(), dto.getRelationType(), dto.getFromNodeId(), dto.getToNodeId(), userId);

        // 树结构已变更，失效该用户的族谱树缓存（事务提交后生效）
        familyTreeService.evictUserTree(userId);

        return relation.getId();
    }

    @Override
    public void deleteRelation(Long userId, Long relationId) {
        FamilyRelationDO relation = familyRelationMapper.selectById(relationId);
        if (relation == null || !Objects.equals(relation.getUserId(), userId)) {
            throw new BusinessException("关系不存在或无权操作");
        }
        familyRelationMapper.deleteById(relationId);
        logger.info("Deleted relation id={} for user={}", relationId, userId);

        // 树结构已变更，失效该用户的族谱树缓存
        familyTreeService.evictUserTree(userId);
    }

    @Override
    public void updateRelation(Long userId, FamilyRelationDTO dto) {
        FamilyRelationDO relation = familyRelationMapper.selectById(dto.getId());
        if (relation == null || !Objects.equals(relation.getUserId(), userId)) {
            throw new BusinessException("关系不存在或无权操作");
        }
        // 结婚日期与离异日期均为非必填，须显式置空。
        // updateById 默认忽略 null 字段，无法清除已有日期，故改用 UpdateWrapper。
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
        familyRelationMapper.update(null, update);
        logger.info("Updated relation id={} divorced={} widowed={} for user={}",
                dto.getId(), dto.getDivorced(), dto.getWidowed(), userId);

        // 婚姻状态已变更，失效该用户的族谱树缓存（离异/丧偶影响配偶挂载与渲染）
        familyTreeService.evictUserTree(userId);
    }

    @Override
    public List<FamilyRelationDTO> listRelationsByNode(Long userId, Long nodeId) {
        LambdaQueryWrapper<FamilyRelationDO> query = new LambdaQueryWrapper<>();
        query.eq(FamilyRelationDO::getUserId, userId)
                .and(w -> w.eq(FamilyRelationDO::getFromNodeId, nodeId)
                        .or().eq(FamilyRelationDO::getToNodeId, nodeId));
        return familyRelationMapper.selectList(query).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<FamilyRelationDTO> listAllRelations(Long userId) {
        LambdaQueryWrapper<FamilyRelationDO> query = new LambdaQueryWrapper<>();
        query.eq(FamilyRelationDO::getUserId, userId);
        return familyRelationMapper.selectList(query).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    private void validateNodesOwnership(Long userId, Long fromNodeId, Long toNodeId) {
        FamilyNodeDO fromNode = familyNodeMapper.selectById(fromNodeId);
        FamilyNodeDO toNode = familyNodeMapper.selectById(toNodeId);
        if (fromNode == null || !Objects.equals(fromNode.getUserId(), userId)) {
            throw new BusinessException("起始节点不存在或无权操作");
        }
        if (toNode == null || !Objects.equals(toNode.getUserId(), userId)) {
            throw new BusinessException("目标节点不存在或无权操作");
        }
    }

    /**
     * 夫妻关系合法性校验。
     * 允许表（堂）兄妹等旁系血亲结婚；禁止自身、重复（含反向）、直系血亲与同胞。
     *
     * @param userId     用户 ID
     * @param fromNodeId 关系起点节点
     * @param toNodeId   关系终点节点
     */
    @Override
    public void validateSpouseRelation(Long userId, Long fromNodeId, Long toNodeId) {
        if (Objects.equals(fromNodeId, toNodeId)) {
            throw new BusinessException("不能与自身建立夫妻关系");
        }

        // 重复校验（含反向：夫妻互为配偶，任一方向已存在即视为重复）
        LambdaQueryWrapper<FamilyRelationDO> duplicateQuery = new LambdaQueryWrapper<>();
        duplicateQuery.eq(FamilyRelationDO::getUserId, userId)
                .eq(FamilyRelationDO::getRelationType, RelationTypeEnum.SPOUSE.getCode())
                .and(w -> w.and(w1 -> w1.eq(FamilyRelationDO::getFromNodeId, fromNodeId)
                                .eq(FamilyRelationDO::getToNodeId, toNodeId))
                        .or(w2 -> w2.eq(FamilyRelationDO::getFromNodeId, toNodeId)
                                .eq(FamilyRelationDO::getToNodeId, fromNodeId)));
        if (familyRelationMapper.selectCount(duplicateQuery) > 0) {
            throw new BusinessException("该夫妻关系已存在");
        }

        // 直系血亲（亲子，任一向）禁止结婚
        LambdaQueryWrapper<FamilyRelationDO> directQuery = new LambdaQueryWrapper<>();
        directQuery.eq(FamilyRelationDO::getUserId, userId)
                .eq(FamilyRelationDO::getRelationType, RelationTypeEnum.PARENT_CHILD.getCode())
                .and(w -> w.and(w1 -> w1.eq(FamilyRelationDO::getFromNodeId, fromNodeId)
                                .eq(FamilyRelationDO::getToNodeId, toNodeId))
                        .or(w2 -> w2.eq(FamilyRelationDO::getFromNodeId, toNodeId)
                                .eq(FamilyRelationDO::getToNodeId, fromNodeId)));
        if (familyRelationMapper.selectCount(directQuery) > 0) {
            throw new BusinessException("直系血亲不能建立夫妻关系");
        }

        // 同胞（存在共同父或母）禁止结婚；表/堂兄妹无共同父母，不受限制
        Set<Long> fromParents = listParentIds(userId, fromNodeId);
        Set<Long> toParents = listParentIds(userId, toNodeId);
        fromParents.retainAll(toParents);
        if (!fromParents.isEmpty()) {
            throw new BusinessException("同胞兄妹不能建立夫妻关系");
        }
    }

    /**
     * 获取指定节点的所有父节点 ID。
     *
     * @param userId 用户 ID
     * @param nodeId 节点 ID
     * @return 父节点 ID 集合（可变，便于求交集）
     */
    private Set<Long> listParentIds(Long userId, Long nodeId) {
        LambdaQueryWrapper<FamilyRelationDO> query = new LambdaQueryWrapper<>();
        query.eq(FamilyRelationDO::getUserId, userId)
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
        return dto;
    }
}
