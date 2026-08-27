package com.mouhin.family.tree.domain.service;

import com.mouhin.family.tree.common.dto.TreeNodeVO;
import com.mouhin.family.tree.common.enums.ColorLabelEnum;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyRelation;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 族谱树构建领域服务。
 * <p>
 * 核心算法：从领域对象（节点 + 关系）构建前端展示所需的树形结构。
 * 包含血亲配偶识别、卫星配偶挂载、改嫁/续弦优先级等复杂业务规则。
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Service
public class FamilyTreeDomainService {

    /**
     * 从领域对象列表构建族谱树
     *
     * @param nodes     家族所有节点
     * @param relations 家族所有关系
     * @return 树形节点列表（可能有多个根节点）
     */
    public List<TreeNodeVO> buildTree(List<FamilyNode> nodes,
                                      List<FamilyRelation> relations) {
        if (nodes.isEmpty()) {
            return new ArrayList<>();
        }

        // 找出有父节点的节点（非根，含过继/收养）
        Set<Long> childIds = relations.stream()
                .filter(r -> r.isParentChild() || r.isAdoption())
                .map(FamilyRelation::getToNodeId)
                .collect(Collectors.toSet());

        // 预标记所有配偶关系中的"被添加方"（toNodeId），这些节点不作为独立根
        Set<Long> spouseAttachedIds = relations.stream()
                .filter(FamilyRelation::isSpouse)
                .map(FamilyRelation::getToNodeId)
                .collect(Collectors.toSet());

        List<FamilyNode> roots = nodes.stream()
                .filter(n -> !childIds.contains(n.getId()))
                .filter(n -> !spouseAttachedIds.contains(n.getId()))
                .toList();

        Map<Long, FamilyNode> nodeMap = nodes.stream()
                .collect(Collectors.toMap(FamilyNode::getId, n -> n, (a, b) -> a));

        // 预构建索引
        Map<Long, List<FamilyRelation>> spouseRelByNode = new HashMap<>();
        Map<Long, List<Long>> childIdsByParent = new HashMap<>();
        Map<Long, List<Long>> parentIdsByChild = new HashMap<>();
        buildRelationIndexes(relations, spouseRelByNode, childIdsByParent, parentIdsByChild);

        // 预计算：改嫁/续弦配偶的卫星挂载优先级
        Set<Long> contestedSpouseSet = new HashSet<>();
        Map<Long, Long> contestedPreferredHusband = new HashMap<>();
        calculateContestedSpouses(spouseRelByNode, contestedSpouseSet,
                contestedPreferredHusband);

        Set<Long> visited = new HashSet<>();
        Set<Long> globalSatelliteSpouseIds = new HashSet<>();
        List<TreeNodeVO> tree = new ArrayList<>();

        for (FamilyNode root : roots) {
            if (visited.contains(root.getId())) {
                continue;
            }
            TreeNodeVO vo = buildSubTree(root, spouseRelByNode, childIdsByParent,
                    parentIdsByChild, nodeMap, visited, childIds,
                    globalSatelliteSpouseIds, contestedSpouseSet,
                    contestedPreferredHusband);
            tree.add(vo);
        }

        return tree;
    }

    /**
     * 以指定节点为根构建子树
     *
     * @param rootNode     子树根节点
     * @param allNodes     家族所有节点
     * @param allRelations 家族所有关系
     * @return 子树形视图对象
     */
    public TreeNodeVO buildSubTreeForNode(FamilyNode rootNode,
                                          List<FamilyNode> allNodes,
                                          List<FamilyRelation> allRelations) {
        Map<Long, FamilyNode> nodeMap = allNodes.stream()
                .collect(Collectors.toMap(FamilyNode::getId, n -> n, (a, b) -> a));

        Map<Long, List<FamilyRelation>> spouseRelByNode = new HashMap<>();
        Map<Long, List<Long>> childIdsByParent = new HashMap<>();
        Map<Long, List<Long>> parentIdsByChild = new HashMap<>();
        buildRelationIndexes(allRelations, spouseRelByNode, childIdsByParent, parentIdsByChild);

        Set<Long> childIds = allRelations.stream()
                .filter(r -> r.isParentChild() || r.isAdoption())
                .map(FamilyRelation::getToNodeId)
                .collect(Collectors.toSet());

        Set<Long> contestedSpouseSet = new HashSet<>();
        Map<Long, Long> contestedPreferredHusband = new HashMap<>();
        calculateContestedSpouses(spouseRelByNode, contestedSpouseSet,
                contestedPreferredHusband);

        Set<Long> visited = new HashSet<>();
        Set<Long> globalSatelliteSpouseIds = new HashSet<>();

        return buildSubTree(rootNode, spouseRelByNode, childIdsByParent,
                parentIdsByChild, nodeMap, visited, childIds,
                globalSatelliteSpouseIds, contestedSpouseSet,
                contestedPreferredHusband);
    }

    /**
     * 构建子树（递归）
     */
    private TreeNodeVO buildSubTree(FamilyNode node,
                                    Map<Long, List<FamilyRelation>> spouseRelByNode,
                                    Map<Long, List<Long>> childIdsByParent,
                                    Map<Long, List<Long>> parentIdsByChild,
                                    Map<Long, FamilyNode> nodeMap,
                                    Set<Long> visited,
                                    Set<Long> childIds,
                                    Set<Long> globalSatelliteSpouseIds,
                                    Set<Long> contestedSpouseSet,
                                    Map<Long, Long> contestedPreferredHusband) {
        if (visited.contains(node.getId())) {
            return toVO(node);
        }
        visited.add(node.getId());

        TreeNodeVO vo = toVO(node);

        List<FamilyRelation> spouseRelations =
                spouseRelByNode.getOrDefault(node.getId(), List.of());

        Set<Long> satelliteSpouseIds = new HashSet<>();

        // 血亲配偶：双方均为族谱成员，各自保留本支
        for (FamilyRelation rel : spouseRelations) {
            Long spouseId = rel.getSpouseId(node.getId());
            if (!childIds.contains(spouseId)) {
                continue;
            }
            FamilyNode spouseNode = nodeMap.get(spouseId);
            if (spouseNode == null) {
                continue;
            }
            boolean divorced = Boolean.TRUE.equals(rel.getDivorced())
                    || rel.getDivorceDate() != null;
            boolean widowed = Boolean.TRUE.equals(rel.getWidowed());
            TreeNodeVO refVO = toVO(spouseNode);
            refVO.setRelationId(rel.getId());
            refVO.setDivorced(divorced);
            refVO.setWidowed(widowed);
            refVO.setMarriageDate(rel.getMarriageDate() != null
                    ? rel.getMarriageDate().toString() : null);
            refVO.setDivorceDate(rel.getDivorceDate() != null
                    ? rel.getDivorceDate().toString() : null);
            refVO.setBloodRelationLabel(
                    bloodRelationLabel(node.getId(), spouseId, parentIdsByChild));
            vo.getBloodSpouses().add(refVO);
        }

        // 第一遍：非离异（在婚 + 丧偶）→ 卫星配偶
        for (FamilyRelation rel : spouseRelations) {
            Long spouseId = rel.getSpouseId(node.getId());
            if (childIds.contains(spouseId)) {
                continue;
            }
            boolean divorced = Boolean.TRUE.equals(rel.getDivorced())
                    || rel.getDivorceDate() != null;
            if (divorced) {
                continue;
            }
            FamilyNode spouseNode = nodeMap.get(spouseId);
            if (spouseNode == null || visited.contains(spouseId)) {
                continue;
            }
            if (globalSatelliteSpouseIds.contains(spouseId)) {
                continue;
            }
            // 改嫁/续弦争议：只有优先丈夫才能挂载为卫星配偶
            if (contestedSpouseSet.contains(spouseId)
                    && !Objects.equals(
                    contestedPreferredHusband.get(spouseId), node.getId())) {
                continue;
            }
            boolean widowed = Boolean.TRUE.equals(rel.getWidowed());
            visited.add(spouseId);
            satelliteSpouseIds.add(spouseId);
            globalSatelliteSpouseIds.add(spouseId);
            TreeNodeVO spouseVO = toVO(spouseNode);
            spouseVO.setRelationId(rel.getId());
            spouseVO.setWidowed(widowed);
            spouseVO.setMarriageDate(rel.getMarriageDate() != null
                    ? rel.getMarriageDate().toString() : null);
            spouseVO.setDivorceDate(rel.getDivorceDate() != null
                    ? rel.getDivorceDate().toString() : null);
            // 反向引用
            TreeNodeVO reverseRef = toVO(node);
            reverseRef.setRelationId(rel.getId());
            reverseRef.setWidowed(widowed);
            reverseRef.setMarriageDate(rel.getMarriageDate() != null
                    ? rel.getMarriageDate().toString() : null);
            reverseRef.setDivorceDate(rel.getDivorceDate() != null
                    ? rel.getDivorceDate().toString() : null);
            spouseVO.getSpouses().add(reverseRef);
            vo.getSpouses().add(spouseVO);
        }

        // 第二遍：离异/丧偶 → 前配偶
        for (FamilyRelation rel : spouseRelations) {
            Long spouseId = rel.getSpouseId(node.getId());
            if (childIds.contains(spouseId)) {
                continue;
            }
            boolean divorced = Boolean.TRUE.equals(rel.getDivorced())
                    || rel.getDivorceDate() != null;
            boolean widowed = Boolean.TRUE.equals(rel.getWidowed());
            if (!divorced && !widowed) {
                continue;
            }
            FamilyNode spouseNode = nodeMap.get(spouseId);
            if (spouseNode == null) {
                continue;
            }
            if (satelliteSpouseIds.contains(spouseId)) {
                continue;
            }
            if (divorced && globalSatelliteSpouseIds.contains(spouseId)) {
                continue;
            }
            TreeNodeVO formerVO = toVO(spouseNode);
            formerVO.setRelationId(rel.getId());
            formerVO.setDivorced(divorced);
            formerVO.setWidowed(widowed);
            formerVO.setMarriageDate(rel.getMarriageDate() != null
                    ? rel.getMarriageDate().toString() : null);
            formerVO.setDivorceDate(rel.getDivorceDate() != null
                    ? rel.getDivorceDate().toString() : null);
            TreeNodeVO reverseRef = toVO(node);
            reverseRef.setRelationId(rel.getId());
            reverseRef.setDivorced(divorced);
            reverseRef.setWidowed(widowed);
            reverseRef.setMarriageDate(rel.getMarriageDate() != null
                    ? rel.getMarriageDate().toString() : null);
            reverseRef.setDivorceDate(rel.getDivorceDate() != null
                    ? rel.getDivorceDate().toString() : null);
            formerVO.getSpouses().add(reverseRef);
            vo.getFormerSpouses().add(formerVO);
        }

        // 收集子节点（本节点 + 卫星配偶的子女）
        Set<Long> parentIds = new HashSet<>();
        parentIds.add(node.getId());
        parentIds.addAll(satelliteSpouseIds);

        List<Long> childrenIds = parentIds.stream()
                .flatMap(pid -> childIdsByParent.getOrDefault(pid, List.of()).stream())
                .distinct()
                .collect(Collectors.toList());

        List<FamilyNode> childNodes = childrenIds.stream()
                .map(nodeMap::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(FamilyNode::getBirthOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        for (FamilyNode childNode : childNodes) {
            TreeNodeVO childVO = buildSubTree(childNode, spouseRelByNode,
                    childIdsByParent, parentIdsByChild, nodeMap, visited, childIds,
                    globalSatelliteSpouseIds, contestedSpouseSet,
                    contestedPreferredHusband);
            vo.getChildren().add(childVO);
        }

        return vo;
    }

    /**
     * 从全量关系列表构建三个查询索引
     */
    private void buildRelationIndexes(List<FamilyRelation> allRelations,
                                      Map<Long, List<FamilyRelation>> spouseRelByNode,
                                      Map<Long, List<Long>> childIdsByParent,
                                      Map<Long, List<Long>> parentIdsByChild) {
        for (FamilyRelation rel : allRelations) {
            if (rel.isSpouse()) {
                spouseRelByNode.computeIfAbsent(rel.getFromNodeId(),
                        k -> new ArrayList<>()).add(rel);
                spouseRelByNode.computeIfAbsent(rel.getToNodeId(),
                        k -> new ArrayList<>()).add(rel);
            } else if (rel.isParentChild() || rel.isAdoption()) {
                childIdsByParent.computeIfAbsent(rel.getFromNodeId(),
                        k -> new ArrayList<>()).add(rel.getToNodeId());
                parentIdsByChild.computeIfAbsent(rel.getToNodeId(),
                        k -> new ArrayList<>()).add(rel.getFromNodeId());
            }
        }
    }

    /**
     * 预计算改嫁/续弦场景中配偶的卫星挂载优先级。
     * 对每个有多段非离异婚姻的配偶，关系ID最大的丈夫优先获得卫星配偶卡片。
     */
    private void calculateContestedSpouses(
            Map<Long, List<FamilyRelation>> spouseRelByNode,
            Set<Long> contestedSpouseSet,
            Map<Long, Long> contestedPreferredHusband) {
        for (Map.Entry<Long, List<FamilyRelation>> entry
                : spouseRelByNode.entrySet()) {
            Long spouseId = entry.getKey();
            Long bestNodeId = null;
            Long bestRelId = null;
            int nonDivCount = 0;
            for (FamilyRelation rel : entry.getValue()) {
                boolean divorced = Boolean.TRUE.equals(rel.getDivorced())
                        || rel.getDivorceDate() != null;
                if (divorced) {
                    continue;
                }
                nonDivCount++;
                Long partnerId = rel.getSpouseId(spouseId);
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
    }

    /**
     * 计算血亲亲缘标签
     */
    private String bloodRelationLabel(Long aId, Long bId,
                                      Map<Long, List<Long>> parentIdsByChild) {
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

    /**
     * 计算共同祖先到两人的最大距离
     */
    private int cousinDistance(Long aId, Long bId,
                               Map<Long, List<Long>> parentIdsByChild) {
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

    /**
     * BFS 向上追溯祖先，记录每个祖先的距离
     */
    private Map<Long, Integer> ancestorDistances(Long startId,
                                                 Map<Long, List<Long>> parentIdsByChild) {
        Map<Long, Integer> distances = new HashMap<>();
        Deque<Long> queue = new ArrayDeque<>();
        distances.put(startId, 0);
        queue.add(startId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            int currentDist = distances.get(current);
            for (Long parentId
                    : parentIdsByChild.getOrDefault(current, List.of())) {
                if (!distances.containsKey(parentId)) {
                    distances.put(parentId, currentDist + 1);
                    queue.add(parentId);
                }
            }
        }
        return distances;
    }

    /**
     * 将领域节点转换为树形视图对象
     */
    private TreeNodeVO toVO(FamilyNode node) {
        TreeNodeVO vo = new TreeNodeVO();
        vo.setId(node.getId());
        vo.setName(node.getName());
        vo.setGender(node.getGender());
        vo.setBirthDate(node.getBirthDate() != null
                ? node.getBirthDate().toString() : null);
        vo.setDeathDate(node.getDeathDate() != null
                ? node.getDeathDate().toString() : null);
        vo.setGeneration(node.getGeneration());
        vo.setBirthOrder(node.getBirthOrder());
        vo.setColorLabel(node.getColorLabel());
        vo.setColorHex(ColorLabelEnum.fromCode(node.getColorLabel()).getHexColor());
        vo.setAvatar(node.getAvatar());
        vo.setRemark(node.getRemark());
        return vo;
    }
}
