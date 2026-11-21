package com.mouhin.family.tree.domain.service;

import com.mouhin.family.tree.common.enums.AffinityTypeEnum;
import com.mouhin.family.tree.common.enums.NodeLinkSourceEnum;
import com.mouhin.family.tree.common.enums.NodeLinkTypeEnum;
import com.mouhin.family.tree.common.enums.RelationTypeEnum;
import com.mouhin.family.tree.domain.entity.CrossFamilyLink;
import com.mouhin.family.tree.domain.entity.Family;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyRelation;
import com.mouhin.family.tree.domain.repository.CrossFamilyLinkRepository;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.FamilyRelationRepository;
import com.mouhin.family.tree.domain.repository.FamilyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 亲族家族领域服务单元测试：覆盖父族回落与回指、母族沿链、妻/夫族按性别判定、
 * 去重与缺链空着不猜测等解析口径。
 *
 * @author Family-Tree
 * @date 2026-11-20
 */
@ExtendWith(MockitoExtension.class)
class AffinityFamilyDomainServiceTest {

    private static final Long FAMILY_ID = 100L;
    private static final Long NODE_ID = 10L;

    @Mock
    private CrossFamilyLinkRepository crossFamilyLinkRepository;

    @Mock
    private FamilyNodeRepository familyNodeRepository;

    @Mock
    private FamilyRelationRepository familyRelationRepository;

    @Mock
    private FamilyRepository familyRepository;

    @InjectMocks
    private AffinityFamilyDomainService affinityFamilyDomainService;

    // ========== 父族 ==========

    @Test
    void resolve_nullOrIncompleteNode_returnsEmpty() {
        assertTrue(affinityFamilyDomainService.resolveAffinityFamilies(null).isEmpty());
        assertTrue(affinityFamilyDomainService.resolveAffinityFamilies(new FamilyNode()).isEmpty());
    }

    @Test
    void resolve_noBloodLink_fallsBackToOwnFamily() {
        when(crossFamilyLinkRepository.findByNodeId(NODE_ID)).thenReturn(List.of());
        when(familyRepository.findById(FAMILY_ID)).thenReturn(family(FAMILY_ID, "蒙氏"));
        when(familyRelationRepository.findByNodeId(FAMILY_ID, NODE_ID)).thenReturn(List.of());

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(node(NODE_ID, FAMILY_ID, 1, "蒙大"));

        assertEquals(1, result.size());
        AffinityFamilyDomainService.AffinityFamilyValueObject father = result.get(0);
        assertEquals(AffinityTypeEnum.FATHER, father.type());
        assertEquals(FAMILY_ID, father.familyId());
        assertEquals("蒙氏", father.familyName());
        // 回落本族行无链接依据（linkId 为空，前端不渲染解除按钮）
        assertNull(father.linkId());
        assertEquals(NODE_ID, father.relatedPersonNodeId());
    }

    @Test
    void resolve_ownBloodLink_usesLinkedOriginFamily() {
        CrossFamilyLink link = link(5L, FAMILY_ID, NODE_ID, 200L, 99L);
        when(crossFamilyLinkRepository.findByNodeId(NODE_ID)).thenReturn(List.of(link));
        when(familyRepository.findById(200L)).thenReturn(family(200L, "罗氏"));
        when(familyRelationRepository.findByNodeId(FAMILY_ID, NODE_ID)).thenReturn(List.of());

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(node(NODE_ID, FAMILY_ID, 1, "蒙大"));

        assertEquals(1, result.size());
        assertEquals(AffinityTypeEnum.FATHER, result.get(0).type());
        assertEquals(Long.valueOf(200L), result.get(0).familyId());
        assertEquals(Long.valueOf(5L), result.get(0).linkId());
    }

    @Test
    void resolve_ownLinkNotBloodType_stillFallsBack() {
        CrossFamilyLink marriageLink = link(5L, FAMILY_ID, NODE_ID, 200L, 99L);
        marriageLink.setLinkType(NodeLinkTypeEnum.MARRIAGE.getCode());
        when(crossFamilyLinkRepository.findByNodeId(NODE_ID)).thenReturn(List.of(marriageLink));
        when(familyRepository.findById(FAMILY_ID)).thenReturn(family(FAMILY_ID, "蒙氏"));
        when(familyRelationRepository.findByNodeId(FAMILY_ID, NODE_ID)).thenReturn(List.of());

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(node(NODE_ID, FAMILY_ID, 1, "蒙大"));

        // 父族回指只认血缘同人链接，姻亲链接不误作出身家族
        assertEquals(FAMILY_ID, result.get(0).familyId());
        assertNull(result.get(0).linkId());
    }

    // ========== 母族 ==========

    @Test
    void resolve_motherLinkAppended() {
        FamilyNode mother = node(20L, FAMILY_ID, 2, "蒙母");
        when(crossFamilyLinkRepository.findByNodeId(NODE_ID)).thenReturn(List.of());
        when(familyRepository.findById(FAMILY_ID)).thenReturn(family(FAMILY_ID, "蒙氏"));
        when(familyRelationRepository.findByNodeId(FAMILY_ID, NODE_ID))
                .thenReturn(List.of(relation(RelationTypeEnum.PARENT_CHILD.getCode(), 20L, NODE_ID)));
        when(familyNodeRepository.findById(20L)).thenReturn(mother);
        when(crossFamilyLinkRepository.findByNodeId(20L))
                .thenReturn(List.of(link(6L, FAMILY_ID, 20L, 300L, 30L)));
        when(familyRepository.findById(300L)).thenReturn(family(300L, "陈氏"));

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(node(NODE_ID, FAMILY_ID, 1, "蒙大"));

        assertEquals(2, result.size());
        AffinityFamilyDomainService.AffinityFamilyValueObject motherFamily = result.get(1);
        assertEquals(AffinityTypeEnum.MOTHER, motherFamily.type());
        assertEquals(Long.valueOf(300L), motherFamily.familyId());
        assertEquals(Long.valueOf(20L), motherFamily.relatedPersonNodeId());
        assertEquals("蒙母", motherFamily.relatedPersonName());
        assertEquals(Long.valueOf(6L), motherFamily.linkId());
    }

    @Test
    void resolve_parentEdgeFromFatherGender_skipped() {
        when(crossFamilyLinkRepository.findByNodeId(NODE_ID)).thenReturn(List.of());
        when(familyRepository.findById(FAMILY_ID)).thenReturn(family(FAMILY_ID, "蒙氏"));
        when(familyRelationRepository.findByNodeId(FAMILY_ID, NODE_ID))
                .thenReturn(List.of(relation(RelationTypeEnum.PARENT_CHILD.getCode(), 20L, NODE_ID)));
        when(familyNodeRepository.findById(20L)).thenReturn(node(20L, FAMILY_ID, 1, "蒙父"));

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(node(NODE_ID, FAMILY_ID, 1, "蒙大"));

        assertEquals(1, result.size());
    }

    @Test
    void resolve_childDirectionEdgeNotForThisNode_skipped() {
        when(crossFamilyLinkRepository.findByNodeId(NODE_ID)).thenReturn(List.of());
        when(familyRepository.findById(FAMILY_ID)).thenReturn(family(FAMILY_ID, "蒙氏"));
        // from=当前节点 to=子女：本节点是父端，不产生母族行
        when(familyRelationRepository.findByNodeId(FAMILY_ID, NODE_ID))
                .thenReturn(List.of(relation(RelationTypeEnum.PARENT_CHILD.getCode(), NODE_ID, 25L)));

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(node(NODE_ID, FAMILY_ID, 1, "蒙大"));

        assertEquals(1, result.size());
    }

    @Test
    void resolve_duplicateMotherEdges_deduplicated() {
        when(crossFamilyLinkRepository.findByNodeId(NODE_ID)).thenReturn(List.of());
        when(familyRepository.findById(FAMILY_ID)).thenReturn(family(FAMILY_ID, "蒙氏"));
        when(familyRelationRepository.findByNodeId(FAMILY_ID, NODE_ID)).thenReturn(List.of(
                relation(RelationTypeEnum.PARENT_CHILD.getCode(), 20L, NODE_ID),
                relation(RelationTypeEnum.ADOPTION.getCode(), 20L, NODE_ID)));
        when(familyNodeRepository.findById(20L)).thenReturn(node(20L, FAMILY_ID, 2, "蒙母"));
        when(crossFamilyLinkRepository.findByNodeId(20L))
                .thenReturn(List.of(link(6L, FAMILY_ID, 20L, 300L, 30L)));
        when(familyRepository.findById(300L)).thenReturn(family(300L, "陈氏"));

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(node(NODE_ID, FAMILY_ID, 1, "蒙大"));

        assertEquals(2, result.size());
        assertEquals(AffinityTypeEnum.MOTHER, result.get(1).type());
    }

    @Test
    void resolve_motherLinkOtherFamilyMissing_leftEmpty() {
        when(crossFamilyLinkRepository.findByNodeId(NODE_ID)).thenReturn(List.of());
        when(familyRepository.findById(FAMILY_ID)).thenReturn(family(FAMILY_ID, "蒙氏"));
        when(familyRelationRepository.findByNodeId(FAMILY_ID, NODE_ID))
                .thenReturn(List.of(relation(RelationTypeEnum.PARENT_CHILD.getCode(), 20L, NODE_ID)));
        when(familyNodeRepository.findById(20L)).thenReturn(node(20L, FAMILY_ID, 2, "蒙母"));
        when(crossFamilyLinkRepository.findByNodeId(20L))
                .thenReturn(List.of(link(6L, FAMILY_ID, 20L, 300L, 30L)));
        when(familyRepository.findById(300L)).thenReturn(null);

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(node(NODE_ID, FAMILY_ID, 1, "蒙大"));

        // 缺链/族数据缺失时空着不猜测
        assertEquals(1, result.size());
    }

    // ========== 妻族 / 夫族 ==========

    @Test
    void resolve_femaleSpouseLink_appendsWifeFamily() {
        when(crossFamilyLinkRepository.findByNodeId(NODE_ID)).thenReturn(List.of());
        when(familyRepository.findById(FAMILY_ID)).thenReturn(family(FAMILY_ID, "蒙氏"));
        when(familyRelationRepository.findByNodeId(FAMILY_ID, NODE_ID))
                .thenReturn(List.of(relation(RelationTypeEnum.SPOUSE.getCode(), NODE_ID, 21L)));
        when(familyNodeRepository.findById(21L)).thenReturn(node(21L, FAMILY_ID, 2, "罗氏"));
        when(crossFamilyLinkRepository.findByNodeId(21L))
                .thenReturn(List.of(link(7L, FAMILY_ID, 21L, 400L, 44L)));
        when(familyRepository.findById(400L)).thenReturn(family(400L, "罗家"));

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(node(NODE_ID, FAMILY_ID, 1, "蒙大"));

        assertEquals(2, result.size());
        assertEquals(AffinityTypeEnum.WIFE, result.get(1).type());
        assertEquals(Long.valueOf(400L), result.get(1).familyId());
    }

    @Test
    void resolve_spouseGenderMissing_inferredFromNodeGender() {
        when(crossFamilyLinkRepository.findByNodeId(NODE_ID)).thenReturn(List.of());
        when(familyRepository.findById(FAMILY_ID)).thenReturn(family(FAMILY_ID, "蒙氏"));
        when(familyRelationRepository.findByNodeId(FAMILY_ID, NODE_ID))
                .thenReturn(List.of(relation(RelationTypeEnum.SPOUSE.getCode(), 21L, NODE_ID)));
        when(familyNodeRepository.findById(21L)).thenReturn(node(21L, FAMILY_ID, null, "未知性别配偶"));
        when(crossFamilyLinkRepository.findByNodeId(21L))
                .thenReturn(List.of(link(7L, 400L, 44L, FAMILY_ID, 21L)));
        when(familyRepository.findById(400L)).thenReturn(family(400L, "罗家"));

        // 本人为女、配偶性别缺失 → 反推为夫族；链接两端家族取非本族一端
        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(node(NODE_ID, FAMILY_ID, 2, "蒙英"));

        assertEquals(2, result.size());
        assertEquals(AffinityTypeEnum.HUSBAND, result.get(1).type());
        assertEquals(Long.valueOf(400L), result.get(1).familyId());
    }

    @Test
    void resolve_bothGendersUnknown_spouseSkipped() {
        when(crossFamilyLinkRepository.findByNodeId(NODE_ID)).thenReturn(List.of());
        when(familyRepository.findById(FAMILY_ID)).thenReturn(family(FAMILY_ID, "蒙氏"));
        when(familyRelationRepository.findByNodeId(FAMILY_ID, NODE_ID))
                .thenReturn(List.of(relation(RelationTypeEnum.SPOUSE.getCode(), NODE_ID, 21L)));
        when(familyNodeRepository.findById(21L)).thenReturn(node(21L, FAMILY_ID, null, "无名配偶"));

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(node(NODE_ID, FAMILY_ID, null, "蒙大"));

        assertEquals(1, result.size());
    }

    @Test
    void resolve_spouseWithoutLink_leftEmpty() {
        when(crossFamilyLinkRepository.findByNodeId(NODE_ID)).thenReturn(List.of());
        when(familyRepository.findById(FAMILY_ID)).thenReturn(family(FAMILY_ID, "蒙氏"));
        when(familyRelationRepository.findByNodeId(FAMILY_ID, NODE_ID))
                .thenReturn(List.of(relation(RelationTypeEnum.SPOUSE.getCode(), NODE_ID, 21L)));
        when(familyNodeRepository.findById(21L)).thenReturn(node(21L, FAMILY_ID, 2, "罗氏"));
        when(crossFamilyLinkRepository.findByNodeId(21L)).thenReturn(List.of());

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(node(NODE_ID, FAMILY_ID, 1, "蒙大"));

        assertEquals(1, result.size());
    }

    // ========== 辅助方法 ==========

    private FamilyNode node(Long id, Long familyId, Integer gender, String name) {
        FamilyNode node = new FamilyNode();
        node.setId(id);
        node.setFamilyId(familyId);
        node.setGender(gender);
        node.setName(name);
        return node;
    }

    private Family family(Long id, String name) {
        Family family = new Family();
        family.setId(id);
        family.setName(name);
        return family;
    }

    private FamilyRelation relation(Integer type, Long fromNodeId, Long toNodeId) {
        FamilyRelation relation = new FamilyRelation();
        relation.setRelationType(type);
        relation.setFromNodeId(fromNodeId);
        relation.setToNodeId(toNodeId);
        return relation;
    }

    private CrossFamilyLink link(Long id, Long srcFamilyId, Long srcNodeId, Long dstFamilyId, Long dstNodeId) {
        CrossFamilyLink link = new CrossFamilyLink();
        link.setId(id);
        link.setSrcFamilyId(srcFamilyId);
        link.setSrcNodeId(srcNodeId);
        link.setDstFamilyId(dstFamilyId);
        link.setDstNodeId(dstNodeId);
        link.setLinkType(NodeLinkTypeEnum.BLOOD.getCode());
        link.setSource(NodeLinkSourceEnum.MANUAL.getCode());
        return link;
    }
}
