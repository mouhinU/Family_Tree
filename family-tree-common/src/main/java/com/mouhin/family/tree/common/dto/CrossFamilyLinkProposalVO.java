package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 跨族链接提案视图对象
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
@Getter
@Setter
public class CrossFamilyLinkProposalVO {

    private Long id;
    private Long srcFamilyId;
    private String srcFamilyName;
    private Long srcNodeId;
    private String srcNodeName;
    private Long dstFamilyId;
    private String dstFamilyName;
    private Long dstNodeId;
    private String dstNodeName;
    private Integer linkType;
    private String status;
    private Integer srcRequirement;
    private Integer dstRequirement;
    private Long srcAgreementCount;
    private Long dstAgreementCount;
    private Long initiatorId;
    private String initiatorName;
    private Long initiatorFamilyId;
    private String reason;
    private String reviewComment;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime expireAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Boolean expired;
    private Boolean viewerCanVote;
    private Boolean viewerHasVoted;
    private List<CrossFamilyLinkProposalVoteVO> votes;
    private List<CrossFamilyLinkProposalEventVO> events;

    @Override
    public String toString() {
        return "CrossFamilyLinkProposalVO{"
                + "id=" + id
                + ", srcFamilyId=" + srcFamilyId
                + ", srcNodeId=" + srcNodeId
                + ", dstFamilyId=" + dstFamilyId
                + ", dstNodeId=" + dstNodeId
                + ", status='" + status + '\''
                + ", expireAt=" + expireAt
                + '}';
    }
}
