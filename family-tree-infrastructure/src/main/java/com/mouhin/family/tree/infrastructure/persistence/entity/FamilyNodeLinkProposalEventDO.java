package com.mouhin.family.tree.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 跨族节点链接提案事件数据对象
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
@Getter
@Setter
@TableName("family_node_link_proposal_event")
public class FamilyNodeLinkProposalEventDO {

    @TableId(type = IdType.AUTO)
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
}
