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

    /**
     * 越权执行节点相关操作时的统一提示
     */
    private static final String OPERATE_DENY_MESSAGE = "无权操作该族人";

    /**
     * 需要族长/管理员权限时的统一提示
     */
    private static final String MANAGER_ONLY_MESSAGE = "仅管理员可执行此操作";

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
     * 断言用户可对指定节点执行操作（新增子女/父母/配偶、编辑、删除、上色、传记、祭奠、缅怀、回滚等）。
     *
     * <p>与"查看详情"复用同一份直系可见集合（自身 + 祖先链到根 + 后代链到叶 + 链上节点直系配偶）：
     * 管理者不受限；非管理员仅能对其可见集合内的节点发起操作，越权抛出业务异常。
     * 创建类操作以其锚点节点调用本方法（新节点随后自动纳入其可见集合）。</p>
     *
     * @param familyId 家族ID
     * @param userId   用户ID
     * @param nodeId   目标（或锚点）节点ID
     */
    public void assertOperableNode(Long familyId, Long userId, Long nodeId) {
        Set<Long> scope = resolveDetailVisibleNodeIds(familyId, userId);
        if (scope == null) {
            return;
        }
        if (nodeId == null || !scope.contains(nodeId)) {
            logger.debug("Node operation denied: familyId={} userId={} nodeId={}", familyId, userId, nodeId);
            throw new BusinessException(OPERATE_DENY_MESSAGE);
        }
    }

    /**
     * 断言用户可对给定的多个节点全部执行操作（关系改删需两端均可见）。
     *
     * @param familyId 家族ID
     * @param userId   用户ID
     * @param nodeIds  需全部可操作的节点ID，任一为 null 或不在可见集合内即拒绝
     */
    public void assertOperableNodes(Long familyId, Long userId, Long... nodeIds) {
        Set<Long> scope = resolveDetailVisibleNodeIds(familyId, userId);
        if (scope == null) {
            return;
        }
        if (nodeIds == null) {
            throw new BusinessException(OPERATE_DENY_MESSAGE);
        }
        for (Long nodeId : nodeIds) {
            if (nodeId == null || !scope.contains(nodeId)) {
                logger.debug("Node operation denied: familyId={} userId={} nodeId={}", familyId, userId, nodeId);
                throw new BusinessException(OPERATE_DENY_MESSAGE);
            }
        }
    }

    /**
     * 断言用户为家族管理者（族长或管理员），用于家族级 / 整棵树操作（导出、GEDCOM、快照、回填、AI）。
     *
     * @param familyId 家族ID
     * @param userId   用户ID
     */
    public void assertManager(Long familyId, Long userId) {
        if (!isManager(familyId, userId)) {
            logger.debug("Manager-only operation denied: familyId={} userId={}", familyId, userId);
            throw new BusinessException(MANAGER_ONLY_MESSAGE);
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
