package com.mouhin.family.tree.domain.service;

import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyRelation;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * 关系路径领域服务。
 * <p>
 * 使用 BFS 算法计算两个族谱节点之间的最短关系路径。
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Service
public class RelationPathDomainService {

    private static final Logger logger =
            LoggerFactory.getLogger(RelationPathDomainService.class);

    /**
     * 关系路径步骤
     */
    @Getter
    public static class RelationPathStep {

        private final Long nodeId;
        private final String name;
        private final Integer relationType;
        private final Long relationId;

        /**
         * 构造路径步骤
         *
         * @param nodeId       节点ID
         * @param name         节点名称
         * @param relationType 与下一个节点的关系类型（最后一步为null）
         * @param relationId   关系ID（最后一步为null）
         */
        public RelationPathStep(Long nodeId, String name,
                                 Integer relationType, Long relationId) {
            this.nodeId = nodeId;
            this.name = name;
            this.relationType = relationType;
            this.relationId = relationId;
        }
    }

    /**
     * 路径查找结果
     */
    @Getter
    public static class PathResult {

        private final boolean found;
        private final List<RelationPathStep> steps;
        private final String message;

        /**
         * 构造路径结果
         *
         * @param found   是否找到路径
         * @param steps   路径步骤列表
         * @param message 结果描述
         */
        public PathResult(boolean found, List<RelationPathStep> steps,
                           String message) {
            this.found = found;
            this.steps = steps;
            this.message = message;
        }
    }

    /**
     * 使用 BFS 查找两个节点之间的最短关系路径
     *
     * @param fromNodeId 起始节点ID
     * @param toNodeId   目标节点ID
     * @param relations  所有关系列表
     * @param nodes      所有节点列表
     * @return 路径结果
     */
    public PathResult findShortestPath(Long fromNodeId, Long toNodeId,
                                        List<FamilyRelation> relations,
                                        List<FamilyNode> nodes) {
        // 构建节点名称索引
        Map<Long, String> nameMap = new HashMap<>();
        for (FamilyNode node : nodes) {
            nameMap.put(node.getId(), node.getName());
        }

        // 构建双向邻接表：nodeId → [(neighborId, relationType, relationId)]
        Map<Long, List<long[]>> adjacency = new HashMap<>();
        for (FamilyRelation rel : relations) {
            adjacency.computeIfAbsent(rel.getFromNodeId(),
                    k -> new ArrayList<>())
                    .add(new long[]{rel.getToNodeId(),
                            rel.getRelationType(), rel.getId()});
            adjacency.computeIfAbsent(rel.getToNodeId(),
                    k -> new ArrayList<>())
                    .add(new long[]{rel.getFromNodeId(),
                            rel.getRelationType(), rel.getId()});
        }

        // BFS
        Queue<Long> queue = new LinkedList<>();
        Map<Long, Long> parentMap = new HashMap<>();
        // 记录从哪个边到达的：child → [relationType, relationId]
        Map<Long, long[]> edgeMap = new HashMap<>();
        Set<Long> visited = new HashSet<>();

        queue.offer(fromNodeId);
        visited.add(fromNodeId);
        parentMap.put(fromNodeId, -1L);

        boolean found = false;
        while (!queue.isEmpty() && !found) {
            Long current = queue.poll();
            List<long[]> neighbors = adjacency.getOrDefault(current,
                    List.of());
            for (long[] neighbor : neighbors) {
                long nextId = neighbor[0];
                if (!visited.contains(nextId)) {
                    visited.add(nextId);
                    parentMap.put(nextId, current);
                    edgeMap.put(nextId, new long[]{neighbor[1], neighbor[2]});
                    queue.offer(nextId);
                    if (nextId == toNodeId) {
                        found = true;
                        break;
                    }
                }
            }
        }

        if (!found) {
            return new PathResult(false, List.of(),
                    "两个节点之间没有关系路径");
        }

        // 回溯路径
        List<Long> path = new ArrayList<>();
        Long current = toNodeId;
        while (current != -1L) {
            path.add(0, current);
            current = parentMap.get(current);
        }

        // 构建路径步骤
        List<RelationPathStep> steps = new ArrayList<>();
        for (int i = 0; i < path.size(); i++) {
            Long nodeId = path.get(i);
            String name = nameMap.getOrDefault(nodeId, "未知");
            Integer relType = null;
            Long relId = null;
            if (i < path.size() - 1) {
                long[] edge = edgeMap.get(path.get(i + 1));
                if (edge != null) {
                    relType = (int) edge[0];
                    relId = edge[1];
                }
            }
            steps.add(new RelationPathStep(nodeId, name, relType, relId));
        }

        logger.info("Found relation path from node={} to node={} with {} steps",
                fromNodeId, toNodeId, steps.size());

        return new PathResult(true, steps, "路径查找成功");
    }
}
