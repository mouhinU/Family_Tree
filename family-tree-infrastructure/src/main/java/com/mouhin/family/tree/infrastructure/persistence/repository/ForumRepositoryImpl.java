package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mouhin.family.tree.domain.entity.ForumReply;
import com.mouhin.family.tree.domain.entity.ForumTopic;
import com.mouhin.family.tree.domain.repository.ForumRepository;
import com.mouhin.family.tree.infrastructure.converter.ForumReplyConverter;
import com.mouhin.family.tree.infrastructure.converter.ForumTopicConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.ForumReplyDO;
import com.mouhin.family.tree.infrastructure.persistence.entity.ForumTopicDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.ForumReplyMapper;
import com.mouhin.family.tree.infrastructure.persistence.mapper.ForumTopicMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Forum 仓储实现
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Repository
public class ForumRepositoryImpl implements ForumRepository {

    private final ForumTopicMapper topicMapper;
    private final ForumReplyMapper replyMapper;

    public ForumRepositoryImpl(ForumTopicMapper topicMapper, ForumReplyMapper replyMapper) {
        this.topicMapper = topicMapper;
        this.replyMapper = replyMapper;
    }

    @Override
    public ForumTopic saveTopic(ForumTopic topic) {
        ForumTopicDO doObj = ForumTopicConverter.toDO(topic);
        topicMapper.insert(doObj);
        topic.setId(doObj.getId());
        return topic;
    }

    @Override
    public ForumTopic findTopicById(Long id) {
        ForumTopicDO doObj = topicMapper.selectById(id);
        return ForumTopicConverter.toDomain(doObj);
    }

    @Override
    public List<ForumTopic> findTopicsByFamilyId(Long familyId, int offset, int size) {
        LambdaQueryWrapper<ForumTopicDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ForumTopicDO::getFamilyId, familyId)
                .orderByDesc(ForumTopicDO::getCreateTime)
                .last("LIMIT " + size + " OFFSET " + offset);
        List<ForumTopicDO> doList = topicMapper.selectList(wrapper);
        return ForumTopicConverter.toDomainList(doList);
    }

    @Override
    public long countTopicsByFamilyId(Long familyId) {
        LambdaQueryWrapper<ForumTopicDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ForumTopicDO::getFamilyId, familyId);
        return topicMapper.selectCount(wrapper);
    }

    @Override
    public void removeTopicById(Long id) {
        topicMapper.deleteById(id);
    }

    @Override
    public void incrementViewCount(Long topicId) {
        LambdaUpdateWrapper<ForumTopicDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ForumTopicDO::getId, topicId)
                .setSql("view_count = view_count + 1");
        topicMapper.update(null, wrapper);
    }

    @Override
    public void incrementReplyCount(Long topicId) {
        LambdaUpdateWrapper<ForumTopicDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ForumTopicDO::getId, topicId)
                .setSql("reply_count = reply_count + 1");
        topicMapper.update(null, wrapper);
    }

    @Override
    public void decrementReplyCount(Long topicId) {
        LambdaUpdateWrapper<ForumTopicDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ForumTopicDO::getId, topicId)
                .gt(ForumTopicDO::getReplyCount, 0)
                .setSql("reply_count = reply_count - 1");
        topicMapper.update(null, wrapper);
    }

    @Override
    public ForumReply saveReply(ForumReply reply) {
        ForumReplyDO doObj = ForumReplyConverter.toDO(reply);
        replyMapper.insert(doObj);
        reply.setId(doObj.getId());
        return reply;
    }

    @Override
    public ForumReply findReplyById(Long id) {
        ForumReplyDO doObj = replyMapper.selectById(id);
        return ForumReplyConverter.toDomain(doObj);
    }

    @Override
    public List<ForumReply> findRepliesByTopicId(Long topicId) {
        LambdaQueryWrapper<ForumReplyDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ForumReplyDO::getTopicId, topicId)
                .orderByAsc(ForumReplyDO::getCreateTime);
        List<ForumReplyDO> doList = replyMapper.selectList(wrapper);
        return ForumReplyConverter.toDomainList(doList);
    }

    @Override
    public void removeReplyById(Long id) {
        replyMapper.deleteById(id);
    }

    @Override
    public void removeRepliesByTopicId(Long topicId) {
        LambdaQueryWrapper<ForumReplyDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ForumReplyDO::getTopicId, topicId);
        replyMapper.delete(wrapper);
    }
}
