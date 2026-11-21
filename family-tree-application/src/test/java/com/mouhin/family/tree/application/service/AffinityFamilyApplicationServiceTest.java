package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.AffinityCandidateVO;
import com.mouhin.family.tree.common.dto.FamilyBriefVO;
import com.mouhin.family.tree.common.dto.NodeAffinityFamilyVO;
import com.mouhin.family.tree.common.dto.NodeMemberFamilyVO;
import com.mouhin.family.tree.common.enums.AffinityTypeEnum;
import com.mouhin.family.tree.common.enums.FamilyMemberRoleEnum;
import com.mouhin.family.tree.common.enums.NodeLinkSourceEnum;
import com.mouhin.family.tree.common.enums.NodeLinkTypeEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.CrossFamilyLink;
import com.mouhin.family.tree.domain.entity.Family;
import com.mouhin.family.tree.domain.entity.FamilyMember;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyUserNode;
import com.mouhin.family.tree.domain.entity.User;
import com.mouhin.family.tree.domain.event.OperationPerformedEvent;
import com.mouhin.family.tree.domain.repository.CrossFamilyLinkRepository;
import com.mouhin.family.tree.domain.repository.FamilyMemberRepository;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.FamilyRepository;
import com.mouhin.family.tree.domain.repository.FamilyUserNodeRepository;
import com.mouhin.family.tree.domain.repository.UserRepository;
import com.mouhin.family.tree.domain.service.AffinityFamilyDomainService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 跨族亲族应用服务单元测试：覆盖亲族解析映射、归属家族受限展示、
 * 人工建链/解链的门与审计事件、同名候选置信度口径与家族摘要。
 *
 * @author Family-Tree
 * @date 2026-11-20
 */
@ExtendWith(MockitoExtension.class)
class AffinityFamilyApplicationServiceTest {

    private static final Long FAMILY_ID = 100L;
    private static final Long OTHER_FAMILY_ID = 200L;
    private static final Long NODE_ID = 10L;
    private static final Long DST_NODE_ID = 30L;
    private static final Long VIEWER_ID = 1L;
    private static final Long BOUND_USER_ID = 90L;

    @Mock
    private FamilyNodeRepository familyNodeRepository;

    @Mock
    private FamilyRepository familyRepository;

    @Mock
    private FamilyMemberRepository familyMemberRepository;

    @Mock
    private FamilyUserNodeRepository familyUserNodeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CrossFamilyLinkRepository crossFamilyLinkRepository;

    @Mock
    private AffinityFamilyDomainService affinityFamilyDomainService;

    @Mock
    private FamilyNodeVisibilityApplicationService visibilityApplicationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AffinityFamilyApplicationService affinityFamilyApplicationService;

    // ========== 亲族家族解析 ==========

    @Test
    void listAffinityFamilies_nodeNotInCurrentFamily_throws() {
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node(NODE_ID, 999L, "蒙大"));

        assertThrows(BusinessException.class,
                () -> affinityFamilyApplicationService.listAffinityFamilies(FAMILY_ID, NODE_ID));
    }

    @Test
    void listAffinityFamilies_mapsValueObjectsToVoCodes() {
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node(NODE_ID, FAMILY_ID, "蒙大"));
        when(affinityFamilyDomainService.resolveAffinityFamilies(any())).thenReturn(List.of(
                new AffinityFamilyDomainService.AffinityFamilyValueObject(
                        AffinityTypeEnum.FATHER, FAMILY_ID, "蒙氏", NODE_ID, "蒙大", null, null, null),
                new AffinityFamilyDomainService.AffinityFamilyValueObject(
                        AffinityTypeEnum.MOTHER, OTHER_FAMILY_ID, "罗氏", 20L, "蒙母", 6L,
                        NodeLinkSourceEnum.MANUAL.getCode(), VIEWER_ID)));

        List<NodeAffinityFamilyVO> result =
                affinityFamilyApplicationService.listAffinityFamilies(FAMILY_ID, NODE_ID);

        assertEquals(2, result.size());
        assertEquals(Integer.valueOf(1), result.get(0).getType());
        assertNull(result.get(0).getLinkId());
        assertEquals(Integer.valueOf(2), result.get(1).getType());
        assertEquals(OTHER_FAMILY_ID, result.get(1).getFamilyId());
        assertEquals(Long.valueOf(6L), result.get(1).getLinkId());
        assertEquals(Integer.valueOf(2), result.get(1).getLinkSource());
    }

    // ========== 归属家族（Phase 0 受限展示） ==========

    @Test
    void listMemberFamilies_unboundNode_returnsEmpty() {
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node(NODE_ID, FAMILY_ID, "蒙大"));
        when(familyUserNodeRepository.findByFamilyIdAndNodeId(FAMILY_ID, NODE_ID)).thenReturn(null);

        assertTrue(affinityFamilyApplicationService
                .listMemberFamilies(FAMILY_ID, NODE_ID, VIEWER_ID).isEmpty());
    }

    @Test
    void listMemberFamilies_autoAccountBinding_returnsEmpty() {
        stubBoundNode(autoUser(BOUND_USER_ID));

        assertTrue(affinityFamilyApplicationService
                .listMemberFamilies(FAMILY_ID, NODE_ID, VIEWER_ID).isEmpty());
    }

    @Test
    void listMemberFamilies_strangerViewer_throws() {
        stubBoundNode(realUser(BOUND_USER_ID));
        when(visibilityApplicationService.isManager(FAMILY_ID, VIEWER_ID)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> affinityFamilyApplicationService.listMemberFamilies(FAMILY_ID, NODE_ID, VIEWER_ID));
        assertTrue(ex.getMessage().contains("仅本人或族长"));
    }

    @Test
    void listMemberFamilies_self_excludesCurrentAndSorts() {
        stubBoundNode(realUser(BOUND_USER_ID));
        when(familyMemberRepository.findByUserId(BOUND_USER_ID)).thenReturn(List.of(
                member(FAMILY_ID, FamilyMemberRoleEnum.OWNER.getCode()),
                member(300L, FamilyMemberRoleEnum.MEMBER.getCode()),
                member(OTHER_FAMILY_ID, FamilyMemberRoleEnum.MEMBER.getCode())));
        when(familyRepository.findByIds(anyList()))
                .thenReturn(List.of(family(OTHER_FAMILY_ID, "罗氏"), family(300L, "陈氏")));

        List<NodeMemberFamilyVO> result =
                affinityFamilyApplicationService.listMemberFamilies(FAMILY_ID, NODE_ID, BOUND_USER_ID);

        assertEquals(2, result.size());
        assertEquals(OTHER_FAMILY_ID, result.get(0).getFamilyId());
        assertEquals(Long.valueOf(300L), result.get(1).getFamilyId());
        assertEquals(FamilyMemberRoleEnum.MEMBER.getCode(), result.get(0).getRole());
    }

    @Test
    void listMemberFamilies_managerViewer_passesGate() {
        stubBoundNode(realUser(BOUND_USER_ID));
        when(visibilityApplicationService.isManager(FAMILY_ID, VIEWER_ID)).thenReturn(true);
        when(familyMemberRepository.findByUserId(BOUND_USER_ID)).thenReturn(List.of(
                member(FAMILY_ID, FamilyMemberRoleEnum.OWNER.getCode())));

        assertTrue(affinityFamilyApplicationService
                .listMemberFamilies(FAMILY_ID, NODE_ID, VIEWER_ID).isEmpty());
    }

    // ========== 人工建链 ==========

    @Test
    void linkNode_dstNodeMissing_throws() {
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node(NODE_ID, FAMILY_ID, "蒙大"));
        when(familyNodeRepository.findById(DST_NODE_ID)).thenReturn(null);

        assertThrows(BusinessException.class, () -> affinityFamilyApplicationService
                .linkNode(FAMILY_ID, NODE_ID, DST_NODE_ID, null, VIEWER_ID, "meng", "127.0.0.1"));
    }

    @Test
    void linkNode_sameFamily_throws() {
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node(NODE_ID, FAMILY_ID, "蒙大"));
        when(familyNodeRepository.findById(DST_NODE_ID)).thenReturn(node(DST_NODE_ID, FAMILY_ID, "蒙二"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> affinityFamilyApplicationService
                        .linkNode(FAMILY_ID, NODE_ID, DST_NODE_ID, null, VIEWER_ID, "meng", "127.0.0.1"));
        assertTrue(ex.getMessage().contains("不同家族"));
    }

    @Test
    void linkNode_existingPair_throws() {
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node(NODE_ID, FAMILY_ID, "蒙大"));
        when(familyNodeRepository.findById(DST_NODE_ID)).thenReturn(node(DST_NODE_ID, OTHER_FAMILY_ID, "罗大"));
        when(crossFamilyLinkRepository.findByNodePair(NODE_ID, DST_NODE_ID)).thenReturn(
                link(8L, FAMILY_ID, NODE_ID, OTHER_FAMILY_ID, DST_NODE_ID, NodeLinkSourceEnum.MANUAL.getCode()));

        assertThrows(BusinessException.class, () -> affinityFamilyApplicationService
                .linkNode(FAMILY_ID, NODE_ID, DST_NODE_ID, null, VIEWER_ID, "meng", "127.0.0.1"));
    }

    @Test
    void linkNode_success_normalizesEndpointsAndPublishesAudit() {
        // 本族端点ID大于他族端点ID，落库前必须按小ID在前规范化
        when(familyNodeRepository.findById(DST_NODE_ID)).thenReturn(node(DST_NODE_ID, FAMILY_ID, "蒙大"));
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node(NODE_ID, OTHER_FAMILY_ID, "罗大"));
        when(crossFamilyLinkRepository.findByNodePair(DST_NODE_ID, NODE_ID)).thenReturn(null);
        doAnswer(invocation -> {
            CrossFamilyLink saved = invocation.getArgument(0);
            saved.setId(55L);
            return saved;
        }).when(crossFamilyLinkRepository).save(any());

        Long linkId = affinityFamilyApplicationService
                .linkNode(FAMILY_ID, DST_NODE_ID, NODE_ID, null, VIEWER_ID, "meng", "127.0.0.1");

        assertEquals(Long.valueOf(55L), linkId);
        ArgumentCaptor<CrossFamilyLink> captor = ArgumentCaptor.forClass(CrossFamilyLink.class);
        verify(crossFamilyLinkRepository).save(captor.capture());
        CrossFamilyLink saved = captor.getValue();
        assertEquals(NODE_ID, saved.getSrcNodeId());
        assertEquals(OTHER_FAMILY_ID, saved.getSrcFamilyId());
        assertEquals(DST_NODE_ID, saved.getDstNodeId());
        assertEquals(FAMILY_ID, saved.getDstFamilyId());
        assertEquals(NodeLinkTypeEnum.BLOOD.getCode(), saved.getLinkType());
        assertEquals(NodeLinkSourceEnum.MANUAL.getCode(), saved.getSource());
        assertEquals(VIEWER_ID, saved.getConfirmedBy());
        verify(eventPublisher).publishEvent(any(OperationPerformedEvent.class));
    }

    @Test
    void linkNode_explicitMarriageType_kept() {
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node(NODE_ID, FAMILY_ID, "蒙大"));
        when(familyNodeRepository.findById(DST_NODE_ID)).thenReturn(node(DST_NODE_ID, OTHER_FAMILY_ID, "罗大"));
        when(crossFamilyLinkRepository.findByNodePair(NODE_ID, DST_NODE_ID)).thenReturn(null);

        affinityFamilyApplicationService.linkNode(FAMILY_ID, NODE_ID, DST_NODE_ID,
                NodeLinkTypeEnum.MARRIAGE.getCode(), VIEWER_ID, "meng", "127.0.0.1");

        ArgumentCaptor<CrossFamilyLink> captor = ArgumentCaptor.forClass(CrossFamilyLink.class);
        verify(crossFamilyLinkRepository).save(captor.capture());
        assertEquals(NodeLinkTypeEnum.MARRIAGE.getCode(), captor.getValue().getLinkType());
    }

    // ========== 解除关联 ==========

    @Test
    void unlinkNode_linkMissingOrNotTouchingNode_throws() {
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node(NODE_ID, FAMILY_ID, "蒙大"));
        when(crossFamilyLinkRepository.findById(8L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> affinityFamilyApplicationService
                .unlinkNode(FAMILY_ID, NODE_ID, 8L, VIEWER_ID, "meng", "127.0.0.1"));

        CrossFamilyLink unrelated = link(8L, FAMILY_ID, 11L, OTHER_FAMILY_ID, DST_NODE_ID,
                NodeLinkSourceEnum.MANUAL.getCode());
        when(crossFamilyLinkRepository.findById(8L)).thenReturn(unrelated);
        assertThrows(BusinessException.class, () -> affinityFamilyApplicationService
                .unlinkNode(FAMILY_ID, NODE_ID, 8L, VIEWER_ID, "meng", "127.0.0.1"));
        verify(crossFamilyLinkRepository, never()).removeById(any());
    }

    @Test
    void unlinkNode_neitherSideManager_throws() {
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node(NODE_ID, FAMILY_ID, "蒙大"));
        when(crossFamilyLinkRepository.findById(8L))
                .thenReturn(link(8L, FAMILY_ID, NODE_ID, OTHER_FAMILY_ID, DST_NODE_ID,
                        NodeLinkSourceEnum.MANUAL.getCode()));
        when(visibilityApplicationService.isManager(FAMILY_ID, VIEWER_ID)).thenReturn(false);
        when(visibilityApplicationService.isManager(OTHER_FAMILY_ID, VIEWER_ID)).thenReturn(false);

        assertThrows(BusinessException.class, () -> affinityFamilyApplicationService
                .unlinkNode(FAMILY_ID, NODE_ID, 8L, VIEWER_ID, "meng", "127.0.0.1"));
        verify(crossFamilyLinkRepository, never()).removeById(any());
    }

    @Test
    void unlinkNode_otherSideManager_removesAndPublishesAudit() {
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node(NODE_ID, FAMILY_ID, "蒙大"));
        when(crossFamilyLinkRepository.findById(8L))
                .thenReturn(link(8L, FAMILY_ID, NODE_ID, OTHER_FAMILY_ID, DST_NODE_ID,
                        NodeLinkSourceEnum.MANUAL.getCode()));
        when(visibilityApplicationService.isManager(FAMILY_ID, VIEWER_ID)).thenReturn(false);
        when(visibilityApplicationService.isManager(OTHER_FAMILY_ID, VIEWER_ID)).thenReturn(true);

        affinityFamilyApplicationService.unlinkNode(FAMILY_ID, NODE_ID, 8L, VIEWER_ID, "meng", "127.0.0.1");

        verify(crossFamilyLinkRepository).removeById(8L);
        verify(eventPublisher).publishEvent(any(OperationPerformedEvent.class));
    }

    // ========== 候选生成 ==========

    @Test
    void suggestCandidates_blankName_returnsEmpty() {
        FamilyNode node = node(NODE_ID, FAMILY_ID, null);
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node);

        assertTrue(affinityFamilyApplicationService.suggestCandidates(FAMILY_ID, NODE_ID).isEmpty());
        verify(familyNodeRepository, never()).findByNameExcludeFamily(any(), any(), anyInt());
    }

    @Test
    void suggestCandidates_confidenceOrderingAndExclusions() {
        FamilyNode self = solarNode(NODE_ID, FAMILY_ID, "蒙大", LocalDate.of(1990, 1, 1));
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(self);
        FamilyNode high = solarNode(31L, OTHER_FAMILY_ID, "蒙大", LocalDate.of(1990, 1, 1));
        FamilyNode mismatch = solarNode(32L, 300L, "蒙大", LocalDate.of(1991, 2, 2));
        FamilyNode unrecorded = node(33L, 400L, "蒙大");
        unrecorded.setBirthDate(LocalDate.of(1990, 1, 1));
        FamilyNode alreadyLinked = solarNode(34L, 400L, "蒙大", LocalDate.of(1990, 1, 1));
        when(familyNodeRepository.findByNameExcludeFamily("蒙大", FAMILY_ID, 200))
                .thenReturn(List.of(high, mismatch, unrecorded, alreadyLinked));
        when(crossFamilyLinkRepository.findByNodePair(NODE_ID, 31L)).thenReturn(null);
        when(crossFamilyLinkRepository.findByNodePair(NODE_ID, 33L)).thenReturn(null);
        when(crossFamilyLinkRepository.findByNodePair(NODE_ID, 34L))
                .thenReturn(link(9L, FAMILY_ID, NODE_ID, 400L, 34L, NodeLinkSourceEnum.MANUAL.getCode()));
        when(familyRepository.findById(OTHER_FAMILY_ID)).thenReturn(family(OTHER_FAMILY_ID, "罗氏"));
        when(familyRepository.findById(400L)).thenReturn(family(400L, "陈氏"));

        List<AffinityCandidateVO> result = affinityFamilyApplicationService.suggestCandidates(FAMILY_ID, NODE_ID);

        // 高置信在前，存量未记录历法降档为中；日期不符与已成链的对不出候选
        assertEquals(2, result.size());
        assertEquals(Long.valueOf(31L), result.get(0).getNodeId());
        assertEquals(Integer.valueOf(2), result.get(0).getConfidence());
        assertEquals("1990-01-01", result.get(0).getBirthDate());
        assertEquals(Long.valueOf(33L), result.get(1).getNodeId());
        assertEquals(Integer.valueOf(1), result.get(1).getConfidence());
    }

    @Test
    void suggestCandidates_bothRecordedLunar_mismatchedLunarExcluded() {
        FamilyNode self = solarNode(NODE_ID, FAMILY_ID, "蒙大", LocalDate.of(1990, 1, 1));
        self.setBirthCalendar(2);
        self.setLunarBirthDate("一九八九年十一月十六");
        FamilyNode other = solarNode(31L, OTHER_FAMILY_ID, "蒙大", LocalDate.of(1990, 1, 1));
        other.setBirthCalendar(2);
        other.setLunarBirthDate("一九八九年冬月十六");
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(self);
        when(familyNodeRepository.findByNameExcludeFamily("蒙大", FAMILY_ID, 200)).thenReturn(List.of(other));

        assertTrue(affinityFamilyApplicationService.suggestCandidates(FAMILY_ID, NODE_ID).isEmpty());
    }

    // ========== 家族摘要 ==========

    @Test
    void getFamilyBrief_missingFamily_throws() {
        when(familyRepository.findById(OTHER_FAMILY_ID)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> affinityFamilyApplicationService.getFamilyBrief(OTHER_FAMILY_ID));
    }

    @Test
    void getFamilyBrief_mapsAncestorAndStats() {
        Family family = family(OTHER_FAMILY_ID, "罗氏");
        family.setGenerationRows(20);
        when(familyRepository.findById(OTHER_FAMILY_ID)).thenReturn(family);
        when(familyNodeRepository.findFirstByFamilyId(OTHER_FAMILY_ID)).thenReturn(node(1L, OTHER_FAMILY_ID, "罗始祖"));
        when(familyMemberRepository.countByFamilyId(OTHER_FAMILY_ID)).thenReturn(12L);

        FamilyBriefVO vo = affinityFamilyApplicationService.getFamilyBrief(OTHER_FAMILY_ID);

        assertEquals(OTHER_FAMILY_ID, vo.getFamilyId());
        assertEquals("罗氏", vo.getFamilyName());
        assertEquals("罗始祖", vo.getAncestorName());
        assertEquals(Integer.valueOf(20), vo.getGenerationRows());
        assertEquals(Long.valueOf(12L), vo.getMemberCount());
    }

    // ========== 辅助方法 ==========

    private void stubBoundNode(User boundUser) {
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node(NODE_ID, FAMILY_ID, "蒙大"));
        FamilyUserNode binding = new FamilyUserNode();
        binding.setFamilyId(FAMILY_ID);
        binding.setUserId(BOUND_USER_ID);
        binding.setNodeId(NODE_ID);
        when(familyUserNodeRepository.findByFamilyIdAndNodeId(FAMILY_ID, NODE_ID)).thenReturn(binding);
        when(userRepository.findById(BOUND_USER_ID)).thenReturn(boundUser);
    }

    private FamilyNode node(Long id, Long familyId, String name) {
        FamilyNode node = new FamilyNode();
        node.setId(id);
        node.setFamilyId(familyId);
        node.setName(name);
        return node;
    }

    private FamilyNode solarNode(Long id, Long familyId, String name, LocalDate birthDate) {
        FamilyNode node = node(id, familyId, name);
        node.setBirthDate(birthDate);
        node.setBirthCalendar(1);
        return node;
    }

    private Family family(Long id, String name) {
        Family family = new Family();
        family.setId(id);
        family.setName(name);
        return family;
    }

    private FamilyMember member(Long familyId, String role) {
        FamilyMember member = new FamilyMember();
        member.setFamilyId(familyId);
        member.setUserId(BOUND_USER_ID);
        member.setRole(role);
        return member;
    }

    private CrossFamilyLink link(Long id, Long srcFamilyId, Long srcNodeId, Long dstFamilyId, Long dstNodeId,
                                 Integer source) {
        CrossFamilyLink link = new CrossFamilyLink();
        link.setId(id);
        link.setSrcFamilyId(srcFamilyId);
        link.setSrcNodeId(srcNodeId);
        link.setDstFamilyId(dstFamilyId);
        link.setDstNodeId(dstNodeId);
        link.setLinkType(NodeLinkTypeEnum.BLOOD.getCode());
        link.setSource(source);
        return link;
    }

    private User realUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setAutoAccount(false);
        return user;
    }

    private User autoUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setAutoAccount(true);
        return user;
    }
}
