package com.mouhin.family.tree.domain.service;

import com.mouhin.family.tree.common.enums.CrossFamilyLinkIssueEnum;
import com.mouhin.family.tree.common.enums.NodeLinkSourceEnum;
import com.mouhin.family.tree.common.enums.NodeLinkTypeEnum;
import com.mouhin.family.tree.domain.entity.CrossFamilyLink;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 跨族链接数据质量领域服务单元测试
 *
 * @author Family-Tree
 * @date 2026-11-26
 */
class CrossFamilyLinkQualityDomainServiceTest {

    private final CrossFamilyLinkQualityDomainService service = new CrossFamilyLinkQualityDomainService();

    @Test
    void cleanLinkHasNoIssue() {
        CrossFamilyLink link = link(1L, 100L, 10L, 200L, 20L,
                NodeLinkTypeEnum.BLOOD.getCode(), NodeLinkSourceEnum.ACCOUNT.getCode(), null);

        List<CrossFamilyLinkIssueEnum> issues = service.inspect(link,
                node(10L, 100L), node(20L, 200L), true, true);

        assertTrue(issues.isEmpty());
    }

    @Test
    void missingEndpointsAndFamiliesAreReported() {
        CrossFamilyLink link = link(1L, 100L, 10L, 200L, 20L,
                NodeLinkTypeEnum.BLOOD.getCode(), NodeLinkSourceEnum.ACCOUNT.getCode(), null);

        List<CrossFamilyLinkIssueEnum> issues = service.inspect(link, null, null, false, false);

        assertEquals(List.of(CrossFamilyLinkIssueEnum.ORPHAN_NODE,
                CrossFamilyLinkIssueEnum.FAMILY_MISSING), issues);
    }

    @Test
    void familyMismatchAndSameFamilyAreReportedOnce() {
        CrossFamilyLink link = link(1L, 100L, 10L, 200L, 20L,
                NodeLinkTypeEnum.MARRIAGE.getCode(), NodeLinkSourceEnum.MANUAL.getCode(), 9L);

        List<CrossFamilyLinkIssueEnum> issues = service.inspect(link,
                node(10L, 300L), node(20L, 300L), true, true);

        assertEquals(List.of(CrossFamilyLinkIssueEnum.FAMILY_MISMATCH,
                CrossFamilyLinkIssueEnum.SAME_FAMILY_ENDPOINT), issues);
    }

    @Test
    void invalidTypeAndSourceAreReported() {
        CrossFamilyLink link = link(1L, 100L, 10L, 200L, 20L, 99, 98, 9L);

        List<CrossFamilyLinkIssueEnum> issues = service.inspect(link,
                node(10L, 100L), node(20L, 200L), true, true);

        assertEquals(List.of(CrossFamilyLinkIssueEnum.INVALID_LINK_TYPE,
                CrossFamilyLinkIssueEnum.INVALID_SOURCE), issues);
    }

    @Test
    void unorderedEndpointsAndMissingConfirmerAreReported() {
        CrossFamilyLink link = link(1L, 100L, 20L, 200L, 10L,
                NodeLinkTypeEnum.BLOOD.getCode(), NodeLinkSourceEnum.SUGGESTED.getCode(), null);

        List<CrossFamilyLinkIssueEnum> issues = service.inspect(link,
                node(20L, 100L), node(10L, 200L), true, true);

        assertEquals(List.of(CrossFamilyLinkIssueEnum.UNORDERED_ENDPOINT,
                CrossFamilyLinkIssueEnum.MISSING_CONFIRMER), issues);
    }

    private CrossFamilyLink link(Long id, Long srcFamilyId, Long srcNodeId, Long dstFamilyId, Long dstNodeId,
                                 Integer linkType, Integer source, Long confirmedBy) {
        CrossFamilyLink link = new CrossFamilyLink();
        link.setId(id);
        link.setSrcFamilyId(srcFamilyId);
        link.setSrcNodeId(srcNodeId);
        link.setDstFamilyId(dstFamilyId);
        link.setDstNodeId(dstNodeId);
        link.setLinkType(linkType);
        link.setSource(source);
        link.setConfirmedBy(confirmedBy);
        return link;
    }

    private FamilyNode node(Long id, Long familyId) {
        FamilyNode node = new FamilyNode();
        node.setId(id);
        node.setFamilyId(familyId);
        return node;
    }
}
