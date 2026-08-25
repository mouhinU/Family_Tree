package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.FamilyNodeApplicationService;
import com.mouhin.family.tree.application.service.FamilyRelationApplicationService;
import com.mouhin.family.tree.common.result.Result;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * 关系路径分析控制器。
 * <p>
 * 输入两个节点ID，使用 BFS 计算它们之间的最短关系路径。
 *
 * @author Family-Tree
 * @date 2026-08-04
 */
@RestController
@RequestMapping("/api/relation-path")
public class RelationPathController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(RelationPathController.class);

    private final FamilyRelationApplicationService familyRelationService;
    private final FamilyNodeApplicationService familyNodeService;

    public RelationPathController(FamilyRelationApplicationService familyRelationService,
                                  FamilyNodeApplicationService familyNodeService) {
        this.familyRelationService = familyRelationService;
        this.familyNodeService = familyNodeService;
    }

    /**
     * 计算两个节点之间的最短关系路径
     *
     * @param fromNodeId 起始节点ID
     * @param toNodeId   目标节点ID
     */
    @GetMapping
    public Result<Map<String, Object>> findPath(
            @RequestParam Long fromNodeId,
            @RequestParam Long toNodeId,
            HttpSession session) {
        Long familyId = getCurrentFamilyId(session);

        // 获取所有关系
        var allRelations = familyRelationService.listAllRelations(familyId);

        // 构建邻接表
        Map<Long, List<long[]>> adjacency = new HashMap<>();
        for (var rel : allRelations) {
            adjacency.computeIfAbsent(rel.getFromNodeId(), k -> new ArrayList<>())
                    .add(new long[]{rel.getToNodeId(), rel.getRelationType(), rel.getId()});
            adjacency.computeIfAbsent(rel.getToNodeId(), k -> new ArrayList<>())
                    .add(new long[]{rel.getFromNodeId(), rel.getRelationType(), rel.getId()});
        }

        // BFS 找最短路径
        Queue<Long> queue = new LinkedList<>();
        Map<Long, Long> parentMap = new HashMap<>();
        Set<Long> visited = new HashSet<>();

        queue.offer(fromNodeId);
        visited.add(fromNodeId);
        parentMap.put(fromNodeId, -1L);

        boolean found = false;
        while (!queue.isEmpty() && !found) {
            Long current = queue.poll();
            List<long[]> neighbors = adjacency.getOrDefault(current, List.of());
            for (long[] neighbor : neighbors) {
                long nextId = neighbor[0];
                if (!visited.contains(nextId)) {
                    visited.add(nextId);
                    parentMap.put(nextId, current);
                    queue.offer(nextId);
                    if (nextId == toNodeId) {
                        found = true;
                        break;
                    }
                }
            }
        }

        if (!found) {
            Map<String, Object> result = new HashMap<>();
            result.put("found", false);
            result.put("message", "两个节点之间没有关系路径");
            return Result.success(result);
        }

        // 回溯路径
        List<Long> path = new ArrayList<>();
        Long current = toNodeId;
        while (current != -1L) {
            path.add(0, current);
            current = parentMap.get(current);
        }

        // 获取节点名称
        var allNodes = familyNodeService.listNodes(familyId);
        Map<Long, String> nameMap = new HashMap<>();
        for (var node : allNodes) {
            nameMap.put(node.getId(), node.getName());
        }

        List<Map<String, Object>> pathDetails = new ArrayList<>();
        for (int i = 0; i < path.size(); i++) {
            Map<String, Object> step = new HashMap<>();
            step.put("nodeId", path.get(i));
            step.put("name", nameMap.getOrDefault(path.get(i), "未知"));
            if (i < path.size() - 1) {
                // 找到连接关系
                Long from = path.get(i);
                Long to = path.get(i + 1);
                for (var rel : allRelations) {
                    if ((rel.getFromNodeId().equals(from) && rel.getToNodeId().equals(to))
                            || (rel.getFromNodeId().equals(to) && rel.getToNodeId().equals(from))) {
                        step.put("relationType", rel.getRelationType());
                        step.put("relationId", rel.getId());
                        break;
                    }
                }
            }
            pathDetails.add(step);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("found", true);
        result.put("pathLength", path.size());
        result.put("path", pathDetails);
        return Result.success(result);
    }
}
