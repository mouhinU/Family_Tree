package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mouhin.family.tree.domain.entity.Notification;
import com.mouhin.family.tree.domain.repository.NotificationRepository;
import com.mouhin.family.tree.infrastructure.converter.NotificationConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.NotificationDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.NotificationMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知仓储实现
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
@Repository
public class NotificationRepositoryImpl implements NotificationRepository {

    private final NotificationMapper mapper;

    public NotificationRepositoryImpl(NotificationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Notification save(Notification notification) {
        NotificationDO doObj = NotificationConverter.toDO(notification);
        mapper.insert(doObj);
        notification.setId(doObj.getId());
        return notification;
    }

    @Override
    public List<Notification> findByUserId(Long userId, int offset, int limit) {
        LambdaQueryWrapper<NotificationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NotificationDO::getUserId, userId)
                .orderByDesc(NotificationDO::getCreateTime)
                .last("LIMIT " + limit + " OFFSET " + offset);
        List<NotificationDO> doList = mapper.selectList(wrapper);
        return NotificationConverter.toDomainList(doList);
    }

    @Override
    public long countByUserId(Long userId) {
        LambdaQueryWrapper<NotificationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NotificationDO::getUserId, userId);
        return mapper.selectCount(wrapper);
    }

    @Override
    public long countUnreadByUserId(Long userId) {
        LambdaQueryWrapper<NotificationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NotificationDO::getUserId, userId)
                .eq(NotificationDO::getRead, false);
        return mapper.selectCount(wrapper);
    }

    @Override
    public void markAsRead(Long id) {
        LambdaUpdateWrapper<NotificationDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(NotificationDO::getId, id)
                .set(NotificationDO::getRead, true)
                .set(NotificationDO::getUpdateTime, LocalDateTime.now());
        mapper.update(null, wrapper);
    }

    @Override
    public void markAllAsRead(Long userId) {
        LambdaUpdateWrapper<NotificationDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(NotificationDO::getUserId, userId)
                .eq(NotificationDO::getRead, false)
                .set(NotificationDO::getRead, true)
                .set(NotificationDO::getUpdateTime, LocalDateTime.now());
        mapper.update(null, wrapper);
    }

    @Override
    public void removeById(Long id) {
        mapper.deleteById(id);
    }
}
