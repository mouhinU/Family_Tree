package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 私信会话展示对象（与某族人的最近往来）
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
public class ConversationVO {

    /**
     * 对方用户ID
     */
    private Long peerUserId;

    /**
     * 对方昵称
     */
    private String peerName;

    /**
     * 最近一条消息内容
     */
    private String lastContent;

    /**
     * 最近一条消息时间
     */
    private LocalDateTime lastTime;

    /**
     * 对方发来的未读消息数
     */
    private Long unreadCount;
}
