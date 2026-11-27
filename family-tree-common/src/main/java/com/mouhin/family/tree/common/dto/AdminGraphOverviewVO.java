package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 平台跨族图谱总览视图对象
 *
 * @author Family-Tree
 * @date 2026-11-26
 */
@Getter
@Setter
public class AdminGraphOverviewVO {

    private LocalDateTime generatedTime;
    private Long familyCount;
    private Long linkedFamilyCount;
    private Long linkCount;
    private Long bloodLinkCount;
    private Long marriageLinkCount;
    private Long issueLinkCount;
    private Boolean truncated;
    private List<AdminGraphFamilyVO> families;
    private List<AdminGraphEdgeVO> edges;

    @Override
    public String toString() {
        return "AdminGraphOverviewVO{"
                + "generatedTime=" + generatedTime
                + ", familyCount=" + familyCount
                + ", linkedFamilyCount=" + linkedFamilyCount
                + ", linkCount=" + linkCount
                + ", bloodLinkCount=" + bloodLinkCount
                + ", marriageLinkCount=" + marriageLinkCount
                + ", issueLinkCount=" + issueLinkCount
                + ", truncated=" + truncated
                + '}';
    }
}
