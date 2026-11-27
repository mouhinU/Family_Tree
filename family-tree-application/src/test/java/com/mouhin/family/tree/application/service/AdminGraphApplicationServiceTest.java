package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.AdminGraphDetailVO;
import com.mouhin.family.tree.common.dto.AdminGraphNetworkVO;
import com.mouhin.family.tree.common.dto.AdminGraphOverviewVO;
import com.mouhin.family.tree.common.enums.NodeLinkSourceEnum;
import com.mouhin.family.tree.common.enums.NodeLinkTypeEnum;
import com.mouhin.family.tree.common.enums.RelationTypeEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.CrossFamilyLink;
import com.mouhin.family.tree.domain.entity.Family;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyRelation;
import com.mouhin.family.tree.domain.repository.CrossFamilyLinkRepository;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.FamilyRelationRepository;
import com.mouhin.family.tree.domain.repository.FamilyRepository;
import com.mouhin.family.tree.domain.service.CrossFamilyLinkQualityDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 平台跨族图谱应用服务单元测试
 *
 * @author Family-Tree
 * @date 2026-11-26
 */
@ExtendWith(MockitoExtension.class)
class AdminGraphApplicationServiceTest {

    @Mock
    private FamilyRepository familyRepository;
    @Mock
    private FamilyNodeRepository familyNodeRepository;
    @Mock
    private FamilyRelationRepository familyRelationRepository;
    @Mock
    private CrossFamilyLinkRepository crossFamilyLinkRepository;

    private AdminGraphApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AdminGraphApplicationService(familyRepository, familyNodeRepository, familyRelationRepository,
                crossFamilyLinkRepository, new CrossFamilyLinkQualityDomainService());
    }

    @Test
    void overview_withoutLinks_returnsAllFamiliesWithoutNodeLookup() {
        when(familyRepository.listAll()).thenReturn(List.of(family(2L, "乙族"), family(1L, "甲族")));
        when(crossFamilyLinkRepository.listAll()).thenReturn(List.of());

        AdminGraphOverviewVO result = service.getOverview();

        assertEquals(2L, result.getFamilyCount());
        assertEquals(0L, result.getLinkedFamilyCount());
        assertEquals(0L, result.getLinkCount());
        assertFalse(result.getTruncated());
        assertEquals(List.of(1L, 2L), result.getFamilies().stream().map(item -> item.getId()).toList());
        verifyNoInteractions(familyNodeRepository);
    }

    @Test
    void overview_aggregatesEdgesAndIssueLinks() {
        when(familyRepository.listAll()).thenReturn(List.of(family(1L, "甲族"), family(2L, "乙族")));
        CrossFamilyLink invalidLink = link(11L, 1L, 101L, 2L, 201L,
                NodeLinkTypeEnum.MARRIAGE.getCode(), 9L);
        invalidLink.setSource(99);
        when(crossFamilyLinkRepository.listAll()).thenReturn(List.of(
                link(10L, 1L, 100L, 2L, 200L, NodeLinkTypeEnum.BLOOD.getCode(), 9L), invalidLink));
        when(familyNodeRepository.findByIds(anyList())).thenReturn(List.of(
                node(100L, 1L, "甲一"), node(200L, 2L, "乙一"),
                node(101L, 1L, "甲二"), node(201L, 2L, "乙二")));

        AdminGraphOverviewVO result = service.getOverview();

        assertEquals(2L, result.getLinkCount());
        assertEquals(1L, result.getBloodLinkCount());
        assertEquals(1L, result.getMarriageLinkCount());
        assertEquals(1L, result.getIssueLinkCount());
        assertFalse(result.getTruncated());
        assertEquals(1, result.getEdges().size());
        assertEquals(2L, result.getEdges().get(0).getLinkCount());
        assertEquals(1L, result.getEdges().get(0).getIssueCount());
        assertEquals(2L, result.getLinkedFamilyCount());
    }

    @Test
    void detail_missingEndpoint_returnsPlaceholderAndIssue() {
        Family family = family(1L, "甲族");
        when(familyRepository.findById(1L)).thenReturn(family);
        when(crossFamilyLinkRepository.findByFamilyId(1L)).thenReturn(List.of(
                link(10L, 1L, 100L, 2L, 200L, NodeLinkTypeEnum.BLOOD.getCode(), null)));
        when(familyNodeRepository.findByIds(anyList())).thenReturn(List.of(node(100L, 1L, "甲一")));
        when(familyRepository.findByIds(anyList())).thenReturn(List.of(family, family(2L, "乙族")));

        AdminGraphDetailVO result = service.getFamilyDetail(1L);

        assertEquals("甲族", result.getFamily().getName());
        assertEquals(1L, result.getFamily().getLinkCount());
        assertEquals(2, result.getNodes().size());
        assertTrue(result.getNodes().stream().anyMatch(node -> Boolean.TRUE.equals(node.getMissing())));
        assertEquals("血缘同人", result.getLinks().get(0).getLinkTypeLabel());
        assertEquals(1, result.getLinks().get(0).getIssues().size());
        assertFalse(result.getTruncated());
    }

    @Test
    void detail_unknownFamily_rejected() {
        when(familyRepository.findById(404L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.getFamilyDetail(404L));

        assertEquals("家族不存在", exception.getMessage());
        verifyNoInteractions(crossFamilyLinkRepository, familyNodeRepository);
    }

    @Test
    void detail_nullFamilyId_rejected() {
        BusinessException exception = assertThrows(BusinessException.class, () -> service.getFamilyDetail(null));

        assertEquals("缺少家族ID", exception.getMessage());
        verifyNoInteractions(familyRepository, crossFamilyLinkRepository, familyNodeRepository);
    }

    @Test
    void network_returnsFamilyNodesRelationsAndExternalEndpoints() {
        Family family = family(1L, "甲族");
        Family otherFamily = family(2L, "乙族");
        when(familyRepository.findById(1L)).thenReturn(family);
        when(familyNodeRepository.findByFamilyId(1L)).thenReturn(List.of(
                node(100L, 1L, "甲一"), node(101L, 1L, "甲二")));
        when(familyRelationRepository.findByFamilyId(1L)).thenReturn(List.of(relation(10L, 100L, 101L)));
        when(crossFamilyLinkRepository.findByFamilyId(1L)).thenReturn(List.of(
                link(20L, 1L, 100L, 2L, 200L, NodeLinkTypeEnum.BLOOD.getCode(), 9L)));
        when(familyNodeRepository.findByIds(List.of(100L, 200L))).thenReturn(List.of(
                node(100L, 1L, "甲一"), node(200L, 2L, "乙一")));
        when(familyRepository.findByIds(List.of(1L, 2L))).thenReturn(List.of(family, otherFamily));

        AdminGraphNetworkVO result = service.getFamilyNetwork(1L);

        assertEquals("甲族", result.getFamily().getName());
        assertEquals(List.of(100L, 101L, 200L), result.getNodes().stream().map(item -> item.getId()).toList());
        assertEquals("乙族", result.getNodes().get(2).getFamilyName());
        assertEquals(1, result.getRelations().size());
        assertEquals("亲子", result.getRelations().get(0).getRelationTypeLabel());
        assertEquals(1, result.getCrossFamilyLinks().size());
        assertFalse(result.getTruncated());
    }

    @Test
    void network_missingEndpoint_returnsPlaceholderAndIssue() {
        Family family = family(1L, "甲族");
        when(familyRepository.findById(1L)).thenReturn(family);
        when(familyNodeRepository.findByFamilyId(1L)).thenReturn(List.of(node(100L, 1L, "甲一")));
        when(familyRelationRepository.findByFamilyId(1L)).thenReturn(List.of());
        when(crossFamilyLinkRepository.findByFamilyId(1L)).thenReturn(List.of(
                link(20L, 1L, 100L, 2L, 200L, NodeLinkTypeEnum.BLOOD.getCode(), null)));
        when(familyNodeRepository.findByIds(List.of(100L, 200L))).thenReturn(List.of(node(100L, 1L, "甲一")));
        when(familyRepository.findByIds(List.of(1L, 2L))).thenReturn(List.of(family, family(2L, "乙族")));

        AdminGraphNetworkVO result = service.getFamilyNetwork(1L);

        assertEquals(2, result.getNodes().size());
        assertTrue(result.getNodes().stream().anyMatch(node -> Boolean.TRUE.equals(node.getMissing())));
        assertEquals(1, result.getCrossFamilyLinks().get(0).getIssues().size());
    }

    @Test
    void network_unknownFamily_rejected() {
        when(familyRepository.findById(404L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.getFamilyNetwork(404L));

        assertEquals("家族不存在", exception.getMessage());
        verifyNoInteractions(crossFamilyLinkRepository, familyNodeRepository, familyRelationRepository);
    }

    @Test
    void network_nullFamilyId_rejected() {
        BusinessException exception = assertThrows(BusinessException.class, () -> service.getFamilyNetwork(null));

        assertEquals("缺少家族ID", exception.getMessage());
        verifyNoInteractions(familyRepository, crossFamilyLinkRepository, familyNodeRepository, familyRelationRepository);
    }

    private Family family(Long id, String name) {
        Family family = new Family();
        family.setId(id);
        family.setName(name);
        family.setStatus("normal");
        return family;
    }

    private FamilyNode node(Long id, Long familyId, String name) {
        FamilyNode node = new FamilyNode();
        node.setId(id);
        node.setFamilyId(familyId);
        node.setName(name);
        node.setGender(1);
        node.setBirthDate(LocalDate.of(1950, 2, 3));
        node.setGeneration(2);
        return node;
    }

    private FamilyRelation relation(Long id, Long fromNodeId, Long toNodeId) {
        FamilyRelation relation = new FamilyRelation();
        relation.setId(id);
        relation.setFromNodeId(fromNodeId);
        relation.setToNodeId(toNodeId);
        relation.setRelationType(RelationTypeEnum.PARENT_CHILD.getCode());
        return relation;
    }

    private CrossFamilyLink link(Long id, Long srcFamilyId, Long srcNodeId, Long dstFamilyId, Long dstNodeId,
                                 Integer linkType, Long confirmedBy) {
        CrossFamilyLink link = new CrossFamilyLink();
        link.setId(id);
        link.setSrcFamilyId(srcFamilyId);
        link.setSrcNodeId(srcNodeId);
        link.setDstFamilyId(dstFamilyId);
        link.setDstNodeId(dstNodeId);
        link.setLinkType(linkType);
        link.setSource(confirmedBy == null ? NodeLinkSourceEnum.ACCOUNT.getCode() : NodeLinkSourceEnum.MANUAL.getCode());
        link.setConfirmedBy(confirmedBy);
        return link;
    }
}
