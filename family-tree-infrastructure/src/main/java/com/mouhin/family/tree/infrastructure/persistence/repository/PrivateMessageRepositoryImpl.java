package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mouhin.family.tree.domain.entity.PrivateMessage;
import com.mouhin.family.tree.domain.repository.PrivateMessageRepository;
import com.mouhin.family.tree.infrastructure.converter.PrivateMessageConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.PrivateMessageDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.PrivateMessageMapper;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

/**
 * PrivateMessage 仓储实现
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Repository
public class PrivateMessageRepositoryImpl implements PrivateMessageRepository {

    /**
     * 会话消息查询上限
     */
    private static final int CONVERSATION_LIMIT = 200;

    private final PrivateMessageMapper mapper;

    public PrivateMessageRepositoryImpl(PrivateMessageMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public PrivateMessage save(PrivateMessage message) {
        PrivateMessageDO doObj = PrivateMessageConverter.toDO(message);
        mapper.insert(doObj);
        message.setId(doObj.getId());
        return message;
    }

    @Override
    public List<PrivateMessage> findConversation(Long userId, Long peerId) {
        LambdaQueryWrapper<PrivateMessageDO> wrapper = new LambdaQueryWrapper<>();
        // (sender=userId AND receiver=peerId) OR (sender=peerId AND receiver=userId)
        wrapper.nested(w -> w.eq(PrivateMessageDO::getSenderId, userId)
                        .eq(PrivateMessageDO::getReceiverId, peerId))
                .or(w -> w.eq(PrivateMessageDO::getSenderId, peerId)
                        .eq(PrivateMessageDO::getReceiverId, userId));
        wrapper.orderByDesc(PrivateMessageDO::getCreateTime)
                .last("LIMIT " + CONVERSATION_LIMIT);
        List<PrivateMessageDO> doList = mapper.selectList(wrapper);
        // 倒序查询后反转为时间正序
        Collections.reverse(doList);
        return PrivateMessageConverter.toDomainList(doList);
    }

    @Override
    public List<PrivateMessage> findRecentByUser(Long userId, int limit) {
        LambdaQueryWrapper<PrivateMessageDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.nested(w -> w.eq(PrivateMessageDO::getSenderId, userId))
                .or(w -> w.eq(PrivateMessageDO::getReceiverId, userId));
        wrapper.orderByDesc(PrivateMessageDO::getCreateTime)
                .last("LIMIT " + limit);
        List<PrivateMessageDO> doList = mapper.selectList(wrapper);
        return PrivateMessageConverter.toDomainList(doList);
    }

    @Override
    public long countUnread(Long receiverId) {
        LambdaQueryWrapper<PrivateMessageDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrivateMessageDO::getReceiverId, receiverId)
                .eq(PrivateMessageDO::getIsRead, 0);
        return mapper.selectCount(wrapper);
    }

    @Override
    public void markRead(Long receiverId, Long senderId) {
        LambdaUpdateWrapper<PrivateMessageDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(PrivateMessageDO::getReceiverId, receiverId)
                .eq(PrivateMessageDO::getSenderId, senderId)
                .eq(PrivateMessageDO::getIsRead, 0)
                .set(PrivateMessageDO::getIsRead, 1);
        mapper.update(null, wrapper);
    }
}
