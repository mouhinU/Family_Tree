package com.mouhin.family.tree.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 家族论坛回复数据对象
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
@TableName("family_forum_reply")
public class ForumReplyDO {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 主题ID
     */
    private Long topicId;

    /**
     * 家族ID
     */
    private Long familyId;

    /**
     * 回复用户ID
     */
    private Long userId;

    /**
     * 回复用户名
     */
    private String username;

    /**
     * 回复内容
     */
    private String content;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
