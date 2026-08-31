package com.mouhin.family.tree.domain.repository;

import com.mouhin.family.tree.domain.entity.ForumReply;
import com.mouhin.family.tree.domain.entity.ForumTopic;

import java.util.List;

/**
 * 家族论坛仓储接口
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
public interface ForumRepository {

    /**
     * 保存主题
     *
     * @param topic 主题领域对象
     * @return 保存后的主题（含ID）
     */
    ForumTopic saveTopic(ForumTopic topic);

    /**
     * 根据ID查询主题
     *
     * @param id 主题ID
     * @return 主题领域对象，不存在返回null
     */
    ForumTopic findTopicById(Long id);

    /**
     * 分页查询家族主题列表（按创建时间倒序）
     *
     * @param familyId 家族ID
     * @param offset   偏移量
     * @param size     每页大小
     * @return 主题列表
     */
    List<ForumTopic> findTopicsByFamilyId(Long familyId, int offset, int size);

    /**
     * 统计家族主题数量
     *
     * @param familyId 家族ID
     * @return 主题数量
     */
    long countTopicsByFamilyId(Long familyId);

    /**
     * 删除主题
     *
     * @param id 主题ID
     */
    void removeTopicById(Long id);

    /**
     * 主题浏览数加一
     *
     * @param topicId 主题ID
     */
    void incrementViewCount(Long topicId);

    /**
     * 主题回复数加一
     *
     * @param topicId 主题ID
     */
    void incrementReplyCount(Long topicId);

    /**
     * 主题回复数减一
     *
     * @param topicId 主题ID
     */
    void decrementReplyCount(Long topicId);

    /**
     * 保存回复
     *
     * @param reply 回复领域对象
     * @return 保存后的回复（含ID）
     */
    ForumReply saveReply(ForumReply reply);

    /**
     * 根据ID查询回复
     *
     * @param id 回复ID
     * @return 回复领域对象，不存在返回null
     */
    ForumReply findReplyById(Long id);

    /**
     * 查询主题的回复列表（按时间正序）
     *
     * @param topicId 主题ID
     * @return 回复列表
     */
    List<ForumReply> findRepliesByTopicId(Long topicId);

    /**
     * 删除回复
     *
     * @param id 回复ID
     */
    void removeReplyById(Long id);

    /**
     * 删除主题的全部回复
     *
     * @param topicId 主题ID
     */
    void removeRepliesByTopicId(Long topicId);
}
