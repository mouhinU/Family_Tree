package com.mouhin.family.tree.domain.repository;

import com.mouhin.family.tree.domain.entity.FamilyMessage;

import java.util.List;

/**
 * 家族留言仓储接口
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
public interface FamilyMessageRepository {

    /**
     * 保存留言（新建）
     *
     * @param message 留言领域对象
     * @return 保存后的留言（含ID）
     */
    FamilyMessage save(FamilyMessage message);

    /**
     * 根据ID查询留言
     *
     * @param id 留言ID
     * @return 留言领域对象，不存在返回null
     */
    FamilyMessage findById(Long id);

    /**
     * 根据家族ID分页查询留言
     *
     * @param familyId 家族ID
     * @param category 留言分类（null 表示全部分类）
     * @param offset   偏移量
     * @param limit    每页数量
     * @return 留言列表
     */
    List<FamilyMessage> findByFamilyId(Long familyId, String category, int offset, int limit);

    /**
     * 统计家族留言数量
     *
     * @param familyId 家族ID
     * @param category 留言分类（null 表示全部分类）
     * @return 留言数量
     */
    long countByFamilyId(Long familyId, String category);

    /**
     * 根据ID删除留言
     *
     * @param id 留言ID
     */
    void removeById(Long id);

    /**
     * 增加留言点赞数
     *
     * @param messageId 留言ID
     */
    void incrementLikeCount(Long messageId);

    /**
     * 减少留言点赞数
     *
     * @param messageId 留言ID
     */
    void decrementLikeCount(Long messageId);

    /**
     * 检查用户是否已点赞
     *
     * @param messageId 留言ID
     * @param userId    用户ID
     * @return 是否已点赞
     */
    boolean existsByMessageIdAndUserId(Long messageId, Long userId);

    /**
     * 保存点赞记录
     *
     * @param messageId 留言ID
     * @param userId    用户ID
     * @param familyId  家族ID
     */
    void saveLike(Long messageId, Long userId, Long familyId);

    /**
     * 删除点赞记录
     *
     * @param messageId 留言ID
     * @param userId    用户ID
     */
    void removeLike(Long messageId, Long userId);

    /**
     * 批量查询用户点赞状态
     *
     * @param messageIds 留言ID列表
     * @param userId     用户ID
     * @return 已点赞的留言ID集合
     */
    java.util.Set<Long> findLikedMessageIds(java.util.List<Long> messageIds, Long userId);
}
