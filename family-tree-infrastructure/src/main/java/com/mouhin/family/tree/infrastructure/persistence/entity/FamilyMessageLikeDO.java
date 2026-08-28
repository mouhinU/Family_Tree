package com.mouhin.family.tree.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 留言点赞记录数据对象
 *
 * @author Family-Tree
 * @date 2026-08-26
 */
@Getter
@Setter
@TableName("family_message_like")
public class FamilyMessageLikeDO {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 留言ID
     */
    private Long messageId;

    /**
     * 点赞用户ID
     */
    private Long userId;

    /**
     * 家族ID
     */
    private Long familyId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
