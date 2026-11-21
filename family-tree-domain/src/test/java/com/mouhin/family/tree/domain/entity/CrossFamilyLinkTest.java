package com.mouhin.family.tree.domain.entity;

import com.mouhin.family.tree.common.enums.NodeLinkSourceEnum;
import com.mouhin.family.tree.common.enums.NodeLinkTypeEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 跨族节点链接实体单元测试：覆盖完整性校验、端点规范化与双端访问助手。
 *
 * @author Family-Tree
 * @date 2026-11-20
 */
class CrossFamilyLinkTest {

    @Test
    void validate_completeLink_passes() {
        CrossFamilyLink link = build(1L, 100L, 10L, 200L, 20L);
        link.validate();
    }

    @Test
    void validate_missingEndpoint_throws() {
        CrossFamilyLink link = build(1L, 100L, 10L, 200L, null);
        assertThrows(BusinessException.class, link::validate);
    }

    @Test
    void validate_sameFamilyEndpoints_throws() {
        CrossFamilyLink link = build(1L, 100L, 10L, 100L, 20L);
        assertThrows(BusinessException.class, link::validate);
    }

    @Test
    void validate_missingTypeOrSource_throws() {
        CrossFamilyLink noType = build(1L, 100L, 10L, 200L, 20L);
        noType.setLinkType(null);
        assertThrows(BusinessException.class, noType::validate);

        CrossFamilyLink noSource = build(1L, 100L, 10L, 200L, 20L);
        noSource.setSource(null);
        assertThrows(BusinessException.class, noSource::validate);
    }

    @Test
    void orderEndpoints_swapsBothNodeAndFamilyColumns() {
        CrossFamilyLink link = build(9L, 100L, 30L, 200L, 7L);
        link.orderEndpoints();
        assertEquals(Long.valueOf(7L), link.getSrcNodeId());
        assertEquals(Long.valueOf(200L), link.getSrcFamilyId());
        assertEquals(Long.valueOf(30L), link.getDstNodeId());
        assertEquals(Long.valueOf(100L), link.getDstFamilyId());
    }

    @Test
    void orderEndpoints_alreadyOrderedOrIncomplete_noop() {
        CrossFamilyLink link = build(9L, 100L, 7L, 200L, 30L);
        link.orderEndpoints();
        assertEquals(Long.valueOf(7L), link.getSrcNodeId());

        CrossFamilyLink incomplete = new CrossFamilyLink();
        incomplete.setSrcNodeId(30L);
        incomplete.orderEndpoints();
        assertEquals(Long.valueOf(30L), incomplete.getSrcNodeId());
    }

    @Test
    void endpointHelpers_returnOtherSide() {
        CrossFamilyLink link = build(9L, 100L, 7L, 200L, 30L);
        assertTrue(link.touchesNode(7L));
        assertTrue(link.touchesNode(30L));
        assertFalse(link.touchesNode(11L));
        assertEquals(Long.valueOf(30L), link.otherNodeId(7L));
        assertEquals(Long.valueOf(7L), link.otherNodeId(30L));
        assertEquals(Long.valueOf(200L), link.otherFamilyId(100L));
        assertEquals(Long.valueOf(100L), link.otherFamilyId(200L));
    }

    @Test
    void isAccountSourced_onlyForAccountSource() {
        CrossFamilyLink account = build(9L, 100L, 7L, 200L, 30L);
        assertTrue(account.isAccountSourced());
        account.setSource(NodeLinkSourceEnum.MANUAL.getCode());
        assertFalse(account.isAccountSourced());
    }

    @Test
    void equalsAndHashCode_byIdOnly() {
        CrossFamilyLink a = build(9L, 100L, 7L, 200L, 30L);
        CrossFamilyLink b = build(9L, 100L, 7L, 200L, 31L);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotSame(a, b);
        assertSame(a, a);
        assertFalse(a.equals(null));
    }

    @Test
    void toString_containsKeyFields() {
        CrossFamilyLink link = build(9L, 100L, 7L, 200L, 30L);
        assertTrue(link.toString().contains("9"));
        assertTrue(link.toString().contains("CrossFamilyLink"));
    }

    // ========== 辅助方法 ==========

    private CrossFamilyLink build(Long id, Long srcFamilyId, Long srcNodeId, Long dstFamilyId, Long dstNodeId) {
        CrossFamilyLink link = new CrossFamilyLink();
        link.setId(id);
        link.setSrcFamilyId(srcFamilyId);
        link.setSrcNodeId(srcNodeId);
        link.setDstFamilyId(dstFamilyId);
        link.setDstNodeId(dstNodeId);
        link.setLinkType(NodeLinkTypeEnum.BLOOD.getCode());
        link.setSource(NodeLinkSourceEnum.ACCOUNT.getCode());
        return link;
    }
}
