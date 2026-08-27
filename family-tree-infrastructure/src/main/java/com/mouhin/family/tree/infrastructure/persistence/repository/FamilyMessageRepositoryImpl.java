package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mouhin.family.tree.domain.entity.FamilyMessage;
import com.mouhin.family.tree.domain.repository.FamilyMessageRepository;
import com.mouhin.family.tree.infrastructure.converter.FamilyMessageConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyMessageDO;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyMessageLikeDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.FamilyMessageLikeMapper;
import com.mouhin.family.tree.infrastructure.persistence.mapper.FamilyMessageMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * FamilyMessage 仓储实现
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Repository
public class FamilyMessageRepositoryImpl implements FamilyMessageRepository {

    private final FamilyMessageMapper mapper;
    private final FamilyMessageLikeMapper likeMapper;

    public FamilyMessageRepositoryImpl(FamilyMessageMapper mapper,
                                       FamilyMessageLikeMapper likeMapper) {
        this.mapper = mapper;
        this.likeMapper = likeMapper;
    }

    @Override
    public FamilyMessage save(FamilyMessage entity) {
        FamilyMessageDO doObj = FamilyMessageConverter.toDO(entity);
        mapper.insert(doObj);
        entity.setId(doObj.getId());
        return entity;
    }

    @Override
    public FamilyMessage findById(Long id) {
        FamilyMessageDO doObj = mapper.selectById(id);
        return FamilyMessageConverter.toDomain(doObj);
    }

    @Override
    public List<FamilyMessage> findByFamilyId(Long familyId, String category, int offset, int size) {
        LambdaQueryWrapper<FamilyMessageDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyMessageDO::getFamilyId, familyId);
        wrapper.isNull(FamilyMessageDO::getParentId);
        if (category != null && !category.isBlank()) {
            wrapper.eq(FamilyMessageDO::getCategory, category);
        }
        wrapper.orderByDesc(FamilyMessageDO::getCreateTime)
                .last("LIMIT " + size + " OFFSET " + offset);
        List<FamilyMessageDO> doList = mapper.selectList(wrapper);
        return FamilyMessageConverter.toDomainList(doList);
    }

    @Override
    public long countByFamilyId(Long familyId, String category) {
        LambdaQueryWrapper<FamilyMessageDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyMessageDO::getFamilyId, familyId);
        wrapper.isNull(FamilyMessageDO::getParentId);
        if (category != null && !category.isBlank()) {
            wrapper.eq(FamilyMessageDO::getCategory, category);
        }
        return mapper.selectCount(wrapper);
    }

    @Override
    public void removeById(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public void incrementLikeCount(Long messageId) {
        LambdaUpdateWrapper<FamilyMessageDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(FamilyMessageDO::getId, messageId)
                .setSql("like_count = like_count + 1");
        mapper.update(null, wrapper);
    }

    @Override
    public void decrementLikeCount(Long messageId) {
        LambdaUpdateWrapper<FamilyMessageDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(FamilyMessageDO::getId, messageId)
                .gt(FamilyMessageDO::getLikeCount, 0)
                .setSql("like_count = like_count - 1");
        mapper.update(null, wrapper);
    }

    @Override
    public boolean existsByMessageIdAndUserId(Long messageId, Long userId) {
        LambdaQueryWrapper<FamilyMessageLikeDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyMessageLikeDO::getMessageId, messageId)
                .eq(FamilyMessageLikeDO::getUserId, userId);
        return likeMapper.selectCount(wrapper) > 0;
    }

    @Override
    public void saveLike(Long messageId, Long userId, Long familyId) {
        FamilyMessageLikeDO likeDO = new FamilyMessageLikeDO();
        likeDO.setMessageId(messageId);
        likeDO.setUserId(userId);
        likeDO.setFamilyId(familyId);
        likeDO.setCreateTime(LocalDateTime.now());
        likeMapper.insert(likeDO);
    }

    @Override
    public void removeLike(Long messageId, Long userId) {
        LambdaQueryWrapper<FamilyMessageLikeDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyMessageLikeDO::getMessageId, messageId)
                .eq(FamilyMessageLikeDO::getUserId, userId);
        likeMapper.delete(wrapper);
    }

    @Override
    public Set<Long> findLikedMessageIds(List<Long> messageIds, Long userId) {
        if (messageIds == null || messageIds.isEmpty()) {
            return Collections.emptySet();
        }
        LambdaQueryWrapper<FamilyMessageLikeDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(FamilyMessageLikeDO::getMessageId, messageIds)
                .eq(FamilyMessageLikeDO::getUserId, userId);
        List<FamilyMessageLikeDO> likes = likeMapper.selectList(wrapper);
        return likes.stream()
                .map(FamilyMessageLikeDO::getMessageId)
                .collect(Collectors.toSet());
    }

    @Override
    public List<FamilyMessage> findByParentId(Long parentId) {
        LambdaQueryWrapper<FamilyMessageDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyMessageDO::getParentId, parentId)
                .orderByAsc(FamilyMessageDO::getCreateTime);
        List<FamilyMessageDO> doList = mapper.selectList(wrapper);
        return FamilyMessageConverter.toDomainList(doList);
    }

    @Override
    public void incrementReplyCount(Long messageId) {
        LambdaUpdateWrapper<FamilyMessageDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(FamilyMessageDO::getId, messageId)
                .setSql("reply_count = reply_count + 1");
        mapper.update(null, wrapper);
    }

    @Override
    public void decrementReplyCount(Long messageId) {
        LambdaUpdateWrapper<FamilyMessageDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(FamilyMessageDO::getId, messageId)
                .gt(FamilyMessageDO::getReplyCount, 0)
                .setSql("reply_count = reply_count - 1");
        mapper.update(null, wrapper);
    }

    @Override
    public void removeByParentId(Long parentId) {
        LambdaQueryWrapper<FamilyMessageDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyMessageDO::getParentId, parentId);
        mapper.delete(wrapper);
    }

    @Override
    public long countByParentId(Long parentId) {
        LambdaQueryWrapper<FamilyMessageDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyMessageDO::getParentId, parentId);
        return mapper.selectCount(wrapper);
    }
}
