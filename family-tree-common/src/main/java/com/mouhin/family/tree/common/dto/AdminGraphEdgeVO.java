package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 平台跨族图谱家族聚合边视图对象
 *
 * @author Family-Tree
 * @date 2026-11-26
 */
@Getter
@Setter
public class AdminGraphEdgeVO {

    private Long srcFamilyId;
    private Long dstFamilyId;
    private Long linkCount;
    private Long bloodLinkCount;
    private Long marriageLinkCount;
    private Long issueCount;

    @Override
    public String toString() {
        return "AdminGraphEdgeVO{"
                + "srcFamilyId=" + srcFamilyId
                + ", dstFamilyId=" + dstFamilyId
                + ", linkCount=" + linkCount
                + ", bloodLinkCount=" + bloodLinkCount
                + ", marriageLinkCount=" + marriageLinkCount
                + ", issueCount=" + issueCount
                + '}';
    }
}
