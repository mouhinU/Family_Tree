package com.mouhin.family.tree.domain.service;

import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.enums.RelationTypeEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyRelation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 族谱节点世代计算领域服务。
 * <p>
 * 负责节点世代的计算、同步（BFS 级联更新后代及配偶世代）。
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Service
public class FamilyNodeDomainService {

    private static final Logger logger =
            LoggerFactory.getLogger(FamilyNodeDomainService.class);

    /**
     * 计算新节点的世代层级
     *
     * @param parentNodeId 父节点ID（可为null）
     * @param childNodeId  子节点ID（可为null，表示当前节点作为父插入）
     * @param spouseNodeId 配偶节点ID（可为null）
     * @param nodeIndex    节点ID → 节点索引
     * @param relations    所有关系列表
     * @return 计算后的世代层级
     */
    public int calculateGeneration(Long parentNodeId, Long childNodeId,
                                    Long spouseNodeId,
                                    Map<Long, FamilyNode> nodeIndex,
                                    List<FamilyRelation> relations) {
        if (parentNodeId != null) {
            FamilyNode parent = nodeIndex.get(parentNodeId);
            if (parent == null) {
                throw new BusinessException("父节点不存在");
            }
            return parent.getGeneration() + 1;
        }
        if (childNodeId != null) {
            FamilyNode child = nodeIndex.get(childNodeId);
            if (child == null) {
                throw new BusinessException("子节点不存在");
            }
            return Math.max(child.getGeneration() - 1, 1);
        }
        if (spouseNodeId != null) {
            FamilyNode spouse = nodeIndex.get(spouseNodeId);
            if (spouse == null) {
                throw new BusinessException("配偶节点不存在");
            }
            return spouse.getGeneration();
        }
        return FamilyTreeConsts.DEFAULT_GENERATION;
    }

    /**
     * BFS 同步后代节点的世代层级。
     * <p>
     * 从起始节点开始，通过亲子关系的邻接表进行 BFS，
     * 每个子节点世代 = 父节点世代 + 1，同时同步配偶世代。
     *
     * @param startNodeId  起始节点ID
     * @param newGeneration 起始节点的新世代
     * @param allNodes     所有节点列表
     * @param allRelations 所有关系列表
     * @return 已更新世代的节点列表（不含起始节点本身）
     */
    public List<FamilyNode> syncDescendantGenerations(Long startNodeId,
                                                       int newGeneration,
                                                       List<FamilyNode> allNodes,
                                                       List<FamilyRelation> allRelations) {
        Map<Long, FamilyNode> nodeIndex = new HashMap<>();
        for (FamilyNode node : allNodes) {
            nodeIndex.put(node.getId(), node);
        }

        // 构建亲子邻接表
        Map<Long, List<Long>> childrenMap = new HashMap<>();
        for (FamilyRelation rel : allRelations) {
            if (Objects.equals(rel.getRelationType(),
                    RelationTypeEnum.PARENT_CHILD.getCode())) {
                childrenMap.computeIfAbsent(rel.getFromNodeId(),
                        k -> new ArrayList<>()).add(rel.getToNodeId());
            }
        }

        // 构建配偶邻接表（双向）
        Map<Long, List<Long>> spouseMap = new HashMap<>();
        for (FamilyRelation rel : allRelations) {
            if (Objects.equals(rel.getRelationType(),
                    RelationTypeEnum.SPOUSE.getCode())) {
                spouseMap.computeIfAbsent(rel.getFromNodeId(),
                        k -> new ArrayList<>()).add(rel.getToNodeId());
                spouseMap.computeIfAbsent(rel.getToNodeId(),
                        k -> new ArrayList<>()).add(rel.getFromNodeId());
            }
        }

        // BFS 遍历
        Set<Long> visited = new HashSet<>();
        Deque<long[]> queue = new ArrayDeque<>();
        queue.offer(new long[]{startNodeId, newGeneration});
        visited.add(startNodeId);

        List<FamilyNode> updatedNodes = new ArrayList<>();

        while (!queue.isEmpty()) {
            long[] current = queue.poll();
            long currentId = current[0];
            int currentGen = (int) current[1];

            List<Long> childIds = childrenMap.getOrDefault(currentId,
                    List.of());
            for (Long childId : childIds) {
                if (visited.contains(childId)) {
                    continue;
                }
                visited.add(childId);
                int childGen = currentGen + 1;
                if (childGen > FamilyTreeConsts.MAX_GENERATION_DEPTH) {
                    throw new BusinessException(
                            "世代层级不能超过"
                                    + FamilyTreeConsts.MAX_GENERATION_DEPTH + "世");
                }
                FamilyNode childNode = nodeIndex.get(childId);
                if (childNode != null) {
                    childNode.setGeneration(childGen);
                    updatedNodes.add(childNode);
                }

                // 同步配偶世代
                List<Long> spouseIds = spouseMap.getOrDefault(childId,
                        List.of());
                for (Long spouseId : spouseIds) {
                    FamilyNode spouseNode = nodeIndex.get(spouseId);
                    if (spouseNode != null) {
                        spouseNode.setGeneration(childGen);
                        updatedNodes.add(spouseNode);
                    }
                }

                queue.offer(new long[]{childId, childGen});
            }
        }

        logger.info("Synced descendant generations from node={} gen={} ({} nodes updated)",
                startNodeId, newGeneration, updatedNodes.size());

        return updatedNodes;
    }

    /**
     * 同步配偶的世代层级与指定节点一致
     *
     * @param node       当前节点
     * @param relations  所有关系列表
     * @param allNodes   所有节点列表
     */
    public void syncSpouseGeneration(FamilyNode node,
                                      List<FamilyRelation> relations,
                                      List<FamilyNode> allNodes) {
        Map<Long, FamilyNode> nodeIndex = new HashMap<>();
        for (FamilyNode n : allNodes) {
            nodeIndex.put(n.getId(), n);
        }

        for (FamilyRelation rel : relations) {
            if (!rel.isSpouse()) {
                continue;
            }
            Long spouseId = rel.getSpouseId(node.getId());
            if (!Objects.equals(rel.getFromNodeId(), node.getId())
                    && !Objects.equals(rel.getToNodeId(), node.getId())) {
                continue;
            }
            FamilyNode spouseNode = nodeIndex.get(spouseId);
            if (spouseNode != null
                    && !Objects.equals(spouseNode.getGeneration(),
                            node.getGeneration())) {
                spouseNode.setGeneration(node.getGeneration());
            }
        }
    }
}
