package com.mouhin.family.tree.domain.service;

import com.mouhin.family.tree.common.dto.NodeDetailVO;
import com.mouhin.family.tree.common.enums.RelationTypeEnum;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyRelation;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 族谱详情构建领域服务单元测试。
 * 覆盖：基础与扩展字段回填、在婚/丧偶/离异/血亲配偶分组、范围外字段不进入明细。
 *
 * @author Family-Tree
 * @date 2026-11-11
 */
class FamilyTreeDomainServiceDetailTest {

    private static final Long FAMILY_ID = 100L;

    private final FamilyTreeDomainService service = new FamilyTreeDomainService();

    private FamilyNode node(long id, String name, int generation) {
        FamilyNode n = new FamilyNode();
        n.setId(id);
        n.setFamilyId(FAMILY_ID);
        n.setName(name);
        n.setGender(1);
        n.setGeneration(generation);
        return n;
    }

    private FamilyRelation spouse(long id, long from, long to) {
        FamilyRelation rel = new FamilyRelation();
        rel.setId(id);
        rel.setFamilyId(FAMILY_ID);
        rel.setFromNodeId(from);
        rel.setToNodeId(to);
        rel.setRelationType(RelationTypeEnum.SPOUSE.getCode());
        return rel;
    }

    private FamilyRelation parentChild(long id, long from, long to) {
        FamilyRelation rel = new FamilyRelation();
        rel.setId(id);
        rel.setFamilyId(FAMILY_ID);
        rel.setFromNodeId(from);
        rel.setToNodeId(to);
        rel.setRelationType(RelationTypeEnum.PARENT_CHILD.getCode());
        return rel;
    }

    @Test
    void populatesBasicAndExtendedFields() {
        FamilyNode self = node(1L, "蒙甲", 3);
        self.setBirthDate(LocalDate.of(1950, 5, 6));
        self.setDeathDate(LocalDate.of(2010, 1, 2));
        self.setBirthOrder(2);
        self.setRemark("族谱主编");
        self.setLunarBirthDate("庚寅年三月二十");
        self.setZi("子明");
        self.setHao("静庵");
        self.setHui("甲");
        self.setGraveLocation("东山祖茔");
        self.setSpouseName("外嫁王氏");
        self.setSpouseOriginFamily("王氏家族");

        NodeDetailVO detail = service.buildNodeDetail(self, List.of(self), List.of());

        assertEquals(1L, detail.getId());
        assertEquals("蒙甲", detail.getName());
        assertEquals("1950-05-06", detail.getBirthDate());
        assertEquals("2010-01-02", detail.getDeathDate());
        assertEquals(2, detail.getBirthOrder());
        assertEquals("族谱主编", detail.getRemark());
        assertEquals("庚寅年三月二十", detail.getLunarBirthDate());
        assertEquals("子明", detail.getZi());
        assertEquals("静庵", detail.getHao());
        assertEquals("甲", detail.getHui());
        assertEquals("东山祖茔", detail.getGraveLocation());
        assertEquals("外嫁王氏", detail.getSpouseName());
        assertEquals("王氏家族", detail.getSpouseOriginFamily());
    }

    @Test
    void currentMarriageGoesToSpousesGroup() {
        FamilyNode husband = node(1L, "蒙夫", 1);
        FamilyNode wife = node(2L, "李妻", 1);
        FamilyRelation marriage = spouse(10L, 1L, 2L);
        marriage.setMarriageDate(LocalDate.of(2000, 1, 1));

        NodeDetailVO detail = service.buildNodeDetail(
                husband, List.of(husband, wife), List.of(marriage));

        assertEquals(1, detail.getSpouses().size());
        NodeDetailVO.SpouseRelation row = detail.getSpouses().get(0);
        assertEquals(2L, row.getNodeId());
        assertEquals("李妻", row.getName());
        assertEquals(10L, row.getRelationId());
        assertEquals("2000-01-01", row.getMarriageDate());
        assertTrue(!Boolean.TRUE.equals(row.getDivorced()));
        assertTrue(detail.getFormerSpouses().isEmpty());
        assertTrue(detail.getBloodSpouses().isEmpty());
    }

    @Test
    void widowedSpouseStaysInSpousesGroupNotFormer() {
        FamilyNode husband = node(1L, "蒙夫", 1);
        FamilyNode wife = node(2L, "李妻", 1);
        FamilyRelation marriage = spouse(10L, 1L, 2L);
        marriage.setWidowed(true);

        NodeDetailVO detail = service.buildNodeDetail(
                husband, List.of(husband, wife), List.of(marriage));

        assertEquals(1, detail.getSpouses().size());
        assertTrue(Boolean.TRUE.equals(detail.getSpouses().get(0).getWidowed()));
        assertTrue(detail.getFormerSpouses().isEmpty());
    }

    @Test
    void divorcedSpouseGoesToFormerGroup() {
        FamilyNode husband = node(1L, "蒙夫", 1);
        FamilyNode wife = node(2L, "李妻", 1);
        FamilyRelation marriage = spouse(10L, 1L, 2L);
        marriage.setDivorced(true);
        marriage.setDivorceDate(LocalDate.of(2005, 6, 6));

        NodeDetailVO detail = service.buildNodeDetail(
                husband, List.of(husband, wife), List.of(marriage));

        assertTrue(detail.getSpouses().isEmpty());
        assertEquals(1, detail.getFormerSpouses().size());
        NodeDetailVO.SpouseRelation row = detail.getFormerSpouses().get(0);
        assertTrue(Boolean.TRUE.equals(row.getDivorced()));
        assertEquals("2005-06-06", row.getDivorceDate());
    }

    @Test
    void bloodSpouseWithOwnBranchGoesToBloodGroup() {
        // 配偶 2 自身有父节点（100→2），属于族谱原生分支成员 → 血亲配偶
        FamilyNode self = node(1L, "蒙甲", 2);
        FamilyNode cousinWife = node(2L, "表妹", 2);
        FamilyNode wifeParent = node(100L, "蒙乙", 1);
        List<FamilyNode> nodes = List.of(self, cousinWife, wifeParent);
        List<FamilyRelation> relations = new ArrayList<>();
        relations.add(spouse(10L, 1L, 2L));
        relations.add(parentChild(11L, 100L, 2L));

        NodeDetailVO detail = service.buildNodeDetail(self, nodes, relations);

        assertEquals(1, detail.getBloodSpouses().size());
        assertTrue(detail.getSpouses().isEmpty());
        NodeDetailVO.SpouseRelation row = detail.getBloodSpouses().get(0);
        assertEquals(2L, row.getNodeId());
        assertNotNull(row.getBloodRelationLabel());
    }

    @Test
    void spouseWithoutNodeIsSkipped() {
        FamilyNode husband = node(1L, "蒙夫", 1);
        FamilyRelation ghost = spouse(10L, 1L, 999L);

        NodeDetailVO detail = service.buildNodeDetail(
                husband, List.of(husband), List.of(ghost));

        assertTrue(detail.getSpouses().isEmpty());
        assertTrue(detail.getFormerSpouses().isEmpty());
        assertTrue(detail.getBloodSpouses().isEmpty());
        assertNull(detail.getRemark());
    }
}
