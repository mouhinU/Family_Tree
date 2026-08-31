package com.mouhin.family.tree.domain.repository;

import com.mouhin.family.tree.domain.entity.PrivateMessage;

import java.util.List;

/**
 * 私信消息仓储接口
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
public interface PrivateMessageRepository {

    /**
     * 保存私信
     *
     * @param message 私信领域对象
     * @return 保存后的私信（含ID）
     */
    PrivateMessage save(PrivateMessage message);

    /**
     * 查询两人之间的会话消息（按时间正序，最多返回最近 200 条）
     *
     * @param userId 用户A
     * @param peerId 用户B
     * @return 消息列表
     */
    List<PrivateMessage> findConversation(Long userId, Long peerId);

    /**
     * 查询用户相关的最近消息（收发双向，用于聚合会话列表）
     *
     * @param userId 用户ID
     * @param limit  最大条数
     * @return 消息列表（按时间倒序）
     */
    List<PrivateMessage> findRecentByUser(Long userId, int limit);

    /**
     * 统计用户未读私信数
     *
     * @param receiverId 接收者ID
     * @return 未读数量
     */
    long countUnread(Long receiverId);

    /**
     * 标记某发信人发来的消息为已读
     *
     * @param receiverId 接收者ID
     * @param senderId   发信者ID
     */
    void markRead(Long receiverId, Long senderId);
}
