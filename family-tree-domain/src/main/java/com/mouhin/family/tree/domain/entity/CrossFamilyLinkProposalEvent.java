package com.mouhin.family.tree.domain.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 跨族链接提案状态事件
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
@Getter
@Setter
public class CrossFamilyLinkProposalEvent {

    private Long id;
    private Long proposalId;
    private String eventType;
    private String fromStatus;
    private String toStatus;
    private Long operatorId;
    private Long operatorFamilyId;
    private String comment;
    private String ipAddress;
    private LocalDateTime createTime;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CrossFamilyLinkProposalEvent that = (CrossFamilyLinkProposalEvent) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "CrossFamilyLinkProposalEvent{"
                + "id=" + id
                + ", proposalId=" + proposalId
                + ", eventType='" + eventType + '\''
                + ", fromStatus='" + fromStatus + '\''
                + ", toStatus='" + toStatus + '\''
                + '}';
    }
}
