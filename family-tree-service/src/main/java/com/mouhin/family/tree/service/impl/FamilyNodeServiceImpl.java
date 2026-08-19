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
    public Long createNode(Long familyId, Long userId, NodeCreateDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessException("节点名称不能为空");
        }
        if (dto.getName().trim().length() > FamilyTreeConsts.MAX_NAME_LENGTH) {
            throw new BusinessException("节点名称不能超过" + FamilyTreeConsts.MAX_NAME_LENGTH + "个字符");
        }

        FamilyNodeDO node = new FamilyNodeDO();
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
            throw new BusinessException("备注不能超过" + FamilyTreeConsts.MAX_REMARK_LENGTH + "个字符");
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
            FamilyNodeDO parent = checkNodeBelongsToFamily(familyId, dto.getParentNodeId());
            node.setGeneration(parent.getGeneration() + 1);
        } else if (dto.getChildNodeId() != null) {
            FamilyNodeDO child = checkNodeBelongsToFamily(familyId, dto.getChildNodeId());
            node.setGeneration(Math.max(child.getGeneration() - 1, 1));
        } else if (dto.getSpouseNodeId() != null) {
            FamilyNodeDO spouse = checkNodeBelongsToFamily(familyId, dto.getSpouseNodeId());
            node.setGeneration(spouse.getGeneration());
        } else {
            node.setGeneration(FamilyTreeConsts.DEFAULT_GENERATION);
        }

        if (node.getGeneration() != null && node.getGeneration() > FamilyTreeConsts.MAX_GENERATION_DEPTH) {
            throw new BusinessException("世代层级不能超过" + FamilyTreeConsts.MAX_GENERATION_DEPTH + "世");
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

        familyNodeMapper.insert(node);
        logger.info("Created family node id={} name={} for family={} by user={}",
                node.getId(), node.getName(), familyId, userId);

        // 建立亲子关系
        if (dto.getParentNodeId() != null) {
            FamilyRelationDO relation = new FamilyRelationDO();
            relation.setUserId(userId);
            relation.setFamilyId(familyId);
            relation.setFromNodeId(dto.getParentNodeId());
            relation.setToNodeId(node.getId());
            relation.setRelationType(RelationTypeEnum.PARENT_CHILD.getCode());
            relation.setCreateTime(LocalDateTime.now());
            relation.setUpdateTime(LocalDateTime.now());
            familyRelationMapper.insert(relation);
        }

        // 建立夫妻关系（需先校验：禁止自身、重复、直系血亲、同胞）
        if (dto.getSpouseNodeId() != null) {
            familyRelationService.validateSpouseRelation(familyId, dto.getSpouseNodeId(), node.getId());
            FamilyRelationDO relation = new FamilyRelationDO();
            relation.setUserId(userId);
            relation.setFamilyId(familyId);
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
            relation.setFamilyId(familyId);
            relation.setFromNodeId(node.getId());
            relation.setToNodeId(dto.getChildNodeId());
            relation.setRelationType(RelationTypeEnum.PARENT_CHILD.getCode());
            relation.setCreateTime(LocalDateTime.now());
            relation.setUpdateTime(LocalDateTime.now());
            familyRelationMapper.insert(relation);
        }

        // 树结构已变更，失效该家族的族谱树缓存（事务提交后生效）
        familyTreeService.evictFamilyTree(familyId);

        return node.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateNode(Long familyId, FamilyNodeDTO dto) {
        FamilyNodeDO existing = checkNodeBelongsToFamily(familyId, dto.getId());

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
                throw new BusinessException("备注不能超过" + FamilyTreeConsts.MAX_REMARK_LENGTH + "个字符");
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
        existing.setUpdateTime(LocalDateTime.now());
        familyNodeMapper.updateById(existing);

        // 日期字段需支持显式清除（传空字符串表示清空）
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

        // 生卒日期顺序校验（取更新后的有效值）
        LocalDate effectiveBirth = existing.getBirthDate();
        LocalDate effectiveDeath = existing.getDeathDate();
        if (dto.getBirthDate() != null) {
            effectiveBirth = dto.getBirthDate().isBlank() ? null : parseDate(dto.getBirthDate());
        }
        if (dto.getDeathDate() != null) {
            effectiveDeath = dto.getDeathDate().isBlank() ? null : parseDate(dto.getDeathDate());
        }
        if (effectiveBirth != null && effectiveDeath != null && effectiveDeath.isBefore(effectiveBirth)) {
            throw new BusinessException("去世日期不能早于出生日期");
        }

        familyTreeService.evictFamilyTree(familyId);
    }

    /**
     * 将指定节点的所有配偶的辈分同步为与该节点一致。
     *
     * @param familyId   家族ID
     * @param nodeId     节点 ID
     * @param generation 目标辈分（第几世）
     */
    private void syncSpouseGeneration(Long familyId, Long nodeId, Integer generation) {
        LambdaQueryWrapper<FamilyRelationDO> query = new LambdaQueryWrapper<>();
        query.eq(FamilyRelationDO::getFamilyId, familyId)
                .eq(FamilyRelationDO::getRelationType, RelationTypeEnum.SPOUSE.getCode())
                .and(w -> w.eq(FamilyRelationDO::getFromNodeId, nodeId)
                        .or().eq(FamilyRelationDO::getToNodeId, nodeId));
        List<FamilyRelationDO> spouseRelations = familyRelationMapper.selectList(query);
        for (FamilyRelationDO relation : spouseRelations) {
            Long spouseId = Objects.equals(relation.getFromNodeId(), nodeId)
                    ? relation.getToNodeId() : relation.getFromNodeId();
            LambdaUpdateWrapper<FamilyNodeDO> spouseUpdate = new LambdaUpdateWrapper<>();
            spouseUpdate.eq(FamilyNodeDO::getId, spouseId)
                    .eq(FamilyNodeDO::getFamilyId, familyId)
                    .set(FamilyNodeDO::getGeneration, generation)
                    .set(FamilyNodeDO::getUpdateTime, LocalDateTime.now());
            familyNodeMapper.update(null, spouseUpdate);
        }
        if (!spouseRelations.isEmpty()) {
            logger.info("Synced generation={} to {} spouse(s) of node={} for family={}",
                    generation, spouseRelations.size(), nodeId, familyId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncDescendantGenerations(Long familyId, Long nodeId, Integer generation) {
        // 一次性加载该家族所有亲子关系到内存，避免 BFS 中逐节点查询的 N+1 问题
        LambdaQueryWrapper<FamilyRelationDO> allChildQuery = new LambdaQueryWrapper<>();
        allChildQuery.eq(FamilyRelationDO::getFamilyId, familyId)
                .eq(FamilyRelationDO::getRelationType, RelationTypeEnum.PARENT_CHILD.getCode());
        List<FamilyRelationDO> allChildRelations = familyRelationMapper.selectList(allChildQuery);

        // 构建邻接表：parentId -> [childId, ...]
        java.util.Map<Long, List<Long>> childrenMap = new java.util.HashMap<>();
        for (FamilyRelationDO rel : allChildRelations) {
            childrenMap.computeIfAbsent(rel.getFromNodeId(), k -> new java.util.ArrayList<>())
                    .add(rel.getToNodeId());
        }

        // 一次性加载该家族所有配偶关系到内存，避免 syncSpouseGeneration 中逐节点查询
        LambdaQueryWrapper<FamilyRelationDO> spouseQuery = new LambdaQueryWrapper<>();
        spouseQuery.eq(FamilyRelationDO::getFamilyId, familyId)
                .eq(FamilyRelationDO::getRelationType, RelationTypeEnum.SPOUSE.getCode());
        List<FamilyRelationDO> allSpouseRelations = familyRelationMapper.selectList(spouseQuery);

        // 构建配偶邻接表：nodeId -> [spouseId, ...]
        java.util.Map<Long, List<Long>> spouseMap = new java.util.HashMap<>();
        for (FamilyRelationDO rel : allSpouseRelations) {
            spouseMap.computeIfAbsent(rel.getFromNodeId(), k -> new java.util.ArrayList<>())
                    .add(rel.getToNodeId());
            spouseMap.computeIfAbsent(rel.getToNodeId(), k -> new java.util.ArrayList<>())
                    .add(rel.getFromNodeId());
        }

        // BFS：使用内存邻接表遍历，收集需要更新的节点
        Set<Long> visited = new HashSet<>();
        Deque<long[]> queue = new ArrayDeque<>();
        queue.offer(new long[]{nodeId, generation});
        visited.add(nodeId);

        // 收集待更新：childId -> childGen
        java.util.Map<Long, Integer> pendingUpdates = new java.util.HashMap<>();
        // 收集需要同步配偶的节点：childId -> childGen
        java.util.Map<Long, Integer> pendingSpouseSyncs = new java.util.HashMap<>();

        while (!queue.isEmpty()) {
            long[] current = queue.poll();
            long currentId = current[0];
            int currentGen = (int) current[1];

            List<Long> childIds = childrenMap.getOrDefault(currentId, java.util.Collections.emptyList());
            for (Long childId : childIds) {
                if (visited.contains(childId)) {
                    continue;
                }
                visited.add(childId);
                int childGen = currentGen + 1;
                if (childGen > FamilyTreeConsts.MAX_GENERATION_DEPTH) {
                    throw new BusinessException("世代层级不能超过"
                            + FamilyTreeConsts.MAX_GENERATION_DEPTH + "世");
                }
                pendingUpdates.put(childId, childGen);
                pendingSpouseSyncs.put(childId, childGen);
                queue.offer(new long[]{childId, childGen});
            }
        }

        // 批量更新子节点辈分
        for (java.util.Map.Entry<Long, Integer> entry : pendingUpdates.entrySet()) {
            LambdaUpdateWrapper<FamilyNodeDO> childUpdate = new LambdaUpdateWrapper<>();
            childUpdate.eq(FamilyNodeDO::getId, entry.getKey())
                    .eq(FamilyNodeDO::getFamilyId, familyId)
                    .set(FamilyNodeDO::getGeneration, entry.getValue())
                    .set(FamilyNodeDO::getUpdateTime, LocalDateTime.now());
            familyNodeMapper.update(null, childUpdate);
        }

        // 批量同步配偶辈分（使用内存中的配偶邻接表）
        for (java.util.Map.Entry<Long, Integer> entry : pendingSpouseSyncs.entrySet()) {
            Long childId = entry.getKey();
            int childGen = entry.getValue();
            List<Long> spouseIds = spouseMap.getOrDefault(childId, java.util.Collections.emptyList());
            for (Long spouseId : spouseIds) {
                LambdaUpdateWrapper<FamilyNodeDO> spouseUpdate = new LambdaUpdateWrapper<>();
                spouseUpdate.eq(FamilyNodeDO::getId, spouseId)
                        .eq(FamilyNodeDO::getFamilyId, familyId)
                        .set(FamilyNodeDO::getGeneration, childGen)
                        .set(FamilyNodeDO::getUpdateTime, LocalDateTime.now());
                familyNodeMapper.update(null, spouseUpdate);
            }
        }

        logger.info("Synced descendant generations from node={} gen={} for family={} ({} nodes updated)",
                nodeId, generation, familyId, pendingUpdates.size());

        familyTreeService.evictFamilyTree(familyId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNode(Long familyId, Long nodeId) {
        checkNodeBelongsToFamily(familyId, nodeId);

        LambdaQueryWrapper<FamilyRelationDO> relationQuery = new LambdaQueryWrapper<>();
        relationQuery.eq(FamilyRelationDO::getFamilyId, familyId)
                .and(w -> w.eq(FamilyRelationDO::getFromNodeId, nodeId)
                        .or().eq(FamilyRelationDO::getToNodeId, nodeId));
        familyRelationMapper.delete(relationQuery);

        familyNodeMapper.deleteById(nodeId);
        logger.info("Deleted family node id={} for family={}", nodeId, familyId);

        familyTreeService.evictFamilyTree(familyId);
    }

    @Override
    public FamilyNodeDTO getNode(Long familyId, Long nodeId) {
        FamilyNodeDO node = checkNodeBelongsToFamily(familyId, nodeId);
        return toDTO(node);
    }

    @Override
    public List<FamilyNodeDTO> listNodes(Long familyId) {
        LambdaQueryWrapper<FamilyNodeDO> query = new LambdaQueryWrapper<>();
        query.eq(FamilyNodeDO::getFamilyId, familyId)
                .orderByAsc(FamilyNodeDO::getGeneration)
                .orderByAsc(FamilyNodeDO::getId);
        return familyNodeMapper.selectList(query).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<FamilyNodeDTO> searchNodes(Long familyId, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        String kw = keyword.trim();
        LambdaQueryWrapper<FamilyNodeDO> query = new LambdaQueryWrapper<>();
        query.eq(FamilyNodeDO::getFamilyId, familyId)
                .and(w -> w.like(FamilyNodeDO::getName, kw)
                        .or().like(FamilyNodeDO::getZi, kw)
                        .or().like(FamilyNodeDO::getHao, kw)
                        .or().like(FamilyNodeDO::getHui, kw))
                .orderByAsc(FamilyNodeDO::getGeneration)
                .orderByAsc(FamilyNodeDO::getId)
                .last("LIMIT 20");
        return familyNodeMapper.selectList(query).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateColor(Long familyId, List<Long> nodeIds, String colorLabel) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            throw new BusinessException("节点ID列表不能为空");
        }
        ColorLabelEnum.validateCode(colorLabel);

        // 批量校验归属（替代逐节点 selectById 的 N+1 查询）
        List<FamilyNodeDO> nodes = familyNodeMapper.selectBatchIds(nodeIds);
        Set<Long> validIds = nodes.stream()
                .filter(n -> Objects.equals(n.getFamilyId(), familyId))
                .map(FamilyNodeDO::getId)
                .collect(Collectors.toSet());
        if (!validIds.containsAll(nodeIds)) {
            throw new BusinessException("节点不存在或无权操作");
        }

        LambdaUpdateWrapper<FamilyNodeDO> update = new LambdaUpdateWrapper<>();
        update.in(FamilyNodeDO::getId, nodeIds)
                .eq(FamilyNodeDO::getFamilyId, familyId)
                .set(FamilyNodeDO::getColorLabel, colorLabel)
                .set(FamilyNodeDO::getUpdateTime, LocalDateTime.now());
        familyNodeMapper.update(null, update);
        logger.info("Updated colorLabel={} for {} nodes for family={}", colorLabel, nodeIds.size(), familyId);

        familyTreeService.evictFamilyTree(familyId);
    }

    /**
     * 校验节点存在且属于指定家族
     *
     * @param familyId 家族ID
     * @param nodeId   节点ID
     * @return 节点数据对象
     */
    private FamilyNodeDO checkNodeBelongsToFamily(Long familyId, Long nodeId) {
        FamilyNodeDO node = familyNodeMapper.selectById(nodeId);
        if (node == null || !Objects.equals(node.getFamilyId(), familyId)) {
            throw new BusinessException("节点不存在或无权操作");
        }
        return node;
    }

    private int countChildren(Long familyId, Long parentNodeId) {
        LambdaQueryWrapper<FamilyRelationDO> query = new LambdaQueryWrapper<>();
        query.eq(FamilyRelationDO::getFamilyId, familyId)
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
