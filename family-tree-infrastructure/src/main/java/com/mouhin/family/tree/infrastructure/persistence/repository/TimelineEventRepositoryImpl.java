package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.family.tree.domain.entity.TimelineEvent;
import com.mouhin.family.tree.domain.repository.TimelineEventRepository;
import com.mouhin.family.tree.infrastructure.converter.TimelineEventConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.TimelineEventDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.TimelineEventMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 时间线事件仓储实现
 *
 * @author Family-Tree
 * @date 2026-09-12
 */
@Repository
public class TimelineEventRepositoryImpl implements TimelineEventRepository {

    private final TimelineEventMapper timelineEventMapper;

    public TimelineEventRepositoryImpl(TimelineEventMapper timelineEventMapper) {
        this.timelineEventMapper = timelineEventMapper;
    }

    @Override
    public void save(TimelineEvent event) {
        TimelineEventDO doObj = TimelineEventConverter.toDO(event);
        timelineEventMapper.insert(doObj);
        event.setId(doObj.getId());
    }

    @Override
    public TimelineEvent findBySourceAndType(String sourceType, Long sourceId, String eventType) {
        LambdaQueryWrapper<TimelineEventDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TimelineEventDO::getSourceType, sourceType)
                .eq(TimelineEventDO::getSourceId, sourceId)
                .eq(TimelineEventDO::getEventType, eventType);
        TimelineEventDO doObj = timelineEventMapper.selectOne(wrapper);
        return TimelineEventConverter.toDomain(doObj);
    }

    @Override
    public void update(TimelineEvent event) {
        TimelineEventDO doObj = TimelineEventConverter.toDO(event);
        timelineEventMapper.updateById(doObj);
    }

    @Override
    public void removeById(Long id) {
        timelineEventMapper.deleteById(id);
    }

    @Override
    public List<TimelineEvent> listByFamilyId(Long familyId) {
        LambdaQueryWrapper<TimelineEventDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TimelineEventDO::getFamilyId, familyId)
                .orderByDesc(TimelineEventDO::getEventDate);
        List<TimelineEventDO> doList = timelineEventMapper.selectList(wrapper);
        return TimelineEventConverter.toDomainList(doList);
    }

    @Override
    public List<TimelineEvent> listByFamilyIdAndEventType(Long familyId, String eventType) {
        LambdaQueryWrapper<TimelineEventDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TimelineEventDO::getFamilyId, familyId);
        if (eventType != null && !eventType.isBlank()) {
            wrapper.eq(TimelineEventDO::getEventType, eventType);
        }
        wrapper.orderByDesc(TimelineEventDO::getEventDate);
        List<TimelineEventDO> doList = timelineEventMapper.selectList(wrapper);
        return TimelineEventConverter.toDomainList(doList);
    }
}
