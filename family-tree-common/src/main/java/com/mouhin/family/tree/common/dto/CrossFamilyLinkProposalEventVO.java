package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 跨族链接提案事件视图对象
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
@Getter
@Setter
public class CrossFamilyLinkProposalEventVO {

    private Long id;
    private Long proposalId;
    private String eventType;
    private String fromStatus;
    private String toStatus;
    private Long operatorId;
    private String operatorName;
    private Long operatorFamilyId;
    private String comment;
    private String ipAddress;
    private LocalDateTime createTime;

    @Override
    public String toString() {
        return "CrossFamilyLinkProposalEventVO{"
                + "id=" + id
                + ", proposalId=" + proposalId
                + ", eventType='" + eventType + '\''
                + ", fromStatus='" + fromStatus + '\''
                + ", toStatus='" + toStatus + '\''
                + '}';
    }
}
