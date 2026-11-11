package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilyMember;
import com.mouhin.family.tree.domain.entity.FamilyRelation;
import com.mouhin.family.tree.domain.entity.FamilyUserNode;
import com.mouhin.family.tree.domain.repository.FamilyMemberRepository;
import com.mouhin.family.tree.domain.repository.FamilyRelationRepository;
import com.mouhin.family.tree.domain.repository.FamilyUserNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 族谱节点详情可见性应用服务
 *
 * <p>族谱树对全族成员完整渲染，但"查看详情"按角色收敛：
 * 族长/管理员不受限；普通成员仅可查看自身绑定节点、其祖先链（到根）、其后代子树（到叶），
 * 以及上述核心节点各自的直系配偶（不展开配偶原生家族分支）。
 * 该规则是横向越权（IDOR）的唯一裁决入口，各按节点读详情的接口须调用
 * {@link #assertDetailVisible} 收口。</p>
 *
 * @author Family-Tree
 * @date 2026-11-11
 */
@Service
public class FamilyNodeVisibilityApplicationService {

    private static final Logger logger =
            LoggerFactory.getLogger(FamilyNodeVisibilityApplicationService.class);

    /**
     * 越权访问详情时的统一提示
     */
    private static final String DENY_MESSAGE = "无权查看该族人详情";

    private final FamilyMemberRepository familyMemberRepository;
    private final FamilyUserNodeRepository familyUserNodeRepository;
    private final FamilyRelationRepository familyRelationRepository;

    public FamilyNodeVisibilityApplicationService(FamilyMemberRepository familyMemberRepository,
                                                  FamilyUserNodeRepository familyUserNodeRepository,
                                                  FamilyRelationRepository familyRelationRepository) {
        this.familyMemberRepository = familyMemberRepository;
        this.familyUserNodeRepository = familyUserNodeRepository;
        this.familyRelationRepository = familyRelationRepository;
    }

    /**
     * 判断用户在该家族内是否为管理者（族长或管理员）
     *
     * @param familyId 家族ID
     * @param userId   用户ID
     * @return 是否为管理者
     */
    public boolean isManager(Long familyId, Long userId) {
        if (familyId == null || userId == null) {
            return false;
        }
        FamilyMember member = familyMemberRepository.findByFamilyIdAndUserId(familyId, userId);
        return member != null && member.isManager();
    }

    /**
     * 解析用户可查看详情的节点ID集合
     *
     * @param familyId 家族ID
     * @param userId   用户ID
     * @return 管理者返回 {@code null} 表示不受限；未绑定节点返回空集；否则返回可见节点集合
     */
    public Set<Long> resolveDetailVisibleNodeIds(Long familyId, Long userId) {
        if (familyId == null || userId == null) {
            return Collections.emptySet();
        }
        if (isManager(familyId, userId)) {
            return null;
        }

        FamilyUserNode binding = familyUserNodeRepository.findByFamilyIdAndUserId(familyId, userId);
        if (binding == null || binding.getNodeId() == null) {
            return Collections.emptySet();
        }
        Long selfNodeId = binding.getNodeId();

        Map<Long, List<Long>> childrenByParent = new HashMap<>();
        Map<Long, List<Long>> parentsByChild = new HashMap<>();
        Map<Long, List<Long>> spousesByNode = new HashMap<>();
        buildLineageIndexes(familyRelationRepository.findByFamilyId(familyId),
                childrenByParent, parentsByChild, spousesByNode);

        // 血缘核心：自身 + 全部祖先（到根）+ 全部后代（到叶）
        Set<Long> core = new HashSet<>();
        core.add(selfNodeId);
        collectReachable(selfNodeId, childrenByParent, core);
        collectReachable(selfNodeId, parentsByChild, core);

        // 并入核心节点的直系配偶（不沿配偶继续展开，避免泄露其原生家族）
        Set<Long> visible = new HashSet<>(core);
        for (Long nodeId : core) {
            List<Long> spouses = spousesByNode.get(nodeId);
            if (spouses != null) {
                visible.addAll(spouses);
            }
        }
        return visible;
    }

    /**
     * 断言用户可查看指定节点的详情，越权抛出业务异常
     *
     * @param familyId 家族ID
     * @param userId   用户ID
     * @param nodeId   目标节点ID
     */
    public void assertDetailVisible(Long familyId, Long userId, Long nodeId) {
        Set<Long> scope = resolveDetailVisibleNodeIds(familyId, userId);
        if (scope == null) {
            return;
        }
        if (nodeId == null || !scope.contains(nodeId)) {
            logger.debug("Detail access denied: familyId={} userId={} nodeId={}", familyId, userId, nodeId);
            throw new BusinessException(DENY_MESSAGE);
        }
    }

    /**
     * 遍历关系构建亲子（父→子、子→父）与配偶邻接索引
     */
    private void buildLineageIndexes(List<FamilyRelation> relations,
                                     Map<Long, List<Long>> childrenByParent,
                                     Map<Long, List<Long>> parentsByChild,
                                     Map<Long, List<Long>> spousesByNode) {
        for (FamilyRelation rel : relations) {
            Long from = rel.getFromNodeId();
            Long to = rel.getToNodeId();
            if (from == null || to == null) {
                continue;
            }
            if (rel.isSpouse()) {
                spousesByNode.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
                spousesByNode.computeIfAbsent(to, k -> new ArrayList<>()).add(from);
            } else if (rel.isParentChild() || rel.isAdoption()) {
                childrenByParent.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
                parentsByChild.computeIfAbsent(to, k -> new ArrayList<>()).add(from);
            }
        }
    }

    /**
     * 从起点沿邻接表广度遍历，将可达节点并入结果集（visited 兼作去重与防环）
     */
    private void collectReachable(Long start, Map<Long, List<Long>> adjacency, Set<Long> out) {
        Deque<Long> queue = new ArrayDeque<>();
        queue.offer(start);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            List<Long> neighbors = adjacency.get(current);
            if (neighbors == null) {
                continue;
            }
            for (Long neighbor : neighbors) {
                if (out.add(neighbor)) {
                    queue.offer(neighbor);
                }
            }
        }
    }
}
