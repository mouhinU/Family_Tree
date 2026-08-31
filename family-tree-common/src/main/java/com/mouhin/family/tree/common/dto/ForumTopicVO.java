package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 论坛主题展示对象
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
public class ForumTopicVO {

    /**
     * 主题ID
     */
    private Long id;

    /**
     * 发帖用户ID
     */
    private Long userId;

    /**
     * 发帖用户名
     */
    private String username;

    /**
     * 主题标题
     */
    private String title;

    /**
     * 富文本内容（详情页返回）
     */
    private String content;

    /**
     * 内容摘要（列表页返回，纯文本）
     */
    private String summary;

    /**
     * 浏览数
     */
    private Long viewCount;

    /**
     * 回复数
     */
    private Long replyCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 是否为当前用户发布（用于前端判断删除权限）
     */
    private Boolean own;

    /**
     * 回复列表（详情页返回）
     */
    private List<ForumReplyVO> replies;
}
