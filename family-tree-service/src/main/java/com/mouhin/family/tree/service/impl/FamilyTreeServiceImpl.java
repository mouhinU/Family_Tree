package com.mouhin.family.tree.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    public FamilyTreeServiceImpl(FamilyNodeMapper familyNodeMapper, FamilyRelationMapper familyRelationMapper) {
        this.familyNodeMapper = familyNodeMapper;
        this.familyRelationMapper = familyRelationMapper;
    }

    @Override
    public List<TreeNodeVO> getFullTree(Long userId) {
        List<FamilyNodeDO> allNodes = listUserNodes(userId);
        List<FamilyRelationDO> allRelations = listUserRelations(userId);

        if (allNodes.isEmpty()) {
            return new ArrayList<>();
        }

        // 找出有父节点的节点（非根）
        Set<Long> childIds = allRelations.stream()
                .filter(r -> Objects.equals(r.getRelationType(), RelationTypeEnum.PARENT_CHILD.getCode()))
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

        Set<Long> visited = new HashSet<>();
        List<TreeNodeVO> tree = new ArrayList<>();
        for (FamilyNodeDO root : roots) {
            if (visited.contains(root.getId())) {
                continue;
            }
            TreeNodeVO vo = buildSubTree(root, spouseRelByNode, childIdsByParent, parentIdsByChild, nodeMap, visited, childIds);
            tree.add(vo);
        }
        return tree;
    }

    @Override
    public TreeNodeVO getSubTree(Long userId, Long nodeId) {
        FamilyNodeDO node = familyNodeMapper.selectById(nodeId);
        if (node == null || !Objects.equals(node.getUserId(), userId)) {
            throw new BusinessException("节点不存在或无权操作");
        }

        List<FamilyRelationDO> allRelations = listUserRelations(userId);
        Map<Long, FamilyNodeDO> nodeMap = listUserNodes(userId).stream()
                .collect(Collectors.toMap(FamilyNodeDO::getId, n -> n, (a, b) -> a));

        Set<Long> childIds = allRelations.stream()
                .filter(r -> Objects.equals(r.getRelationType(), RelationTypeEnum.PARENT_CHILD.getCode()))
                .map(FamilyRelationDO::getToNodeId)
                .collect(Collectors.toSet());

        Map<Long, List<FamilyRelationDO>> spouseRelByNode = new HashMap<>();
        Map<Long, List<Long>> childIdsByParent = new HashMap<>();
        Map<Long, List<Long>> parentIdsByChild = new HashMap<>();
        buildRelationIndexes(allRelations, spouseRelByNode, childIdsByParent, parentIdsByChild);

        Set<Long> visited = new HashSet<>();
        return buildSubTree(node, spouseRelByNode, childIdsByParent, parentIdsByChild, nodeMap, visited, childIds);
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
            } else if (Objects.equals(rel.getRelationType(), RelationTypeEnum.PARENT_CHILD.getCode())) {
                childIdsByParent.computeIfAbsent(rel.getFromNodeId(), k -> new ArrayList<>()).add(rel.getToNodeId());
                parentIdsByChild.computeIfAbsent(rel.getToNodeId(), k -> new ArrayList<>()).add(rel.getFromNodeId());
            }
        }
    }

    private TreeNodeVO buildSubTree(FamilyNodeDO node, Map<Long, List<FamilyRelationDO>> spouseRelByNode,
                                    Map<Long, List<Long>> childIdsByParent, Map<Long, List<Long>> parentIdsByChild,
                                    Map<Long, FamilyNodeDO> nodeMap,
                                    Set<Long> visited, Set<Long> childIds) {
        if (visited.contains(node.getId())) {
            return toVO(node);
        }
        visited.add(node.getId());

        TreeNodeVO vo = toVO(node);

        // 查找配偶（携带关系元数据）：直接命中索引，无需全量扫描
        List<FamilyRelationDO> spouseRelations = spouseRelByNode.getOrDefault(node.getId(), List.of());

        // 仅"卫星配偶"（嫁入/入赘、自身无原生分支）参与子女归集；
        // 血亲配偶（如表兄妹结婚）保留在其原生分支，子女挂在添加时所选的父/母之下，避免重复挂载。
        Set<Long> satelliteSpouseIds = new HashSet<>();
        for (FamilyRelationDO rel : spouseRelations) {
            Long spouseId = Objects.equals(rel.getFromNodeId(), node.getId()) ? rel.getToNodeId() : rel.getFromNodeId();
            FamilyNodeDO spouseNode = nodeMap.get(spouseId);
            if (spouseNode == null) {
                continue;
            }
            boolean divorced = Boolean.TRUE.equals(rel.getDivorced()) || rel.getDivorceDate() != null;
            boolean widowed = Boolean.TRUE.equals(rel.getWidowed());
            boolean bloodSpouse = childIds.contains(spouseId);
            if (bloodSpouse) {
                // 血亲配偶：自身在族谱中有原生分支，不嵌入为卫星节点，仅保留引用，
                // 由前端在两个分支的卡片之间绘制跨分支连线。
                TreeNodeVO refVO = toVO(spouseNode);
                refVO.setRelationId(rel.getId());
                refVO.setDivorced(divorced);
                refVO.setWidowed(widowed);
                refVO.setMarriageDate(rel.getMarriageDate() != null ? rel.getMarriageDate().toString() : null);
                refVO.setDivorceDate(rel.getDivorceDate() != null ? rel.getDivorceDate().toString() : null);
                // 按距最近共同祖先的世代数标注亲缘（亲表兄妹 / 堂表兄妹 / 远房表亲）
                refVO.setBloodRelationLabel(bloodRelationLabel(node.getId(), spouseId, parentIdsByChild));
                vo.getBloodSpouses().add(refVO);
            } else if ((divorced || widowed) && hasCurrentMarriageElsewhere(spouseId, node.getId(), spouseRelByNode)) {
                // 卫星配偶已离异/丧偶且改嫁/再婚至他处：其本人将作为卫星节点挂到当前配偶名下，
                // 本节点仅保留引用（供详情弹窗展示），不嵌入卫星节点、不标记 visited。
                TreeNodeVO formerVO = toVO(spouseNode);
                formerVO.setRelationId(rel.getId());
                formerVO.setDivorced(divorced);
                formerVO.setWidowed(widowed);
                formerVO.setMarriageDate(rel.getMarriageDate() != null ? rel.getMarriageDate().toString() : null);
                formerVO.setDivorceDate(rel.getDivorceDate() != null ? rel.getDivorceDate().toString() : null);
                vo.getFormerSpouses().add(formerVO);
            } else if (!visited.contains(spouseId)) {
                visited.add(spouseId);
                satelliteSpouseIds.add(spouseId);
                TreeNodeVO spouseVO = toVO(spouseNode);
                spouseVO.setRelationId(rel.getId());
                spouseVO.setDivorced(divorced);
                spouseVO.setWidowed(widowed);
                spouseVO.setMarriageDate(rel.getMarriageDate() != null ? rel.getMarriageDate().toString() : null);
                spouseVO.setDivorceDate(rel.getDivorceDate() != null ? rel.getDivorceDate().toString() : null);
                // 配偶节点详情需回显丈夫信息：将本节点（丈夫）以浅引用形式加入配偶的配偶列表。
                // toVO 生成的新对象其子列表均为空，不会与外层 vo 构成循环引用。
                TreeNodeVO husbandRef = toVO(node);
                husbandRef.setRelationId(rel.getId());
                husbandRef.setDivorced(divorced);
                husbandRef.setWidowed(widowed);
                husbandRef.setMarriageDate(rel.getMarriageDate() != null ? rel.getMarriageDate().toString() : null);
                husbandRef.setDivorceDate(rel.getDivorceDate() != null ? rel.getDivorceDate().toString() : null);
                spouseVO.getSpouses().add(husbandRef);
                vo.getSpouses().add(spouseVO);
            }
        }

        // 查找子节点（当前节点或其卫星配偶为父节点的）：从索引取并去重
        Set<Long> parentIds = new HashSet<>();
        parentIds.add(node.getId());
        parentIds.addAll(satelliteSpouseIds);

        List<Long> childrenIds = parentIds.stream()
                .flatMap(pid -> childIdsByParent.getOrDefault(pid, List.of()).stream())
                .distinct()
                .collect(Collectors.toList());

        // 按同胞排次升序排列子节点（未设置排次的排在最后）
        List<FamilyNodeDO> childNodes = childrenIds.stream()
                .map(nodeMap::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(FamilyNodeDO::getBirthOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        for (FamilyNodeDO childNode : childNodes) {
            TreeNodeVO childVO = buildSubTree(childNode, spouseRelByNode, childIdsByParent, parentIdsByChild, nodeMap, visited, childIds);
            vo.getChildren().add(childVO);
        }

        return vo;
    }

    /**
     * 判断卫星配偶是否在本节点之外另有一段在婚（未离异）的婚姻。
     * 用于"改嫁/再婚"场景：离异的前任配偶仅保留引用，本人归属其当前配偶名下渲染。
     *
     * @param spouseId        配偶节点 ID
     * @param excludeNodeId   需排除的节点 ID（即当前正在构建的节点）
     * @param spouseRelByNode 配偶关系索引
     * @return 若该配偶与他人存在未离异婚姻返回 true
     */
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

    /**
     * 计算两个血亲节点的亲缘标签（用于血亲配偶展示）。
     * 以距最近共同祖先的世代数区分：2 代（共享祖父母）= 亲表兄妹，
     * 3 代（共享曾祖父母）= 堂表兄妹，更远 = 远房表亲；无共同祖先时回退"血亲"。
     *
     * @param aId            节点 A 的 ID
     * @param bId            节点 B 的 ID
     * @param parentIdsByChild 子女id → 父母id列表索引
     * @return 亲缘标签文案
     */
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

    /**
     * 求两节点距其最近共同祖先的世代数（同辈血亲两侧距离相等，取较大值即可）。
     *
     * @param aId            节点 A 的 ID
     * @param bId            节点 B 的 ID
     * @param parentIdsByChild 子女id → 父母id列表索引
     * @return 最近共同祖先的世代距离；无共同祖先返回 Integer.MAX_VALUE
     */
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

    /**
     * 广度优先向上追溯，求某节点到其全部祖先（含自身，自身距离为 0）的世代距离。
     *
     * @param startId          起始节点 ID
     * @param parentIdsByChild 子女id → 父母id列表索引
     * @return 祖先id → 世代距离 映射
     */
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

    private List<FamilyNodeDO> listUserNodes(Long userId) {
        LambdaQueryWrapper<FamilyNodeDO> query = new LambdaQueryWrapper<>();
        query.eq(FamilyNodeDO::getUserId, userId)
                .orderByAsc(FamilyNodeDO::getId);
        return familyNodeMapper.selectList(query);
    }

    private List<FamilyRelationDO> listUserRelations(Long userId) {
        LambdaQueryWrapper<FamilyRelationDO> query = new LambdaQueryWrapper<>();
        query.eq(FamilyRelationDO::getUserId, userId);
        return familyRelationMapper.selectList(query);
    }
}
