package com.mouhin.family.tree.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 私信消息数据对象
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
@TableName("family_private_message")
public class PrivateMessageDO {

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
     * 发送者ID
     */
    private Long senderId;

    /**
     * 发送者昵称
     */
    private String senderName;

    /**
     * 接收者ID
     */
    private Long receiverId;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 是否已读：1是 0否
     */
    private Integer isRead;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
