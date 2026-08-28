package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 通知视图对象
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
@Getter
@Setter
public class NotificationVO {

    /**
     * 通知ID
     */
    private Long id;

    /**
     * 家族ID
     */
    private Long familyId;

    /**
     * 接收通知的用户ID
     */
    private Long userId;

    /**
     * 通知标题
     */
    private String title;

    /**
     * 通知内容
     */
    private String content;

    /**
     * 通知类型（MEMBER_JOIN / NODE_CREATE / SYSTEM）
     */
    private String notificationType;

    /**
     * 关联对象ID（如节点ID、用户ID）
     */
    private Long relatedId;

    /**
     * 是否已读
     */
    private Boolean read;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 距离创建时间的相对时间描述（如"3分钟前"、"2小时前"）
     */
    private String timeAgo;
}
