package com.mouhin.family.tree.domain.service;

import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyRelation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.PriorityQueue;
import java.util.Set;

/**
 * 世代动态修复领域服务。
 * <p>
 * 以树结构为世代唯一真值：从根节点出发逐级向下重算全部节点的第几世。
 * 规则：亲子/过继边向下 +1 世；夫妻边同世；无父辈边的根节点保留其现存世代作为全树锚点
 * （允许支系从第 N 世起算），入赘/嫁娶配偶不以自身为根，随配偶对齐。
 * 同一节点有多个父辈边时取最浅值（先按结构可达的最浅世代收敛）。
 * 仅存在于亲子环中、结构上不可从任何根到达的节点保持原值不动，留给人工处理。
 *
 * @author Family-Tree
 * @date 2026-11-19
 */
@Service
public class GenerationRepairDomainService {

    private static final Logger logger = LoggerFactory.getLogger(GenerationRepairDomainService.class);

    /**
     * 按树结构重算世代，回写差异节点的 generation 与内存值
     *
     * @param nodes     家族全部节点（调用方按家族加载）
     * @param relations 家族全部关系
     * @return 世代发生变化的节点列表（对象已就地更新 generation）
     */
    public List<FamilyNode> repair(List<FamilyNode> nodes, List<FamilyRelation> relations) {
        List<FamilyNode> changed = new ArrayList<>();
        if (nodes == null || nodes.isEmpty()) {
            return changed;
        }

        Map<Long, FamilyNode> nodeIndex = new HashMap<>((int) (nodes.size() / 0.75) + 1);
        for (FamilyNode node : nodes) {
            nodeIndex.put(node.getId(), node);
        }

        // 亲子边邻接表：父 → 子（过继/收养按亲子同规则处理）；反向记录有父辈边的节点
        Map<Long, List<Long>> parentToChildren = new HashMap<>((int) (nodes.size() / 0.75) + 1);
        Set<Long> nodesWithParent = new HashSet<>();
        // 配偶邻接表（双向）
        Map<Long, List<Long>> spouseAdj = new HashMap<>();
        for (FamilyRelation relation : relations) {
            FamilyNode from = nodeIndex.get(relation.getFromNodeId());
            FamilyNode to = nodeIndex.get(relation.getToNodeId());
            if (from == null || to == null) {
                continue;
            }
            if (relation.isParentChild() || relation.isAdoption()) {
                parentToChildren.computeIfAbsent(relation.getFromNodeId(), k -> new ArrayList<>())
                        .add(relation.getToNodeId());
                nodesWithParent.add(relation.getToNodeId());
            } else if (relation.isSpouse()) {
                spouseAdj.computeIfAbsent(relation.getFromNodeId(), k -> new ArrayList<>())
                        .add(relation.getToNodeId());
                spouseAdj.computeIfAbsent(relation.getToNodeId(), k -> new ArrayList<>())
                        .add(relation.getFromNodeId());
            }
        }

        // 种子层：无父辈边、且不是"嫁给/娶了有父辈边节点"的入嫁配偶；根节点保留现存世代作锚点
        Map<Long, Integer> levelMap = new HashMap<>((int) (nodes.size() / 0.75) + 1);
        PriorityQueue<long[]> queue = new PriorityQueue<>(Comparator.comparingInt(entry -> (int) entry[1]));
        for (FamilyNode node : nodes) {
            if (nodesWithParent.contains(node.getId())) {
                continue;
            }
            if (isMarriedIntoLineage(node.getId(), spouseAdj, nodesWithParent)) {
                continue;
            }
            int anchor = node.getGeneration() == null || node.getGeneration() < 1
                    ? FamilyTreeConsts.DEFAULT_GENERATION : node.getGeneration();
            levelMap.put(node.getId(), anchor);
            queue.offer(new long[]{node.getId(), anchor});
        }

        // 0/1 权重最短路松弛：配偶边同层（权重 0），亲子边加一层（权重 1），取可达最浅值
        while (!queue.isEmpty()) {
            long[] current = queue.poll();
            long currentId = current[0];
            int currentGen = (int) current[1];
            if (currentGen > levelMap.getOrDefault(currentId, Integer.MAX_VALUE)) {
                continue;
            }
            relaxChildren(parentToChildren.get(currentId), currentGen + 1, levelMap, queue);
            relaxSameLevel(spouseAdj.get(currentId), currentGen, levelMap, queue);
        }

        for (FamilyNode node : nodes) {
            Integer target = levelMap.get(node.getId());
            if (target == null || target > FamilyTreeConsts.MAX_GENERATION_DEPTH) {
                continue;
            }
            if (!Objects.equals(node.getGeneration(), target)) {
                node.setGeneration(target);
                changed.add(node);
            }
        }
        if (!changed.isEmpty()) {
            logger.info("Generation repair computed {} node(s) to update", changed.size());
        }
        return changed;
    }

    /**
     * 判断无父辈边的节点是否为入嫁/入赘配偶：其配偶带有父辈边，则世代应随配偶而非作为独立根
     */
    private boolean isMarriedIntoLineage(Long nodeId, Map<Long, List<Long>> spouseAdj,
                                         Set<Long> nodesWithParent) {
        List<Long> spouses = spouseAdj.get(nodeId);
        if (spouses == null) {
            return false;
        }
        for (Long spouseId : spouses) {
            if (nodesWithParent.contains(spouseId)) {
                return true;
            }
        }
        return false;
    }

    private void relaxChildren(List<Long> childIds, int childGen,
                               Map<Long, Integer> levelMap, PriorityQueue<long[]> queue) {
        if (childIds == null) {
            return;
        }
        for (Long childId : childIds) {
            relax(childId, childGen, levelMap, queue);
        }
    }

    private void relaxSameLevel(List<Long> spouseIds, int gen,
                                Map<Long, Integer> levelMap, PriorityQueue<long[]> queue) {
        if (spouseIds == null) {
            return;
        }
        for (Long spouseId : spouseIds) {
            relax(spouseId, gen, levelMap, queue);
        }
    }

    /**
     * 松弛更新节点世代：仅在取得更浅值时入队，保证收敛且与遍历顺序无关
     */
    private void relax(Long nodeId, int gen, Map<Long, Integer> levelMap, PriorityQueue<long[]> queue) {
        if (gen < levelMap.getOrDefault(nodeId, Integer.MAX_VALUE)) {
            levelMap.put(nodeId, gen);
            queue.offer(new long[]{nodeId, gen});
        }
    }
}
