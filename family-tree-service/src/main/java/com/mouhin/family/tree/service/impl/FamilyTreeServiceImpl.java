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

import java.util.ArrayList;
import java.util.Comparator;
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
        buildRelationIndexes(allRelations, spouseRelByNode, childIdsByParent);

        Set<Long> visited = new HashSet<>();
        List<TreeNodeVO> tree = new ArrayList<>();
        for (FamilyNodeDO root : roots) {
            if (visited.contains(root.getId())) {
                continue;
            }
            TreeNodeVO vo = buildSubTree(root, spouseRelByNode, childIdsByParent, nodeMap, visited, childIds);
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
        buildRelationIndexes(allRelations, spouseRelByNode, childIdsByParent);

        Set<Long> visited = new HashSet<>();
        return buildSubTree(node, spouseRelByNode, childIdsByParent, nodeMap, visited, childIds);
    }

    /**
     * 从全量关系列表构建两个查询索引：
     * spouseRelByNode —— 节点id → 涉及该节点的配偶关系（from/to 双向挂载）；
     * childIdsByParent —— 父节点id → 子女节点id列表。
     */
    private void buildRelationIndexes(List<FamilyRelationDO> allRelations,
                                      Map<Long, List<FamilyRelationDO>> spouseRelByNode,
                                      Map<Long, List<Long>> childIdsByParent) {
        for (FamilyRelationDO rel : allRelations) {
            if (Objects.equals(rel.getRelationType(), RelationTypeEnum.SPOUSE.getCode())) {
                spouseRelByNode.computeIfAbsent(rel.getFromNodeId(), k -> new ArrayList<>()).add(rel);
                spouseRelByNode.computeIfAbsent(rel.getToNodeId(), k -> new ArrayList<>()).add(rel);
            } else if (Objects.equals(rel.getRelationType(), RelationTypeEnum.PARENT_CHILD.getCode())) {
                childIdsByParent.computeIfAbsent(rel.getFromNodeId(), k -> new ArrayList<>()).add(rel.getToNodeId());
            }
        }
    }

    private TreeNodeVO buildSubTree(FamilyNodeDO node, Map<Long, List<FamilyRelationDO>> spouseRelByNode,
                                    Map<Long, List<Long>> childIdsByParent, Map<Long, FamilyNodeDO> nodeMap,
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
            boolean bloodSpouse = childIds.contains(spouseId);
            if (bloodSpouse) {
                // 血亲配偶：自身在族谱中有原生分支，不嵌入为卫星节点，仅保留引用，
                // 由前端在两个分支的卡片之间绘制跨分支连线。
                TreeNodeVO refVO = toVO(spouseNode);
                refVO.setRelationId(rel.getId());
                refVO.setDivorced(divorced);
                refVO.setMarriageDate(rel.getMarriageDate() != null ? rel.getMarriageDate().toString() : null);
                refVO.setDivorceDate(rel.getDivorceDate() != null ? rel.getDivorceDate().toString() : null);
                vo.getBloodSpouses().add(refVO);
            } else if (!visited.contains(spouseId)) {
                visited.add(spouseId);
                satelliteSpouseIds.add(spouseId);
                TreeNodeVO spouseVO = toVO(spouseNode);
                spouseVO.setRelationId(rel.getId());
                spouseVO.setDivorced(divorced);
                spouseVO.setMarriageDate(rel.getMarriageDate() != null ? rel.getMarriageDate().toString() : null);
                spouseVO.setDivorceDate(rel.getDivorceDate() != null ? rel.getDivorceDate().toString() : null);
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
            TreeNodeVO childVO = buildSubTree(childNode, spouseRelByNode, childIdsByParent, nodeMap, visited, childIds);
            vo.getChildren().add(childVO);
        }

        return vo;
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
