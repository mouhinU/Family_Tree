package com.mouhin.family.tree.domain.repository;

import com.mouhin.family.tree.domain.entity.Notification;

import java.util.List;

/**
 * 通知仓储接口
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
public interface NotificationRepository {

    /**
     * 保存通知
     *
     * @param notification 通知领域对象
     * @return 保存后的通知（含ID）
     */
    Notification save(Notification notification);

    /**
     * 根据用户ID分页查询通知列表
     *
     * @param userId 用户ID
     * @param offset 偏移量
     * @param limit  每页数量
     * @return 通知列表
     */
    List<Notification> findByUserId(Long userId, int offset, int limit);

    /**
     * 统计用户通知总数
     *
     * @param userId 用户ID
     * @return 通知总数
     */
    long countByUserId(Long userId);

    /**
     * 统计用户未读通知数量
     *
     * @param userId 用户ID
     * @return 未读通知数量
     */
    long countUnreadByUserId(Long userId);

    /**
     * 标记通知为已读
     *
     * @param id 通知ID
     */
    void markAsRead(Long id);

    /**
     * 标记用户所有通知为已读
     *
     * @param userId 用户ID
     */
    void markAllAsRead(Long userId);

    /**
     * 根据ID删除通知
     *
     * @param id 通知ID
     */
    void removeById(Long id);
}
