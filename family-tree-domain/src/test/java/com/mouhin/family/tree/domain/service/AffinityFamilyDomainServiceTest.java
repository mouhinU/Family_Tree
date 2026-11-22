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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 亲族家族领域服务单元测试：覆盖出身家族统一口径（族内父边优先、血缘链接回指、多链接优选）、
 * 母族/妻夫族按亲属出身解析、去重与缺链空着不猜测。
 *
 * @author Family-Tree
 * @date 2026-11-22
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

    private final Map<Long, FamilyNode> nodes = new HashMap<>(16);
    private final Map<Long, Family> families = new HashMap<>(8);
    private final Map<Long, List<CrossFamilyLink>> linksByNode = new HashMap<>(16);
    private final Map<Long, List<FamilyRelation>> relationsByNode = new HashMap<>(16);

    @BeforeEach
    void stubFixtureMaps() {
        lenient().when(familyNodeRepository.findById(anyLong()))
                .thenAnswer(invocation -> nodes.get(invocation.<Long>getArgument(0)));
        lenient().when(familyRepository.findById(anyLong()))
                .thenAnswer(invocation -> families.get(invocation.<Long>getArgument(0)));
        lenient().when(crossFamilyLinkRepository.findByNodeId(anyLong()))
                .thenAnswer(invocation -> linksByNode.getOrDefault(invocation.<Long>getArgument(0), List.of()));
        lenient().when(familyRelationRepository.findByNodeId(anyLong(), anyLong()))
                .thenAnswer(invocation -> relationsByNode.getOrDefault(invocation.<Long>getArgument(1), List.of()));
    }

    // ========== 父族 ==========

    @Test
    void resolve_nullOrIncompleteNode_returnsEmpty() {
        assertTrue(affinityFamilyDomainService.resolveAffinityFamilies(null).isEmpty());
        assertTrue(affinityFamilyDomainService.resolveAffinityFamilies(new FamilyNode()).isEmpty());
    }

    @Test
    void resolve_inFamilyFatherEdge_fatherFamilyShowsFather() {
        putFamily(FAMILY_ID, "蒙氏");
        putNode(NODE_ID, FAMILY_ID, 1, "蒙斌");
        putNode(20L, FAMILY_ID, 1, "蒙永成");
        putRelation(RelationTypeEnum.PARENT_CHILD.getCode(), 20L, NODE_ID);

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(nodes.get(NODE_ID));

        // 族内有父边：父族=本族、对应人为父亲（2026-11-22 口径，蒙斌父族=蒙氏/蒙永成）
        assertEquals(1, result.size());
        assertEquals(AffinityTypeEnum.FATHER, result.get(0).type());
        assertEquals(FAMILY_ID, result.get(0).familyId());
        assertEquals("蒙氏", result.get(0).familyName());
        assertEquals(Long.valueOf(20L), result.get(0).relatedPersonNodeId());
        assertEquals("蒙永成", result.get(0).relatedPersonName());
        assertNull(result.get(0).linkId());
    }

    @Test
    void resolve_noFatherEdgeNoLink_noFallbackFatherRow() {
        putFamily(FAMILY_ID, "蒙氏");
        putNode(NODE_ID, FAMILY_ID, 1, "蒙大");

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(nodes.get(NODE_ID));

        // 无父边且无血缘链接：出身不可考，不再回落误显本族父族行
        assertTrue(result.isEmpty());
    }

    @Test
    void resolve_marriedInSpouseNode_noFallbackFatherRow() {
        putFamily(FAMILY_ID, "蒙氏");
        putNode(NODE_ID, FAMILY_ID, 2, "冉从桃");
        putNode(21L, FAMILY_ID, 1, "蒙夫");
        putRelation(RelationTypeEnum.SPOUSE.getCode(), NODE_ID, 21L);

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(nodes.get(NODE_ID));

        // 入嫁夫/妻节点无父边且无同人链接：出身不可考，不得误显本族为父族（2026-11-22 全库误显修订）
        assertTrue(result.isEmpty());
    }

    @Test
    void resolve_ownBloodLink_usesLinkedOriginFamily() {
        putFamily(FAMILY_ID, "蒙氏");
        putFamily(200L, "罗氏");
        putNode(NODE_ID, FAMILY_ID, 2, "蒙英");
        putNode(99L, 200L, 2, "蒙英");
        putLink(5L, FAMILY_ID, NODE_ID, 200L, 99L);

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(nodes.get(NODE_ID));

        // 无父边沿血缘链接回指出身家族（外嫁回指），对应人为本人
        assertEquals(1, result.size());
        assertEquals(AffinityTypeEnum.FATHER, result.get(0).type());
        assertEquals(Long.valueOf(200L), result.get(0).familyId());
        assertEquals(Long.valueOf(5L), result.get(0).linkId());
        assertEquals(NODE_ID, result.get(0).relatedPersonNodeId());
    }

    @Test
    void resolve_multipleBloodLinks_prefersEndWithFatherRecord() {
        putFamily(FAMILY_ID, "蒙氏");
        putFamily(200L, "胡氏");
        putFamily(300L, "蒙氏分支");
        putNode(NODE_ID, FAMILY_ID, 1, "蒙斌");
        putNode(98L, 200L, 1, "蒙斌");
        putNode(77L, 300L, 1, "蒙斌");
        putNode(70L, 300L, 1, "蒙父");
        // 300 族内 77 有父边（血脉延续记载源），10↔77 应优先于无父边端 10↔98
        putRelation(RelationTypeEnum.PARENT_CHILD.getCode(), 70L, 77L);
        putLink(6L, FAMILY_ID, NODE_ID, 200L, 98L);
        putLink(7L, FAMILY_ID, NODE_ID, 300L, 77L);

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(nodes.get(NODE_ID));

        assertEquals(1, result.size());
        assertEquals(Long.valueOf(300L), result.get(0).familyId());
        assertEquals(Long.valueOf(7L), result.get(0).linkId());
    }

    @Test
    void resolve_ownLinkNotBloodType_noFatherRow() {
        putFamily(FAMILY_ID, "蒙氏");
        putFamily(200L, "罗氏");
        putNode(NODE_ID, FAMILY_ID, 1, "蒙大");
        putNode(99L, 200L, 2, "罗氏");
        CrossFamilyLink marriageLink = putLink(5L, FAMILY_ID, NODE_ID, 200L, 99L);
        marriageLink.setLinkType(NodeLinkTypeEnum.MARRIAGE.getCode());

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(nodes.get(NODE_ID));

        // 父族回指只认血缘同人链接，姻亲链接不误作出身家族；无父边也无血缘链接时不出父族行
        assertTrue(result.isEmpty());
    }

    // ========== 母族 ==========

    @Test
    void resolve_motherLinkAppended() {
        putFamily(FAMILY_ID, "蒙氏");
        putFamily(300L, "陈氏");
        putNode(NODE_ID, FAMILY_ID, 1, "蒙斌");
        putNode(20L, FAMILY_ID, 2, "罗梦桃");
        putNode(30L, 300L, 2, "罗梦桃");
        putRelation(RelationTypeEnum.PARENT_CHILD.getCode(), 20L, NODE_ID);
        putLink(6L, FAMILY_ID, 20L, 300L, 30L);

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(nodes.get(NODE_ID));

        assertEquals(1, result.size());
        AffinityFamilyDomainService.AffinityFamilyValueObject motherFamily = result.get(0);
        assertEquals(AffinityTypeEnum.MOTHER, motherFamily.type());
        assertEquals(Long.valueOf(300L), motherFamily.familyId());
        assertEquals(Long.valueOf(20L), motherFamily.relatedPersonNodeId());
        assertEquals("罗梦桃", motherFamily.relatedPersonName());
        assertEquals(Long.valueOf(6L), motherFamily.linkId());
    }

    @Test
    void resolve_parentEdgeFromFatherGender_notAppendedAsMother() {
        putFamily(FAMILY_ID, "蒙氏");
        putNode(NODE_ID, FAMILY_ID, 1, "蒙斌");
        putNode(20L, FAMILY_ID, 1, "蒙永成");
        putRelation(RelationTypeEnum.PARENT_CHILD.getCode(), 20L, NODE_ID);

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(nodes.get(NODE_ID));

        // 父端边不产母族行；转为父族行（对应人=父亲）
        assertEquals(1, result.size());
        assertEquals(AffinityTypeEnum.FATHER, result.get(0).type());
        assertEquals(Long.valueOf(20L), result.get(0).relatedPersonNodeId());
    }

    @Test
    void resolve_childDirectionEdgeNotForThisNode_skipped() {
        putFamily(FAMILY_ID, "蒙氏");
        putNode(NODE_ID, FAMILY_ID, 1, "蒙大");
        putNode(25L, FAMILY_ID, 1, "蒙二");
        putRelation(RelationTypeEnum.PARENT_CHILD.getCode(), NODE_ID, 25L);

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(nodes.get(NODE_ID));

        // 子方向边不算本人父边；无父边无链接时不回落出父族行
        assertTrue(result.isEmpty());
    }

    @Test
    void resolve_duplicateMotherEdges_deduplicated() {
        putFamily(FAMILY_ID, "蒙氏");
        putFamily(300L, "陈氏");
        putNode(NODE_ID, FAMILY_ID, 1, "蒙斌");
        putNode(20L, FAMILY_ID, 2, "蒙母");
        putNode(30L, 300L, 2, "蒙母");
        putRelation(RelationTypeEnum.PARENT_CHILD.getCode(), 20L, NODE_ID);
        putRelation(RelationTypeEnum.ADOPTION.getCode(), 20L, NODE_ID);
        putLink(6L, FAMILY_ID, 20L, 300L, 30L);

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(nodes.get(NODE_ID));

        // 仅母亲出身一行（本人无父边无链接，无父族行）
        assertEquals(1, result.size());
        assertEquals(AffinityTypeEnum.MOTHER, result.get(0).type());
    }

    @Test
    void resolve_motherLinkOtherFamilyMissing_noMotherRow() {
        putFamily(FAMILY_ID, "蒙氏");
        putNode(NODE_ID, FAMILY_ID, 1, "蒙斌");
        putNode(20L, FAMILY_ID, 2, "蒙母");
        putNode(30L, 300L, 2, "蒙母");
        putRelation(RelationTypeEnum.PARENT_CHILD.getCode(), 20L, NODE_ID);
        putLink(6L, FAMILY_ID, 20L, 300L, 30L);

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(nodes.get(NODE_ID));

        // 链接另一端家族数据缺失、母亲在本族又无父边：出身不可考，母族空着不猜测（2026-11-22 修订，勿回落本族）
        assertTrue(result.isEmpty());
    }

    @Test
    void resolve_marriedInMotherNoEvidence_noMotherRow() {
        putFamily(FAMILY_ID, "蒙氏");
        putNode(NODE_ID, FAMILY_ID, 1, "林世强");
        putNode(20L, FAMILY_ID, 2, "林母");
        putNode(21L, FAMILY_ID, 1, "蒙夫");
        putRelation(RelationTypeEnum.PARENT_CHILD.getCode(), 20L, NODE_ID);
        putRelation(RelationTypeEnum.SPOUSE.getCode(), 21L, 20L);

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(nodes.get(NODE_ID));

        // 生产 1:72 林世强案例：母亲为入嫁方（族内无父边、无出身链接），母族不得算作本族，空着不猜测
        assertTrue(result.isEmpty());
    }

    @Test
    void resolve_noFatherEvidenceMotherNativeToCurrentFamily_currentFamilyShowsAsMother() {
        putFamily(FAMILY_ID, "蒙氏");
        putNode(NODE_ID, FAMILY_ID, 1, "姚能刚");
        putNode(20L, FAMILY_ID, 2, "蒙昌绪");
        putNode(21L, FAMILY_ID, 1, "蒙母父");
        putRelation(RelationTypeEnum.PARENT_CHILD.getCode(), 20L, NODE_ID);
        putRelation(RelationTypeEnum.PARENT_CHILD.getCode(), 21L, 20L);

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(nodes.get(NODE_ID));

        // 生产 1:48 姚能刚案例：本人无父族证据、母亲为本族女儿（族内有父边）→ 本族以母族行显示
        assertEquals(1, result.size());
        assertEquals(AffinityTypeEnum.MOTHER, result.get(0).type());
        assertEquals(FAMILY_ID, result.get(0).familyId());
        assertEquals("蒙氏", result.get(0).familyName());
        assertEquals(Long.valueOf(20L), result.get(0).relatedPersonNodeId());
        assertEquals("蒙昌绪", result.get(0).relatedPersonName());
        assertNull(result.get(0).linkId());
    }

    @Test
    void resolve_nativeChildWithNativeMother_noMotherRow() {
        putFamily(FAMILY_ID, "蒙氏");
        putNode(NODE_ID, FAMILY_ID, 1, "蒙斌");
        putNode(20L, FAMILY_ID, 2, "蒙母");
        putNode(21L, FAMILY_ID, 1, "蒙母父");
        putNode(22L, FAMILY_ID, 1, "蒙父");
        putRelation(RelationTypeEnum.PARENT_CHILD.getCode(), 20L, NODE_ID);
        putRelation(RelationTypeEnum.PARENT_CHILD.getCode(), 21L, 20L);
        putRelation(RelationTypeEnum.PARENT_CHILD.getCode(), 22L, NODE_ID);

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(nodes.get(NODE_ID));

        // 本人有父族行（族内父边）：母亲出身即本族时不显示母族行，不误伤正常本族子女
        assertEquals(1, result.size());
        assertEquals(AffinityTypeEnum.FATHER, result.get(0).type());
        assertEquals(Long.valueOf(22L), result.get(0).relatedPersonNodeId());
    }

    // ========== 妻族 / 夫族 ==========

    @Test
    void resolve_femaleSpouseLink_appendsWifeFamily() {
        putFamily(FAMILY_ID, "蒙氏");
        putFamily(400L, "胡氏");
        putNode(NODE_ID, FAMILY_ID, 1, "蒙斌");
        putNode(21L, FAMILY_ID, 2, "胡兰");
        putNode(44L, 400L, 2, "胡兰");
        putRelation(RelationTypeEnum.SPOUSE.getCode(), NODE_ID, 21L);
        putLink(7L, FAMILY_ID, 21L, 400L, 44L);

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(nodes.get(NODE_ID));

        assertEquals(1, result.size());
        assertEquals(AffinityTypeEnum.WIFE, result.get(0).type());
        assertEquals(Long.valueOf(400L), result.get(0).familyId());
        assertEquals("胡兰", result.get(0).relatedPersonName());
    }

    @Test
    void resolve_spouseGenderMissing_inferredFromNodeGender() {
        putFamily(FAMILY_ID, "蒙氏");
        putFamily(400L, "罗家");
        putNode(NODE_ID, FAMILY_ID, 2, "蒙英");
        putNode(21L, FAMILY_ID, null, "未知性别配偶");
        putNode(44L, 400L, 1, "未知性别配偶");
        putRelation(RelationTypeEnum.SPOUSE.getCode(), 21L, NODE_ID);
        putLink(7L, 400L, 44L, FAMILY_ID, 21L);

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(nodes.get(NODE_ID));

        // 本人为女、配偶性别缺失 → 反推为夫族；链接两端家族取非本族一端
        assertEquals(1, result.size());
        assertEquals(AffinityTypeEnum.HUSBAND, result.get(0).type());
        assertEquals(Long.valueOf(400L), result.get(0).familyId());
    }

    @Test
    void resolve_bothGendersUnknown_spouseSkipped() {
        putFamily(FAMILY_ID, "蒙氏");
        putNode(NODE_ID, FAMILY_ID, null, "蒙大");
        putNode(21L, FAMILY_ID, null, "无名配偶");
        putRelation(RelationTypeEnum.SPOUSE.getCode(), NODE_ID, 21L);

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(nodes.get(NODE_ID));

        // 性别均未知：配偶行跳过，本人亦无父族证据
        assertTrue(result.isEmpty());
    }

    @Test
    void resolve_spouseWithoutLink_leftEmpty() {
        putFamily(FAMILY_ID, "蒙氏");
        putNode(NODE_ID, FAMILY_ID, 1, "蒙大");
        putNode(21L, FAMILY_ID, 2, "罗氏");
        putRelation(RelationTypeEnum.SPOUSE.getCode(), NODE_ID, 21L);

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(nodes.get(NODE_ID));

        // 配偶无跨族链接：妻族空着不猜测，本人亦无父族行
        assertTrue(result.isEmpty());
    }

    @Test
    void resolve_marriedInWithNativeSpouse_currentFamilyShowsAsHusband() {
        putFamily(FAMILY_ID, "蒙氏");
        putNode(296L, FAMILY_ID, 2, "胡兰");
        putNode(298L, FAMILY_ID, 1, "蒙斌");
        putNode(10L, FAMILY_ID, 1, "蒙父");
        putRelation(RelationTypeEnum.SPOUSE.getCode(), 296L, 298L);
        putRelation(RelationTypeEnum.PARENT_CHILD.getCode(), 10L, 298L);
        putLink(7L, FAMILY_ID, 298L, 39L, 328L);

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(nodes.get(296L));

        // 本节点（入嫁方）不再回落误显父族；配偶出身即本族且有族内父边证据 → 本族以夫族行显示
        assertEquals(1, result.size());
        assertEquals(AffinityTypeEnum.HUSBAND, result.get(0).type());
        assertEquals(FAMILY_ID, result.get(0).familyId());
        assertEquals(Long.valueOf(298L), result.get(0).relatedPersonNodeId());
        assertNull(result.get(0).linkId());
    }

    @Test
    void resolve_marriedInWifeWithNativeHusband_showsHusbandFamilyNotFather() {
        putFamily(FAMILY_ID, "蒙氏家族");
        putNode(11L, FAMILY_ID, 2, "冉从桃");
        putNode(8L, FAMILY_ID, 1, "蒙永华");
        putNode(1L, FAMILY_ID, 1, "蒙正文");
        putRelation(RelationTypeEnum.SPOUSE.getCode(), 11L, 8L);
        putRelation(RelationTypeEnum.PARENT_CHILD.getCode(), 1L, 8L);

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> wifeView =
                affinityFamilyDomainService.resolveAffinityFamilies(nodes.get(11L));

        // 入嫁妻页面（生产 1:11 冉从桃案例）：不再误显「父族=蒙氏家族」，本族以夫族行显示、对应人为丈夫
        assertEquals(1, wifeView.size());
        assertEquals(AffinityTypeEnum.HUSBAND, wifeView.get(0).type());
        assertEquals(FAMILY_ID, wifeView.get(0).familyId());
        assertEquals("蒙氏家族", wifeView.get(0).familyName());
        assertEquals(Long.valueOf(8L), wifeView.get(0).relatedPersonNodeId());
        assertNull(wifeView.get(0).linkId());

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> husbandView =
                affinityFamilyDomainService.resolveAffinityFamilies(nodes.get(8L));

        // 本族丈夫页面：父族=本族（对应人=父亲），妻子出身不可考时不显示妻族行
        assertEquals(1, husbandView.size());
        assertEquals(AffinityTypeEnum.FATHER, husbandView.get(0).type());
        assertEquals(Long.valueOf(1L), husbandView.get(0).relatedPersonNodeId());
    }

    @Test
    void resolve_linkedMarriedInWife_showsNatalFatherFamilyAndCurrentHusbandFamily() {
        putFamily(FAMILY_ID, "蒙氏家族");
        putFamily(39L, "胡氏家族");
        putNode(296L, FAMILY_ID, 2, "胡兰");
        putNode(328L, 39L, 2, "胡兰");
        putNode(298L, FAMILY_ID, 1, "蒙斌");
        putNode(10L, FAMILY_ID, 1, "蒙正文");
        putRelation(RelationTypeEnum.SPOUSE.getCode(), 296L, 298L);
        putRelation(RelationTypeEnum.PARENT_CHILD.getCode(), 10L, 298L);
        putLink(7L, FAMILY_ID, 296L, 39L, 328L);

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(nodes.get(296L));

        // 有血缘回指的入嫁方：父族=娘家（链接证据）+ 夫族=本族（配偶有族内父边）
        assertEquals(2, result.size());
        assertEquals(AffinityTypeEnum.FATHER, result.get(0).type());
        assertEquals(Long.valueOf(39L), result.get(0).familyId());
        assertEquals(Long.valueOf(7L), result.get(0).linkId());
        assertEquals(AffinityTypeEnum.HUSBAND, result.get(1).type());
        assertEquals(FAMILY_ID, result.get(1).familyId());
        assertEquals(Long.valueOf(298L), result.get(1).relatedPersonNodeId());
        assertNull(result.get(1).linkId());
    }

    @Test
    void resolve_wifeFamilyFollowsSpousePreferredLink() {
        putFamily(FAMILY_ID, "蒙氏");
        putFamily(39L, "胡氏");
        putFamily(40L, "罗氏");
        putNode(NODE_ID, FAMILY_ID, 1, "蒙斌");
        putNode(21L, FAMILY_ID, 2, "胡兰");
        putNode(44L, 39L, 2, "胡兰");
        putNode(45L, 40L, 2, "胡兰");
        putNode(46L, 40L, 1, "胡兰父");
        putRelation(RelationTypeEnum.SPOUSE.getCode(), NODE_ID, 21L);
        // 配偶多链接时优选"另一端在其族内有父边"的一条（40 族记载 45 有父边）
        putRelation(RelationTypeEnum.PARENT_CHILD.getCode(), 46L, 45L);
        putLink(8L, FAMILY_ID, 21L, 39L, 44L);
        putLink(9L, FAMILY_ID, 21L, 40L, 45L);

        List<AffinityFamilyDomainService.AffinityFamilyValueObject> result =
                affinityFamilyDomainService.resolveAffinityFamilies(nodes.get(NODE_ID));

        assertEquals(1, result.size());
        assertEquals(AffinityTypeEnum.WIFE, result.get(0).type());
        assertEquals(Long.valueOf(40L), result.get(0).familyId());
        assertEquals(Long.valueOf(9L), result.get(0).linkId());
    }

    // ========== 辅助方法 ==========

    private void putFamily(Long id, String name) {
        Family family = new Family();
        family.setId(id);
        family.setName(name);
        families.put(id, family);
    }

    private void putNode(Long id, Long familyId, Integer gender, String name) {
        FamilyNode node = new FamilyNode();
        node.setId(id);
        node.setFamilyId(familyId);
        node.setGender(gender);
        node.setName(name);
        nodes.put(id, node);
    }

    private void putRelation(Integer type, Long fromNodeId, Long toNodeId) {
        FamilyRelation relation = new FamilyRelation();
        relation.setRelationType(type);
        relation.setFromNodeId(fromNodeId);
        relation.setToNodeId(toNodeId);
        relationsByNode.computeIfAbsent(fromNodeId, key -> new ArrayList<>()).add(relation);
        relationsByNode.computeIfAbsent(toNodeId, key -> new ArrayList<>()).add(relation);
    }

    private CrossFamilyLink putLink(Long id, Long srcFamilyId, Long srcNodeId, Long dstFamilyId, Long dstNodeId) {
        CrossFamilyLink link = new CrossFamilyLink();
        link.setId(id);
        link.setSrcFamilyId(srcFamilyId);
        link.setSrcNodeId(srcNodeId);
        link.setDstFamilyId(dstFamilyId);
        link.setDstNodeId(dstNodeId);
        link.setLinkType(NodeLinkTypeEnum.BLOOD.getCode());
        link.setSource(NodeLinkSourceEnum.MANUAL.getCode());
        linksByNode.computeIfAbsent(srcNodeId, key -> new ArrayList<>()).add(link);
        linksByNode.computeIfAbsent(dstNodeId, key -> new ArrayList<>()).add(link);
        return link;
    }
}
