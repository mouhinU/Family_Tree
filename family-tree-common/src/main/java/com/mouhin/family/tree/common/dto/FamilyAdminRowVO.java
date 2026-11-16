package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 平台管理页家族列表行视图对象
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Getter
@Setter
public class FamilyAdminRowVO {

    /**
     * 家族ID
     */
    private Long id;

    /**
     * 家族名称
     */
    private String name;

    /**
     * 堂号
     */
    private String hallName;

    /**
     * 籍贯
     */
    private String ancestralHome;

    /**
     * 邀请码
     */
    private String inviteCode;

    /**
     * 族长（创建者）用户ID
     */
    private Long creatorId;

    /**
     * 族长昵称
     */
    private String creatorNickname;

    /**
     * 族长手机号（脱敏展示，如 138****0969）
     */
    private String creatorPhone;

    /**
     * 家族成员数
     */
    private Integer memberCount;

    /**
     * 族谱节点数
     */
    private Integer nodeCount;

    /**
     * 已故节点数
     */
    private Integer deceasedNodeCount;

    /**
     * 亲缘关系数
     */
    private Integer relationCount;

    /**
     * 辈分管理列数
     */
    private Integer generationCols;

    /**
     * 辈分管理行数
     */
    private Integer generationRows;

    /**
     * 家族状态编码：NORMAL / FROZEN / ARCHIVED
     */
    private String status;

    /**
     * 家族状态展示名
     */
    private String statusLabel;

    /**
     * 家族状态含义说明
     */
    private String statusDescription;

    /**
     * 家族创建时间
     */
    private LocalDateTime createTime;

    /**
     * 家族信息最近更新时间
     */
    private LocalDateTime updateTime;
}
