package com.mouhin.family.tree.domain.service;

import com.mouhin.family.tree.common.enums.RelationTypeEnum;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyRelation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 世代动态修复领域服务单元测试。
 * <p>
 * 覆盖链式重算、入嫁配偶对齐、支系锚点保留、多父取最浅、过继边、
 * 空世代兜底、亲子环不触碰与超过最大深度跳过等口径。
 *
 * @author Family-Tree
 * @date 2026-11-19
 */
class GenerationRepairDomainServiceTest {

    private final GenerationRepairDomainService service = new GenerationRepairDomainService();

    // ========== 链式重算 ==========

    @Test
    void chainBelowRootIsRecomputedLevelByLevel() {
        FamilyNode root = node(1L, 1);
        FamilyNode child = node(2L, 1);
        FamilyNode grandChild = node(3L, 1);
        List<FamilyRelation> relations = List.of(parentChild(1L, 2L), parentChild(2L, 3L));

        List<FamilyNode> changed = service.repair(List.of(root, child, grandChild), relations);

        assertEquals(List.of(child, grandChild), changed);
        assertEquals(1, root.getGeneration());
        assertEquals(2, child.getGeneration());
        assertEquals(3, grandChild.getGeneration());
    }

    @Test
    void nullGenerationRootAnchorsAtDefaultGeneration() {
        FamilyNode root = node(1L, null);
        FamilyNode child = node(2L, 9);

        service.repair(List.of(root, child), List.of(parentChild(1L, 2L)));

        assertEquals(1, root.getGeneration());
        assertEquals(2, child.getGeneration());
    }

    // ========== 配偶边 ==========

    @Test
    void marriedInSpouseAlignsToLineageInsteadOfStayingRoot() {
        FamilyNode grandfather = node(1L, 1);
        FamilyNode son = node(2L, 1);
        FamilyNode daughterInLaw = node(3L, 1);
        List<FamilyRelation> relations = List.of(
                parentChild(1L, 2L), spouse(2L, 3L));

        service.repair(List.of(grandfather, son, daughterInLaw), relations);

        assertEquals(2, son.getGeneration());
        assertEquals(2, daughterInLaw.getGeneration(), "入嫁配偶应随配偶同世，而非作为独立根停在第1世");
    }

    @Test
    void founderCoupleKeepsStoredGenerationAsFamilyAnchor() {
        FamilyNode founder = node(1L, 3);
        FamilyNode spouse = node(2L, 3);
        FamilyNode child = node(3L, 1);
        List<FamilyRelation> relations = List.of(
                spouse(1L, 2L), parentChild(1L, 3L));

        List<FamilyNode> changed = service.repair(List.of(founder, spouse, child), relations);

        assertEquals(List.of(child), changed, "支系始祖对的现存世代是全树锚点，不应被压回第1世");
        assertEquals(3, founder.getGeneration());
        assertEquals(3, spouse.getGeneration());
        assertEquals(4, child.getGeneration());
    }

    // ========== 多父边与过继 ==========

    @Test
    void multipleParentEdgesConvergeToShallowestGeneration() {
        FamilyNode shallowRoot = node(1L, 1);
        FamilyNode deepBranch = node(2L, 5);
        FamilyNode deepChild = node(3L, 1);
        FamilyNode sharedChild = node(4L, 9);
        List<FamilyRelation> relations = List.of(
                parentChild(1L, 4L), parentChild(2L, 3L), parentChild(3L, 4L));

        service.repair(List.of(shallowRoot, deepBranch, deepChild, sharedChild), relations);

        assertEquals(2, sharedChild.getGeneration(), "多条父辈边应取最浅可达世代");
        assertEquals(6, deepChild.getGeneration());
    }

    @Test
    void adoptionEdgeCountsLikeParentChild() {
        FamilyNode adopter = node(1L, 2);
        FamilyNode adopted = node(2L, 1);

        service.repair(List.of(adopter, adopted), List.of(relation(1L, 2L, RelationTypeEnum.ADOPTION)));

        assertEquals(3, adopted.getGeneration());
    }

    // ========== 异常结构 ==========

    @Test
    void pureParentCycleIsLeftUntouched() {
        FamilyNode cycleA = node(1L, 7);
        FamilyNode cycleB = node(2L, 8);
        FamilyNode healthyRoot = node(3L, 1);
        FamilyNode healthyChild = node(4L, 1);
        List<FamilyRelation> relations = List.of(
                parentChild(1L, 2L), parentChild(2L, 1L), parentChild(3L, 4L));

        List<FamilyNode> changed = service.repair(
                List.of(cycleA, cycleB, healthyRoot, healthyChild), relations);

        assertEquals(List.of(healthyChild), changed, "环内节点不可从任何根到达，应保持原值留给人工处理");
        assertEquals(7, cycleA.getGeneration());
        assertEquals(8, cycleB.getGeneration());
    }

    @Test
    void danglingRelationsAreIgnoredWithoutFailure() {
        FamilyNode root = node(1L, 1);
        FamilyNode child = node(2L, 1);
        List<FamilyRelation> relations = List.of(
                parentChild(1L, 99L), parentChild(1L, 2L), spouse(2L, 98L));

        List<FamilyNode> changed = service.repair(List.of(root, child), relations);

        assertEquals(List.of(child), changed);
    }

    @Test
    void levelsBeyondMaxDepthAreSkipped() {
        int total = 105;
        List<FamilyNode> nodes = new ArrayList<>(total);
        List<FamilyRelation> relations = new ArrayList<>(total - 1);
        for (long id = 1; id <= total; id++) {
            nodes.add(node(id, 0));
            if (id > 1) {
                relations.add(parentChild(id - 1, id));
            }
        }

        service.repair(nodes, relations);

        assertEquals(1, nodes.get(0).getGeneration());
        assertEquals(100, nodes.get(99).getGeneration(), "第100层仍在深度上限内，应正常修复");
        assertEquals(0, nodes.get(100).getGeneration(), "超过最大深度的节点应跳过更新");
        assertEquals(0, nodes.get(total - 1).getGeneration());
    }

    @Test
    void emptyInputReturnsNoChanges() {
        assertTrue(service.repair(List.of(), List.of()).isEmpty());
    }

    // ========== 数据构造辅助 ==========

    private FamilyNode node(Long id, Integer generation) {
        FamilyNode node = new FamilyNode();
        node.setId(id);
        node.setName("节点" + id);
        node.setGeneration(generation);
        return node;
    }

    private FamilyRelation parentChild(Long parentId, Long childId) {
        return relation(parentId, childId, RelationTypeEnum.PARENT_CHILD);
    }

    private FamilyRelation spouse(Long fromId, Long toId) {
        return relation(fromId, toId, RelationTypeEnum.SPOUSE);
    }

    private FamilyRelation relation(Long fromId, Long toId, RelationTypeEnum type) {
        FamilyRelation relation = new FamilyRelation();
        relation.setFromNodeId(fromId);
        relation.setToNodeId(toId);
        relation.setRelationType(type.getCode());
        return relation;
    }
}
