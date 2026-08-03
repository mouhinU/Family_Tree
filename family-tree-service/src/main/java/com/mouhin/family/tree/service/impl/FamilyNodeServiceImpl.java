package com.mouhin.family.tree.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.FamilyNodeDTO;
import com.mouhin.family.tree.common.dto.NodeCreateDTO;
import com.mouhin.family.tree.common.enums.ColorLabelEnum;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 族谱节点服务实现类
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@Service
public class FamilyNodeServiceImpl implements FamilyNodeService {

    private static final Logger logger = LoggerFactory.getLogger(FamilyNodeServiceImpl.class);

    private final FamilyNodeMapper familyNodeMapper;
    private final FamilyRelationMapper familyRelationMapper;
    private final FamilyRelationService familyRelationService;
    private final FamilyTreeService familyTreeService;

    public FamilyNodeServiceImpl(FamilyNodeMapper familyNodeMapper,
                                 FamilyRelationMapper familyRelationMapper,
                                 @Lazy FamilyRelationService familyRelationService,
                                 FamilyTreeService familyTreeService) {
        this.familyNodeMapper = familyNodeMapper;
        this.familyRelationMapper = familyRelationMapper;
        this.familyRelationService = familyRelationService;
        this.familyTreeService = familyTreeService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createNode(Long userId, NodeCreateDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessException("节点名称不能为空");
        }
        if (dto.getName().trim().length() > FamilyTreeConsts.MAX_NAME_LENGTH) {
            throw new BusinessException("节点名称不能超过" + FamilyTreeConsts.MAX_NAME_LENGTH + "个字符");
        }

        FamilyNodeDO node = new FamilyNodeDO();
        node.setUserId(userId);
        node.setName(dto.getName().trim());
        node.setGender(dto.getGender() != null ? dto.getGender() : 0);
        node.setBirthDate(parseDate(dto.getBirthDate()));
        node.setDeathDate(parseDate(dto.getDeathDate()));
        if (dto.getColorLabel() != null) {
            ColorLabelEnum.validateCode(dto.getColorLabel());
            node.setColorLabel(dto.getColorLabel());
        } else {
            node.setColorLabel(ColorLabelEnum.DEFAULT.getCode());
        }
        node.setAvatar(dto.getAvatar());
        node.setRemark(dto.getRemark());
        node.setCreateTime(LocalDateTime.now());
        node.setUpdateTime(LocalDateTime.now());

        // 计算世代层级
        if (dto.getParentNodeId() != null) {
            FamilyNodeDO parent = getAndCheckOwnership(userId, dto.getParentNodeId());
            node.setGeneration(parent.getGeneration() + 1);
        } else if (dto.getChildNodeId() != null) {
            FamilyNodeDO child = getAndCheckOwnership(userId, dto.getChildNodeId());
            node.setGeneration(Math.max(child.getGeneration() - 1, 1));
        } else if (dto.getSpouseNodeId() != null) {
            FamilyNodeDO spouse = getAndCheckOwnership(userId, dto.getSpouseNodeId());
            node.setGeneration(spouse.getGeneration());
        } else {
            node.setGeneration(FamilyTreeConsts.DEFAULT_GENERATION);
        }

        if (node.getGeneration() != null && node.getGeneration() > FamilyTreeConsts.MAX_GENERATION_DEPTH) {
            throw new BusinessException("世代层级不能超过" + FamilyTreeConsts.MAX_GENERATION_DEPTH + "世");
        }

        // 同胞排次：优先使用传入值；新增子节点且未传时自动追加为末位
        if (dto.getBirthOrder() != null) {
            node.setBirthOrder(dto.getBirthOrder());
        } else if (dto.getParentNodeId() != null) {
            node.setBirthOrder(countChildren(userId, dto.getParentNodeId()) + 1);
        }

        familyNodeMapper.insert(node);
        logger.info("Created family node id={} name={} for user={}", node.getId(), node.getName(), userId);

        // 建立亲子关系
        if (dto.getParentNodeId() != null) {
            FamilyRelationDO relation = new FamilyRelationDO();
            relation.setUserId(userId);
            relation.setFromNodeId(dto.getParentNodeId());
            relation.setToNodeId(node.getId());
            relation.setRelationType(RelationTypeEnum.PARENT_CHILD.getCode());
            relation.setCreateTime(LocalDateTime.now());
            relation.setUpdateTime(LocalDateTime.now());
            familyRelationMapper.insert(relation);
        }

        // 建立夫妻关系（需先校验：禁止自身、重复、直系血亲、同胞）
        if (dto.getSpouseNodeId() != null) {
            familyRelationService.validateSpouseRelation(userId, dto.getSpouseNodeId(), node.getId());
            FamilyRelationDO relation = new FamilyRelationDO();
            relation.setUserId(userId);
            relation.setFromNodeId(dto.getSpouseNodeId());
            relation.setToNodeId(node.getId());
            relation.setRelationType(RelationTypeEnum.SPOUSE.getCode());
            relation.setCreateTime(LocalDateTime.now());
            relation.setUpdateTime(LocalDateTime.now());
            familyRelationMapper.insert(relation);
        }

        // 新增父节点：当前节点为父，childNodeId 为子
        if (dto.getChildNodeId() != null) {
            FamilyRelationDO relation = new FamilyRelationDO();
            relation.setUserId(userId);
            relation.setFromNodeId(node.getId());
            relation.setToNodeId(dto.getChildNodeId());
            relation.setRelationType(RelationTypeEnum.PARENT_CHILD.getCode());
            relation.setCreateTime(LocalDateTime.now());
            relation.setUpdateTime(LocalDateTime.now());
            familyRelationMapper.insert(relation);
        }

        // 树结构已变更，失效该用户的族谱树缓存（事务提交后生效）
        familyTreeService.evictUserTree(userId);

        return node.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateNode(Long userId, FamilyNodeDTO dto) {
        FamilyNodeDO existing = getAndCheckOwnership(userId, dto.getId());

        if (dto.getName() != null && !dto.getName().isBlank()) {
            existing.setName(dto.getName().trim());
        }
        if (dto.getGender() != null) {
            existing.setGender(dto.getGender());
        }
        if (dto.getColorLabel() != null) {
            ColorLabelEnum.validateCode(dto.getColorLabel());
            existing.setColorLabel(dto.getColorLabel());
        }
        if (dto.getBirthOrder() != null) {
            existing.setBirthOrder(dto.getBirthOrder());
        }
        if (dto.getGeneration() != null) {
            boolean generationChanged = !dto.getGeneration().equals(existing.getGeneration());
            existing.setGeneration(dto.getGeneration());
            // 配偶的辈分（第几世）与本节点保持一致，后代递归同步
            if (generationChanged) {
                syncSpouseGeneration(userId, existing.getId(), dto.getGeneration());
                syncDescendantGenerations(userId, existing.getId(), dto.getGeneration());
            }
        }
        if (dto.getAvatar() != null) {
            existing.setAvatar(dto.getAvatar());
        }
        if (dto.getRemark() != null) {
            existing.setRemark(dto.getRemark());
        }
        existing.setUpdateTime(LocalDateTime.now());
        familyNodeMapper.updateById(existing);

        // 日期字段需支持显式清除（传空字符串表示清空）。
        // updateById 默认忽略 null 字段，置空无法生效，故改用 UpdateWrapper 显式写入。
        // 仅当前端实际传入日期字段（非 null）时才处理；null 表示未提交，保持原值不变。
        if (dto.getBirthDate() != null || dto.getDeathDate() != null) {
            LambdaUpdateWrapper<FamilyNodeDO> dateUpdate = new LambdaUpdateWrapper<>();
            dateUpdate.eq(FamilyNodeDO::getId, existing.getId());
            if (dto.getBirthDate() != null) {
                dateUpdate.set(FamilyNodeDO::getBirthDate,
                        dto.getBirthDate().isBlank() ? null : parseDate(dto.getBirthDate()));
            }
            if (dto.getDeathDate() != null) {
                dateUpdate.set(FamilyNodeDO::getDeathDate,
                        dto.getDeathDate().isBlank() ? null : parseDate(dto.getDeathDate()));
            }
            familyNodeMapper.update(null, dateUpdate);
        }

        // 节点信息已变更，失效该用户的族谱树缓存（事务提交后生效）
        familyTreeService.evictUserTree(userId);
    }

    /**
     * 将指定节点的所有配偶的辈分（第几世）同步为与该节点一致。
     * 配偶关系为 SPOUSE 类型，节点可能位于 fromNodeId 或 toNodeId 任一侧。
     *
     * @param userId     用户 ID
     * @param nodeId     节点 ID
     * @param generation 目标辈分（第几世）
     */
    private void syncSpouseGeneration(Long userId, Long nodeId, Integer generation) {
        LambdaQueryWrapper<FamilyRelationDO> query = new LambdaQueryWrapper<>();
        query.eq(FamilyRelationDO::getUserId, userId)
                .eq(FamilyRelationDO::getRelationType, RelationTypeEnum.SPOUSE.getCode())
                .and(w -> w.eq(FamilyRelationDO::getFromNodeId, nodeId)
                        .or().eq(FamilyRelationDO::getToNodeId, nodeId));
        List<FamilyRelationDO> spouseRelations = familyRelationMapper.selectList(query);
        for (FamilyRelationDO relation : spouseRelations) {
            Long spouseId = Objects.equals(relation.getFromNodeId(), nodeId)
                    ? relation.getToNodeId() : relation.getFromNodeId();
            LambdaUpdateWrapper<FamilyNodeDO> spouseUpdate = new LambdaUpdateWrapper<>();
            spouseUpdate.eq(FamilyNodeDO::getId, spouseId)
                    .eq(FamilyNodeDO::getUserId, userId)
                    .set(FamilyNodeDO::getGeneration, generation)
                    .set(FamilyNodeDO::getUpdateTime, LocalDateTime.now());
            familyNodeMapper.update(null, spouseUpdate);
        }
        if (!spouseRelations.isEmpty()) {
            logger.info("Synced generation={} to {} spouse(s) of node={} for user={}",
                    generation, spouseRelations.size(), nodeId, userId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncDescendantGenerations(Long userId, Long nodeId, Integer generation) {
        Set<Long> visited = new HashSet<>();
        Deque<long[]> queue = new ArrayDeque<>();
        // 元素：[nodeId, generation]
        queue.offer(new long[]{nodeId, generation});
        visited.add(nodeId);

        while (!queue.isEmpty()) {
            long[] current = queue.poll();
            long currentId = current[0];
            int currentGen = (int) current[1];

            // 查找所有子节点
            LambdaQueryWrapper<FamilyRelationDO> childQuery = new LambdaQueryWrapper<>();
            childQuery.eq(FamilyRelationDO::getUserId, userId)
                    .eq(FamilyRelationDO::getFromNodeId, currentId)
                    .eq(FamilyRelationDO::getRelationType, RelationTypeEnum.PARENT_CHILD.getCode());
            List<FamilyRelationDO> childRelations = familyRelationMapper.selectList(childQuery);

            for (FamilyRelationDO rel : childRelations) {
                Long childId = rel.getToNodeId();
                if (visited.contains(childId)) {
                    continue;
                }
                visited.add(childId);
                int childGen = currentGen + 1;
                if (childGen > FamilyTreeConsts.MAX_GENERATION_DEPTH) {
                    throw new BusinessException("世代层级不能超过"
                            + FamilyTreeConsts.MAX_GENERATION_DEPTH + "世");
                }
                // 更新子节点世代
                LambdaUpdateWrapper<FamilyNodeDO> childUpdate = new LambdaUpdateWrapper<>();
                childUpdate.eq(FamilyNodeDO::getId, childId)
                        .eq(FamilyNodeDO::getUserId, userId)
                        .set(FamilyNodeDO::getGeneration, childGen)
                        .set(FamilyNodeDO::getUpdateTime, LocalDateTime.now());
                familyNodeMapper.update(null, childUpdate);

                // 子节点的配偶同代
                syncSpouseGeneration(userId, childId, childGen);
                // 继续向下遍历
                queue.offer(new long[]{childId, childGen});
            }
        }
        logger.info("Synced descendant generations from node={} gen={} for user={}", nodeId, generation, userId);

        // 后代世代已变更，失效该用户的族谱树缓存（事务提交后生效）
        familyTreeService.evictUserTree(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNode(Long userId, Long nodeId) {
        getAndCheckOwnership(userId, nodeId);

        // 删除关联关系
        LambdaQueryWrapper<FamilyRelationDO> relationQuery = new LambdaQueryWrapper<>();
        relationQuery.eq(FamilyRelationDO::getUserId, userId)
                .and(w -> w.eq(FamilyRelationDO::getFromNodeId, nodeId)
                        .or().eq(FamilyRelationDO::getToNodeId, nodeId));
        familyRelationMapper.delete(relationQuery);

        // 删除节点
        familyNodeMapper.deleteById(nodeId);
        logger.info("Deleted family node id={} for user={}", nodeId, userId);

        // 树结构已变更，失效该用户的族谱树缓存（事务提交后生效）
        familyTreeService.evictUserTree(userId);
    }

    @Override
    public FamilyNodeDTO getNode(Long userId, Long nodeId) {
        FamilyNodeDO node = getAndCheckOwnership(userId, nodeId);
        return toDTO(node);
    }

    @Override
    public List<FamilyNodeDTO> listNodes(Long userId) {
        LambdaQueryWrapper<FamilyNodeDO> query = new LambdaQueryWrapper<>();
        query.eq(FamilyNodeDO::getUserId, userId)
                .orderByAsc(FamilyNodeDO::getGeneration)
                .orderByAsc(FamilyNodeDO::getId);
        return familyNodeMapper.selectList(query).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateColor(Long userId, List<Long> nodeIds, String colorLabel) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            throw new BusinessException("节点ID列表不能为空");
        }
        ColorLabelEnum.validateCode(colorLabel);

        // 批量校验归属（替代逐节点 selectById 的 N+1 查询）
        List<FamilyNodeDO> nodes = familyNodeMapper.selectBatchIds(nodeIds);
        Set<Long> ownedIds = nodes.stream()
                .filter(n -> Objects.equals(n.getUserId(), userId))
                .map(FamilyNodeDO::getId)
                .collect(Collectors.toSet());
        if (!ownedIds.containsAll(nodeIds)) {
            throw new BusinessException("节点不存在或无权操作");
        }

        // 单条 SQL 批量更新（替代逐节点 updateById）
        LambdaUpdateWrapper<FamilyNodeDO> update = new LambdaUpdateWrapper<>();
        update.in(FamilyNodeDO::getId, nodeIds)
                .eq(FamilyNodeDO::getUserId, userId)
                .set(FamilyNodeDO::getColorLabel, colorLabel)
                .set(FamilyNodeDO::getUpdateTime, LocalDateTime.now());
        familyNodeMapper.update(null, update);
        logger.info("Updated colorLabel={} for {} nodes for user={}", colorLabel, nodeIds.size(), userId);

        // 节点颜色已变更，失效该用户的族谱树缓存（事务提交后生效）
        familyTreeService.evictUserTree(userId);
    }

    private FamilyNodeDO getAndCheckOwnership(Long userId, Long nodeId) {
        FamilyNodeDO node = familyNodeMapper.selectById(nodeId);
        if (node == null || !Objects.equals(node.getUserId(), userId)) {
            throw new BusinessException("节点不存在或无权操作");
        }
        return node;
    }

    private int countChildren(Long userId, Long parentNodeId) {
        LambdaQueryWrapper<FamilyRelationDO> query = new LambdaQueryWrapper<>();
        query.eq(FamilyRelationDO::getUserId, userId)
                .eq(FamilyRelationDO::getFromNodeId, parentNodeId)
                .eq(FamilyRelationDO::getRelationType, RelationTypeEnum.PARENT_CHILD.getCode());
        return Math.toIntExact(familyRelationMapper.selectCount(query));
    }

    private FamilyNodeDTO toDTO(FamilyNodeDO entity) {
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
