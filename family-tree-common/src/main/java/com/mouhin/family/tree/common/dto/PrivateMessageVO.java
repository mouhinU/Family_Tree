package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 私信消息展示对象
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
public class PrivateMessageVO {

    /**
     * 消息ID
     */
    private Long id;

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
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 是否为当前用户发送
     */
    private Boolean own;
}
