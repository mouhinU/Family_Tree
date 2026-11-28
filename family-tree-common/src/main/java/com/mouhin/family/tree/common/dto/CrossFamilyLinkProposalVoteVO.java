package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 跨族链接提案投票视图对象
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
@Getter
@Setter
public class CrossFamilyLinkProposalVoteVO {

    private Long id;
    private Long proposalId;
    private Long familyId;
    private Long nodeId;
    private String nodeName;
    private Long userId;
    private String username;
    private String status;
    private Boolean valid;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @Override
    public String toString() {
        return "CrossFamilyLinkProposalVoteVO{"
                + "id=" + id
                + ", proposalId=" + proposalId
                + ", familyId=" + familyId
                + ", nodeId=" + nodeId
                + ", userId=" + userId
                + ", status=" + status
                + '}';
    }
}
