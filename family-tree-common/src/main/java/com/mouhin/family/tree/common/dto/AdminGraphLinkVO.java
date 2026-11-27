package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 平台跨族图谱链接明细视图对象
 *
 * @author Family-Tree
 * @date 2026-11-26
 */
@Getter
@Setter
public class AdminGraphLinkVO {

    private Long id;
    private Long srcFamilyId;
    private Long srcNodeId;
    private String srcNodeName;
    private Long dstFamilyId;
    private Long dstNodeId;
    private String dstNodeName;
    private Integer linkType;
    private String linkTypeLabel;
    private Integer source;
    private String sourceLabel;
    private Long confirmedBy;
    private LocalDateTime createTime;
    private List<CrossFamilyLinkIssueVO> issues;

    @Override
    public String toString() {
        return "AdminGraphLinkVO{"
                + "id=" + id
                + ", srcNodeId=" + srcNodeId
                + ", dstNodeId=" + dstNodeId
                + ", linkType=" + linkType
                + ", source=" + source
                + ", confirmedBy=" + confirmedBy
                + ", createTime=" + createTime
                + '}';
    }
}
