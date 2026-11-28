package com.mouhin.family.tree.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 跨族节点链接提案数据对象
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
@Getter
@Setter
@TableName("family_node_link_proposal")
public class FamilyNodeLinkProposalDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long srcFamilyId;
    private Long srcNodeId;
    private Long dstFamilyId;
    private Long dstNodeId;
    private Integer linkType;
    private String status;
    private Integer srcRequirement;
    private Integer dstRequirement;
    private Long activeSrcNodeId;
    private Long activeDstNodeId;
    private Long initiatorId;
    private Long initiatorFamilyId;
    private String reason;
    private String reviewComment;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime expireAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
