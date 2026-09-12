package com.mouhin.family.tree.infrastructure.converter;

import com.mouhin.family.tree.domain.entity.TimelineEvent;
import com.mouhin.family.tree.infrastructure.persistence.entity.TimelineEventDO;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TimelineEvent 转换器
 *
 * @author Family-Tree
 * @date 2026-09-12
 */
public final class TimelineEventConverter {

    private TimelineEventConverter() {
    }

    /**
     * DO 转领域对象
     *
     * @param doObj 数据对象
     * @return 领域实体
     */
    public static TimelineEvent toDomain(TimelineEventDO doObj) {
        if (doObj == null) {
            return null;
        }
        TimelineEvent entity = new TimelineEvent();
        entity.setId(doObj.getId());
        entity.setFamilyId(doObj.getFamilyId());
        entity.setSourceType(doObj.getSourceType());
        entity.setSourceId(doObj.getSourceId());
        entity.setEventType(doObj.getEventType());
        entity.setEventDate(doObj.getEventDate());
        entity.setDescription(doObj.getDescription());
        entity.setNodeId(doObj.getNodeId());
        entity.setRelationId(doObj.getRelationId());
        entity.setIsNew(doObj.getIsNew());
        entity.setCreateTime(doObj.getCreateTime());
        entity.setUpdateTime(doObj.getUpdateTime());
        return entity;
    }

    /**
     * 领域对象转 DO
     *
     * @param entity 领域实体
     * @return 数据对象
     */
    public static TimelineEventDO toDO(TimelineEvent entity) {
        if (entity == null) {
            return null;
        }
        TimelineEventDO doObj = new TimelineEventDO();
        doObj.setId(entity.getId());
        doObj.setFamilyId(entity.getFamilyId());
        doObj.setSourceType(entity.getSourceType());
        doObj.setSourceId(entity.getSourceId());
        doObj.setEventType(entity.getEventType());
        doObj.setEventDate(entity.getEventDate());
        doObj.setDescription(entity.getDescription());
        doObj.setNodeId(entity.getNodeId());
        doObj.setRelationId(entity.getRelationId());
        doObj.setIsNew(entity.getIsNew());
        doObj.setCreateTime(entity.getCreateTime());
        doObj.setUpdateTime(entity.getUpdateTime());
        return doObj;
    }

    /**
     * DO 列表转领域对象列表
     *
     * @param doList DO 列表
     * @return 领域实体列表
     */
    public static List<TimelineEvent> toDomainList(List<TimelineEventDO> doList) {
        if (doList == null) {
            return Collections.emptyList();
        }
        return doList.stream()
                .map(TimelineEventConverter::toDomain)
                .collect(Collectors.toList());
    }
}
