package com.mouhin.family.tree.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 跨族节点链接提案投票数据对象
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
@Getter
@Setter
@TableName("family_node_link_proposal_vote")
public class FamilyNodeLinkProposalVoteDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long proposalId;
    private Long familyId;
    private Long nodeId;
    private Long userId;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
