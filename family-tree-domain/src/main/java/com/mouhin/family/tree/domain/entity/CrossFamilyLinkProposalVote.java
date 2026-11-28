package com.mouhin.family.tree.domain.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 跨族链接提案同意投票
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
@Getter
@Setter
public class CrossFamilyLinkProposalVote {

    private Long id;
    private Long proposalId;
    private Long familyId;
    private Long nodeId;
    private Long userId;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CrossFamilyLinkProposalVote that = (CrossFamilyLinkProposalVote) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "CrossFamilyLinkProposalVote{"
                + "id=" + id
                + ", proposalId=" + proposalId
                + ", familyId=" + familyId
                + ", nodeId=" + nodeId
                + ", userId=" + userId
                + ", status=" + status
                + '}';
    }
}
