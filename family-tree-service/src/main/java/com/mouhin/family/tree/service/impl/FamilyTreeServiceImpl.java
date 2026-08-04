package com.mouhin.family.tree.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mouhin.family.tree.common.dto.TreeNodeVO;
import com.mouhin.family.tree.common.enums.ColorLabelEnum;
import com.mouhin.family.tree.common.enums.RelationTypeEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.persistence.entity.FamilyNodeDO;
import com.mouhin.family.tree.persistence.entity.FamilyRelationDO;
import com.mouhin.family.tree.persistence.mapper.FamilyNodeMapper;
import com.mouhin.family.tree.persistence.mapper.FamilyRelationMapper;
import com.mouhin.family.tree.service.FamilyTreeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 族谱树形结构服务实现类
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@Service
public class FamilyTreeServiceImpl implements FamilyTreeService {

    private final FamilyNodeMapper familyNodeMapper;
    private final FamilyRelationMapper familyRelationMapper;

    /** 树缓存最大家族数：家庭内部系统，远超实际规模，仅作内存保护 */
    private static final int MAX_CACHED_FAMILIES = 200;

    /** 树缓存过期时间：除写操作主动失效外的兜底，防止遗漏写路径导致长期脏读 */
    private static final Duration TREE_CACHE_TTL = Duration.ofMinutes(10);

    /** 整棵族谱树缓存（key=familyId）。族谱为读多写少场景，命中时省去全表查询与建树。
     *  注意：缓存值为构建好的 VO 列表，读取方只可序列化展示，不得修改其内容。 */
    private final Cache<Long, List<TreeNodeVO>> fullTreeCache = Caffeine.newBuilder()
            .maximumSize(MAX_CACHED_FAMILIES)
            .expireAfterWrite(TREE_CACHE_TTL)
            .build();

    public FamilyTreeServiceImpl(FamilyNodeMapper familyNodeMapper, FamilyRelationMapper familyRelationMapper) {
        this.familyNodeMapper = familyNodeMapper;
        this.familyRelationMapper = familyRelationMapper;
    }

    @Override
    public List<TreeNodeVO> getFullTree(Long familyId) {
        return fullTreeCache.get(familyId, fid -> buildFullTree(fid));
    }

    @Override
    public void evictFamilyTree(Long familyId) {
        if (familyId == null) {
            return;
        }
        // 写路径多在事务内：延迟到提交后再失效，避免并发读在提交前用旧数据回填缓存
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    fullTreeCache.invalidate(familyId);
                }
            });
        } else {
            fullTreeCache.invalidate(familyId);
        }
    }

    /**
     * 真正执行全量查询与建树（缓存未命中时由 {@link #getFullTree} 触发）。
     *
     * @param familyId 家族ID
     * @return 树形结构列表（可能有多个根节点）
     */
    private List<TreeNodeVO> buildFullTree(Long familyId) {
        List<FamilyNodeDO> allNodes = listFamilyNodes(familyId);
        List<FamilyRelationDO> allRelations = listFamilyRelations(familyId);

        if (allNodes.isEmpty()) {
            return new ArrayList<>();
        }

        // 找出有父节点的节点（非根，含过继/收养）
        Set<Long> childIds = allRelations.stream()
                .filter(r -> Objects.equals(r.getRelationType(), RelationTypeEnum.PARENT_CHILD.getCode())
                        || Objects.equals(r.getRelationType(), RelationTypeEnum.ADOPTION.getCode()))
                .map(FamilyRelationDO::getToNodeId)
                .collect(Collectors.toSet());

        // 预标记所有配偶关系中的"被添加方"（toNodeId），这些节点不作为独立根
        Set<Long> spouseAttachedIds = allRelations.stream()
                .filter(r -> Objects.equals(r.getRelationType(), RelationTypeEnum.SPOUSE.getCode()))
                .map(FamilyRelationDO::getToNodeId)
                .collect(Collectors.toSet());

        List<FamilyNodeDO> roots = allNodes.stream()
                .filter(n -> !childIds.contains(n.getId()))
                .filter(n -> !spouseAttachedIds.contains(n.getId()))
                .collect(Collectors.toList());

        Map<Long, FamilyNodeDO> nodeMap = allNodes.stream()
                .collect(Collectors.toMap(FamilyNodeDO::getId, n -> n, (a, b) -> a));

        // 预构建索引，避免递归内对每个节点全量扫描关系表（O(n·r) → O(n+r)）
        Map<Long, List<FamilyRelationDO>> spouseRelByNode = new HashMap<>();
        Map<Long, List<Long>> childIdsByParent = new HashMap<>();
        Map<Long, List<Long>> parentIdsByChild = new HashMap<>();
        buildRelationIndexes(allRelations, spouseRelByNode, childIdsByParent, parentIdsByChild);

        // 预计算：改嫁/续弦配偶的卫星挂载优先级
        // 对每个有多段非离异婚姻的配偶，关系ID最大的丈夫优先获得卫星配偶卡片
        Set<Long> contestedSpouseSet = new HashSet<>();
        Map<Long, Long> contestedPreferredHusband = new HashMap<>();
        for (Map.Entry<Long, List<FamilyRelationDO>> entry : spouseRelByNode.entrySet()) {
            Long spouseId = entry.getKey();
            Long bestNodeId = null;
            Long bestRelId = null;
            int nonDivCount = 0;
            for (FamilyRelationDO rel : entry.getValue()) {
                boolean divorced = Boolean.TRUE.equals(rel.getDivorced()) || rel.getDivorceDate() != null;
                if (divorced) {
                    continue;
                }
                nonDivCount++;
                Long partnerId = Objects.equals(rel.getFromNodeId(), spouseId) ? rel.getToNodeId() : rel.getFromNodeId();
                if (bestRelId == null || rel.getId() > bestRelId) {
                    bestRelId = rel.getId();
                    bestNodeId = partnerId;
                }
            }
            if (nonDivCount >= 2 && bestNodeId != null) {
                contestedSpouseSet.add(spouseId);
                contestedPreferredHusband.put(spouseId, bestNodeId);
            }
        }

        Set<Long> visited = new HashSet<>();
        Set<Long> globalSatelliteSpouseIds = new HashSet<>();
        List<TreeNodeVO> tree = new ArrayList<>();
        for (FamilyNodeDO root : roots) {
            if (visited.contains(root.getId())) {
                continue;
            }
            TreeNodeVO vo = buildSubTree(root, spouseRelByNode, childIdsByParent, parentIdsByChild, nodeMap, visited, childIds, globalSatelliteSpouseIds, contestedSpouseSet, contestedPreferredHusband);
            tree.add(vo);
        }
        return tree;
    }

    @Override
    public TreeNodeVO getSubTree(Long familyId, Long nodeId) {
        FamilyNodeDO node = familyNodeMapper.selectById(nodeId);
        if (node == null || !Objects.equals(node.getFamilyId(), familyId)) {
            throw new BusinessException("节点不存在或无权操作");
        }

        List<FamilyRelationDO> allRelations = listFamilyRelations(familyId);
        Map<Long, FamilyNodeDO> nodeMap = listFamilyNodes(familyId).stream()
                .collect(Collectors.toMap(FamilyNodeDO::getId, n -> n, (a, b) -> a));

        Set<Long> childIds = allRelations.stream()
                .filter(r -> Objects.equals(r.getRelationType(), RelationTypeEnum.PARENT_CHILD.getCode())
                        || Objects.equals(r.getRelationType(), RelationTypeEnum.ADOPTION.getCode()))
                .map(FamilyRelationDO::getToNodeId)
                .collect(Collectors.toSet());

        Map<Long, List<FamilyRelationDO>> spouseRelByNode = new HashMap<>();
        Map<Long, List<Long>> childIdsByParent = new HashMap<>();
        Map<Long, List<Long>> parentIdsByChild = new HashMap<>();
        buildRelationIndexes(allRelations, spouseRelByNode, childIdsByParent, parentIdsByChild);

        Set<Long> visited = new HashSet<>();
        Set<Long> globalSatelliteSpouseIds = new HashSet<>();
        Set<Long> contestedSpouseSet = new HashSet<>();
        Map<Long, Long> contestedPreferredHusband = new HashMap<>();
        return buildSubTree(node, spouseRelByNode, childIdsByParent, parentIdsByChild, nodeMap, visited, childIds, globalSatelliteSpouseIds, contestedSpouseSet, contestedPreferredHusband);
    }

    /**
     * 从全量关系列表构建三个查询索引：
     * spouseRelByNode —— 节点id → 涉及该节点的配偶关系（from/to 双向挂载）；
     * childIdsByParent —— 父节点id → 子女节点id列表；
     * parentIdsByChild —— 子女节点id → 父母节点id列表（血亲亲缘度计算向上追溯用）。
     */
    private void buildRelationIndexes(List<FamilyRelationDO> allRelations,
                                      Map<Long, List<FamilyRelationDO>> spouseRelByNode,
                                      Map<Long, List<Long>> childIdsByParent,
                                      Map<Long, List<Long>> parentIdsByChild) {
        for (FamilyRelationDO rel : allRelations) {
            if (Objects.equals(rel.getRelationType(), RelationTypeEnum.SPOUSE.getCode())) {
                spouseRelByNode.computeIfAbsent(rel.getFromNodeId(), k -> new ArrayList<>()).add(rel);
                spouseRelByNode.computeIfAbsent(rel.getToNodeId(), k -> new ArrayList<>()).add(rel);
            } else if (Objects.equals(rel.getRelationType(), RelationTypeEnum.PARENT_CHILD.getCode())
                    || Objects.equals(rel.getRelationType(), RelationTypeEnum.ADOPTION.getCode())) {
                // 亲子关系和过继/收养关系均纳入树形结构的父子索引
                childIdsByParent.computeIfAbsent(rel.getFromNodeId(), k -> new ArrayList<>()).add(rel.getToNodeId());
                parentIdsByChild.computeIfAbsent(rel.getToNodeId(), k -> new ArrayList<>()).add(rel.getFromNodeId());
            }
        }
    }

    private TreeNodeVO buildSubTree(FamilyNodeDO node, Map<Long, List<FamilyRelationDO>> spouseRelByNode,
                                    Map<Long, List<Long>> childIdsByParent, Map<Long, List<Long>> parentIdsByChild,
                                    Map<Long, FamilyNodeDO> nodeMap,
                                    Set<Long> visited, Set<Long> childIds,
                                    Set<Long> globalSatelliteSpouseIds,
                                    Set<Long> contestedSpouseSet,
                                    Map<Long, Long> contestedPreferredHusband) {
        if (visited.contains(node.getId())) {
            return toVO(node);
        }
        visited.add(node.getId());

        TreeNodeVO vo = toVO(node);

        // 查找配偶（携带关系元数据）：直接命中索引，无需全量扫描
        List<FamilyRelationDO> spouseRelations = spouseRelByNode.getOrDefault(node.getId(), List.of());

        Set<Long> satelliteSpouseIds = new HashSet<>();

        // 血亲配偶（近亲结婚）：双方均为族谱成员，各自保留本支
        for (FamilyRelationDO rel : spouseRelations) {
            Long spouseId = Objects.equals(rel.getFromNodeId(), node.getId()) ? rel.getToNodeId() : rel.getFromNodeId();
            if (!childIds.contains(spouseId)) {
                continue;
            }
            FamilyNodeDO spouseNode = nodeMap.get(spouseId);
            if (spouseNode == null) {
                continue;
            }
            boolean divorced = Boolean.TRUE.equals(rel.getDivorced()) || rel.getDivorceDate() != null;
            boolean widowed = Boolean.TRUE.equals(rel.getWidowed());
            TreeNodeVO refVO = toVO(spouseNode);
            refVO.setRelationId(rel.getId());
            refVO.setDivorced(divorced);
            refVO.setWidowed(widowed);
            refVO.setMarriageDate(rel.getMarriageDate() != null ? rel.getMarriageDate().toString() : null);
            refVO.setDivorceDate(rel.getDivorceDate() != null ? rel.getDivorceDate().toString() : null);
            refVO.setBloodRelationLabel(bloodRelationLabel(node.getId(), spouseId, parentIdsByChild));
            vo.getBloodSpouses().add(refVO);
        }

        // 第一遍：非离异（在婚 + 丧偶）→ 卫星配偶
        // 优先挂载在婚/丧偶配偶，确保改嫁/续弦场景中配偶卡片显示在最终丈夫身旁
        for (FamilyRelationDO rel : spouseRelations) {
            Long spouseId = Objects.equals(rel.getFromNodeId(), node.getId()) ? rel.getToNodeId() : rel.getFromNodeId();
            if (childIds.contains(spouseId)) {
                continue;
            }
            boolean divorced = Boolean.TRUE.equals(rel.getDivorced()) || rel.getDivorceDate() != null;
            if (divorced) {
                continue;
            }
            FamilyNodeDO spouseNode = nodeMap.get(spouseId);
            if (spouseNode == null || visited.contains(spouseId)) {
                continue;
            }
            // 已被其他节点挂载为卫星配偶的不再重复挂载
            if (globalSatelliteSpouseIds.contains(spouseId)) {
                continue;
            }
            // 改嫁/续弦争议：只有优先丈夫（关系ID最大）才能挂载为卫星配偶
            if (contestedSpouseSet.contains(spouseId)
                    && !Objects.equals(contestedPreferredHusband.get(spouseId), node.getId())) {
                continue;
            }
            boolean widowed = Boolean.TRUE.equals(rel.getWidowed());
            visited.add(spouseId);
            satelliteSpouseIds.add(spouseId);
            globalSatelliteSpouseIds.add(spouseId);
            TreeNodeVO spouseVO = toVO(spouseNode);
            spouseVO.setRelationId(rel.getId());
            spouseVO.setWidowed(widowed);
            spouseVO.setMarriageDate(rel.getMarriageDate() != null ? rel.getMarriageDate().toString() : null);
            spouseVO.setDivorceDate(rel.getDivorceDate() != null ? rel.getDivorceDate().toString() : null);
            TreeNodeVO husbandRef = toVO(node);
            husbandRef.setRelationId(rel.getId());
            husbandRef.setWidowed(widowed);
            husbandRef.setMarriageDate(rel.getMarriageDate() != null ? rel.getMarriageDate().toString() : null);
            husbandRef.setDivorceDate(rel.getDivorceDate() != null ? rel.getDivorceDate().toString() : null);
            spouseVO.getSpouses().add(husbandRef);
            vo.getSpouses().add(spouseVO);
        }

        // 第二遍：离异 → 前配偶（已被其他节点挂载为卫星的不再重复列入）
        //        丧偶 → 前配偶（即使已被挂载为卫星也列入，保证前夫详情中可见婚姻历史）
        for (FamilyRelationDO rel : spouseRelations) {
            Long spouseId = Objects.equals(rel.getFromNodeId(), node.getId()) ? rel.getToNodeId() : rel.getFromNodeId();
            if (childIds.contains(spouseId)) {
                continue;
            }
            boolean divorced = Boolean.TRUE.equals(rel.getDivorced()) || rel.getDivorceDate() != null;
            boolean widowed = Boolean.TRUE.equals(rel.getWidowed());
            if (!divorced && !widowed) {
                continue;
            }
            FamilyNodeDO spouseNode = nodeMap.get(spouseId);
            if (spouseNode == null) {
                continue;
            }
            // 本节点已在第一遍挂载为卫星的配偶 → 跳过（避免同时出现在配偶和前配偶列表）
            if (satelliteSpouseIds.contains(spouseId)) {
                continue;
            }
            // 离异配偶已被其他节点挂载为卫星 → 跳过
            if (divorced && globalSatelliteSpouseIds.contains(spouseId)) {
                continue;
            }
            TreeNodeVO formerVO = toVO(spouseNode);
            formerVO.setRelationId(rel.getId());
            formerVO.setDivorced(divorced);
            formerVO.setWidowed(widowed);
            formerVO.setMarriageDate(rel.getMarriageDate() != null ? rel.getMarriageDate().toString() : null);
            formerVO.setDivorceDate(rel.getDivorceDate() != null ? rel.getDivorceDate().toString() : null);
            // 反向引用：前配偶详情弹框中可看到与本节点的配偶关系
            TreeNodeVO reverseRef = toVO(node);
            reverseRef.setRelationId(rel.getId());
            reverseRef.setDivorced(divorced);
            reverseRef.setWidowed(widowed);
            reverseRef.setMarriageDate(rel.getMarriageDate() != null ? rel.getMarriageDate().toString() : null);
            reverseRef.setDivorceDate(rel.getDivorceDate() != null ? rel.getDivorceDate().toString() : null);
            formerVO.getSpouses().add(reverseRef);
            vo.getFormerSpouses().add(formerVO);
        }

        Set<Long> parentIds = new HashSet<>();
        parentIds.add(node.getId());
        parentIds.addAll(satelliteSpouseIds);

        List<Long> childrenIds = parentIds.stream()
                .flatMap(pid -> childIdsByParent.getOrDefault(pid, List.of()).stream())
                .distinct()
                .collect(Collectors.toList());

        List<FamilyNodeDO> childNodes = childrenIds.stream()
                .map(nodeMap::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(FamilyNodeDO::getBirthOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        for (FamilyNodeDO childNode : childNodes) {
            TreeNodeVO childVO = buildSubTree(childNode, spouseRelByNode, childIdsByParent, parentIdsByChild, nodeMap, visited, childIds, globalSatelliteSpouseIds, contestedSpouseSet, contestedPreferredHusband);
            vo.getChildren().add(childVO);
        }

        return vo;
    }

    private boolean hasCurrentMarriageElsewhere(Long spouseId, Long excludeNodeId,
                                                Map<Long, List<FamilyRelationDO>> spouseRelByNode) {
        List<FamilyRelationDO> relations = spouseRelByNode.getOrDefault(spouseId, List.of());
        for (FamilyRelationDO rel : relations) {
            Long partner = Objects.equals(rel.getFromNodeId(), spouseId) ? rel.getToNodeId() : rel.getFromNodeId();
            boolean relDivorced = Boolean.TRUE.equals(rel.getDivorced()) || rel.getDivorceDate() != null;
            boolean relWidowed = Boolean.TRUE.equals(rel.getWidowed());
            if (!relDivorced && !relWidowed && !Objects.equals(partner, excludeNodeId)) {
                return true;
            }
        }
        return false;
    }

    private String bloodRelationLabel(Long aId, Long bId, Map<Long, List<Long>> parentIdsByChild) {
        int distance = cousinDistance(aId, bId, parentIdsByChild);
        if (distance == 2) {
            return "亲表兄妹";
        }
        if (distance == 3) {
            return "堂表兄妹";
        }
        if (distance >= 4) {
            return "远房表亲";
        }
        return "血亲";
    }

    private int cousinDistance(Long aId, Long bId, Map<Long, List<Long>> parentIdsByChild) {
        Map<Long, Integer> aAncestors = ancestorDistances(aId, parentIdsByChild);
        Map<Long, Integer> bAncestors = ancestorDistances(bId, parentIdsByChild);
        int best = Integer.MAX_VALUE;
        for (Map.Entry<Long, Integer> entry : aAncestors.entrySet()) {
            Integer bDist = bAncestors.get(entry.getKey());
            if (bDist != null) {
                best = Math.min(best, Math.max(entry.getValue(), bDist));
            }
        }
        return best;
    }

    private Map<Long, Integer> ancestorDistances(Long startId, Map<Long, List<Long>> parentIdsByChild) {
        Map<Long, Integer> distances = new HashMap<>();
        Deque<Long> queue = new ArrayDeque<>();
        distances.put(startId, 0);
        queue.add(startId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            int currentDist = distances.get(current);
            for (Long parentId : parentIdsByChild.getOrDefault(current, List.of())) {
                if (!distances.containsKey(parentId)) {
                    distances.put(parentId, currentDist + 1);
                    queue.add(parentId);
                }
            }
        }
        return distances;
    }

    private TreeNodeVO toVO(FamilyNodeDO entity) {
        TreeNodeVO vo = new TreeNodeVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setGender(entity.getGender());
        vo.setBirthDate(entity.getBirthDate() != null ? entity.getBirthDate().toString() : null);
        vo.setDeathDate(entity.getDeathDate() != null ? entity.getDeathDate().toString() : null);
        vo.setGeneration(entity.getGeneration());
        vo.setBirthOrder(entity.getBirthOrder());
        vo.setColorLabel(entity.getColorLabel());
        vo.setColorHex(ColorLabelEnum.fromCode(entity.getColorLabel()).getHexColor());
        vo.setAvatar(entity.getAvatar());
        vo.setRemark(entity.getRemark());
        return vo;
    }

    private List<FamilyNodeDO> listFamilyNodes(Long familyId) {
        LambdaQueryWrapper<FamilyNodeDO> query = new LambdaQueryWrapper<>();
        query.eq(FamilyNodeDO::getFamilyId, familyId)
                .orderByAsc(FamilyNodeDO::getId);
        return familyNodeMapper.selectList(query);
    }

    private List<FamilyRelationDO> listFamilyRelations(Long familyId) {
        LambdaQueryWrapper<FamilyRelationDO> query = new LambdaQueryWrapper<>();
        query.eq(FamilyRelationDO::getFamilyId, familyId);
        return familyRelationMapper.selectList(query);
    }
}
