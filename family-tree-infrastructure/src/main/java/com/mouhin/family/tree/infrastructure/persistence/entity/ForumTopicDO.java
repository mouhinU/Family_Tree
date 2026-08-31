package com.mouhin.family.tree.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 家族论坛主题数据对象
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
@TableName("family_forum_topic")
public class ForumTopicDO {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 家族ID
     */
    private Long familyId;

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
     * 富文本内容
     */
    private String content;

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
     * 更新时间
     */
    private LocalDateTime updateTime;
}
