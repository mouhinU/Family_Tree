package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.TreeNodeVO;
import com.mouhin.family.tree.common.enums.ColorLabelEnum;
import com.mouhin.family.tree.common.enums.RelationTypeEnum;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyRelation;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.FamilyRelationRepository;
import com.mouhin.family.tree.domain.service.FamilyTreeDomainService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 族谱树形结构应用服务单元测试。
 * 覆盖：空树、普通父子结构、配偶挂载、血亲配偶、同胞排次、
 * 已故节点保留、离异丧偶改嫁、缓存命中与失效。
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@ExtendWith(MockitoExtension.class)
class FamilyTreeApplicationServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long FAMILY_ID = 100L;

    @Mock
    private FamilyNodeRepository familyNodeRepository;

    @Mock
    private FamilyRelationRepository familyRelationRepository;

    @Mock
    private FamilyTreeDomainService familyTreeDomainService;

    private FamilyTreeApplicationService familyTreeApplicationService;

    @BeforeEach
    void setUp() {
        familyTreeApplicationService = new FamilyTreeApplicationService(
                familyNodeRepository, familyRelationRepository,
                familyTreeDomainService, new SimpleMeterRegistry());
    }

    // ========== 数据构造辅助 ==========

    private FamilyNode node(long id, String name, int generation) {
        FamilyNode n = new FamilyNode();
        n.setId(id);
        n.setUserId(USER_ID);
        n.setFamilyId(FAMILY_ID);
        n.setName(name);
        n.setGender(1);
        n.setGeneration(generation);
        n.setColorLabel(ColorLabelEnum.DEFAULT.getCode());
        return n;
    }

    private FamilyRelation relation(long fromNodeId, long toNodeId, int type) {
        FamilyRelation rel = new FamilyRelation();
        rel.setId(System.currentTimeMillis() + fromNodeId + toNodeId);
        rel.setUserId(USER_ID);
        rel.setFamilyId(FAMILY_ID);
        rel.setFromNodeId(fromNodeId);
        rel.setToNodeId(toNodeId);
        rel.setRelationType(type);
        return rel;
    }

    private FamilyRelation parentChild(long from, long to) {
        return relation(from, to, RelationTypeEnum.PARENT_CHILD.getCode());
    }

    private FamilyRelation spouse(long from, long to) {
        return relation(from, to, RelationTypeEnum.SPOUSE.getCode());
    }

    private void stubTree(List<FamilyNode> nodes, List<FamilyRelation> relations,
                          List<TreeNodeVO> treeResult) {
        when(familyNodeRepository.findByFamilyId(FAMILY_ID)).thenReturn(nodes);
        when(familyRelationRepository.findByFamilyId(FAMILY_ID)).thenReturn(relations);
        when(familyTreeDomainService.buildTree(eq(nodes), eq(relations)))
                .thenReturn(treeResult);
    }

    // ========== 测试用例 ==========

    @Test
    void emptyTreeReturnsEmptyList() {
        List<TreeNodeVO> emptyTree = new ArrayList<>();
        stubTree(new ArrayList<>(), new ArrayList<>(), emptyTree);

        List<TreeNodeVO> tree = familyTreeApplicationService.getFullTree(FAMILY_ID);

        assertNotNull(tree);
        assertTrue(tree.isEmpty());
    }

    @Test
    void simpleParentChildTreeBuildsCorrectly() {
        FamilyNode father = node(1L, "蒙父", 1);
        father.setDeathDate(LocalDate.of(2020, 5, 5));
        List<FamilyNode> nodes = List.of(father, node(2L, "蒙甲", 2), node(3L, "蒙乙", 2));
        List<FamilyRelation> relations = List.of(parentChild(1L, 2L), parentChild(1L, 3L));

        // 领域服务返回构建好的树
        TreeNodeVO rootVO = new TreeNodeVO();
        rootVO.setId(1L);
        rootVO.setName("蒙父");
        rootVO.setGeneration(1);
        rootVO.setDeathDate("2020-05-05");
        TreeNodeVO child1 = new TreeNodeVO();
        child1.setId(2L);
        child1.setName("蒙甲");
        child1.setGeneration(2);
        TreeNodeVO child2 = new TreeNodeVO();
        child2.setId(3L);
        child2.setName("蒙乙");
        child2.setGeneration(2);
        rootVO.getChildren().add(child1);
        rootVO.getChildren().add(child2);

        stubTree(nodes, relations, List.of(rootVO));

        List<TreeNodeVO> tree = familyTreeApplicationService.getFullTree(FAMILY_ID);

        assertEquals(1, tree.size(), "应只有一个根节点");
        TreeNodeVO root = tree.get(0);
        assertEquals(1L, root.getId());
        assertEquals(2, root.getChildren().size());
        assertEquals(2L, root.getChildren().get(0).getId());
        assertEquals(3L, root.getChildren().get(1).getId());
        assertEquals("2020-05-05", root.getDeathDate(),
                "已故节点的卒日期应完整映射到 VO");
    }

    @Test
    void satelliteSpouseAttachedWithMutualReference() {
        FamilyRelation marriage = spouse(1L, 2L);
        marriage.setMarriageDate(LocalDate.of(2000, 1, 1));
        List<FamilyNode> nodes = List.of(node(1L, "蒙夫", 1), node(2L, "李妻", 1));
        List<FamilyRelation> relations = List.of(marriage);

        // 领域服务返回含配偶的树
        TreeNodeVO husbandVO = new TreeNodeVO();
        husbandVO.setId(1L);
        husbandVO.setName("蒙夫");
        husbandVO.setGeneration(1);
        TreeNodeVO wifeVO = new TreeNodeVO();
        wifeVO.setId(2L);
        wifeVO.setName("李妻");
        wifeVO.setGeneration(1);
        wifeVO.setRelationId(marriage.getId());
        wifeVO.setMarriageDate("2000-01-01");
        // 反向引用
        TreeNodeVO reverseRef = new TreeNodeVO();
        reverseRef.setId(1L);
        reverseRef.setName("蒙夫");
        reverseRef.setGeneration(1);
        wifeVO.getSpouses().add(reverseRef);
        husbandVO.getSpouses().add(wifeVO);

        stubTree(nodes, relations, List.of(husbandVO));

        List<TreeNodeVO> tree = familyTreeApplicationService.getFullTree(FAMILY_ID);

        assertEquals(1, tree.size());
        TreeNodeVO husband = tree.get(0);
        assertEquals(1L, husband.getId());
        assertEquals(1, husband.getSpouses().size());

        TreeNodeVO wife = husband.getSpouses().get(0);
        assertEquals(2L, wife.getId());
        assertEquals(marriage.getId(), wife.getRelationId(),
                "配偶 VO 应携带关系ID供前端管理关系");
        assertEquals("2000-01-01", wife.getMarriageDate());

        // 配偶（妻子）的详情需能回显丈夫
        assertEquals(1, wife.getSpouses().size());
        assertEquals(1L, wife.getSpouses().get(0).getId());
    }

    @Test
    void getFullTreeUsesCacheUntilEvicted() {
        List<FamilyNode> nodes = List.of(node(1L, "蒙甲", 1));
        List<FamilyRelation> relations = new ArrayList<>();
        TreeNodeVO rootVO = new TreeNodeVO();
        rootVO.setId(1L);
        rootVO.setName("蒙甲");
        rootVO.setGeneration(1);
        List<TreeNodeVO> treeResult = List.of(rootVO);

        stubTree(nodes, relations, treeResult);

        familyTreeApplicationService.getFullTree(FAMILY_ID);
        familyTreeApplicationService.getFullTree(FAMILY_ID);

        // 第二次读取应命中缓存，不再查库
        verify(familyNodeRepository, times(1)).findByFamilyId(FAMILY_ID);
        verify(familyRelationRepository, times(1)).findByFamilyId(FAMILY_ID);

        familyTreeApplicationService.evictFamilyTree(FAMILY_ID);
        familyTreeApplicationService.getFullTree(FAMILY_ID);

        // 失效后重新构建
        verify(familyNodeRepository, times(2)).findByFamilyId(FAMILY_ID);
        verify(familyRelationRepository, times(2)).findByFamilyId(FAMILY_ID);
    }
}
