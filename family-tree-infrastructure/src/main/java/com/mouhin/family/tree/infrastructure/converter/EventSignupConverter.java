package com.mouhin.family.tree.infrastructure.converter;

import com.mouhin.family.tree.domain.entity.EventSignup;
import com.mouhin.family.tree.infrastructure.persistence.entity.EventSignupDO;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * EventSignup 转换器
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
public final class EventSignupConverter {

    private EventSignupConverter() {
    }

    /**
     * DO 转领域对象
     *
     * @param doObj 数据对象
     * @return 领域实体
     */
    public static EventSignup toDomain(EventSignupDO doObj) {
        if (doObj == null) {
            return null;
        }
        EventSignup entity = new EventSignup();
        entity.setId(doObj.getId());
        entity.setEventId(doObj.getEventId());
        entity.setFamilyId(doObj.getFamilyId());
        entity.setUserId(doObj.getUserId());
        entity.setUsername(doObj.getUsername());
        entity.setAttendeeCount(doObj.getAttendeeCount());
        entity.setRemark(doObj.getRemark());
        entity.setCreateTime(doObj.getCreateTime());
        return entity;
    }

    /**
     * 领域对象转 DO
     *
     * @param entity 领域实体
     * @return 数据对象
     */
    public static EventSignupDO toDO(EventSignup entity) {
        if (entity == null) {
            return null;
        }
        EventSignupDO doObj = new EventSignupDO();
        doObj.setId(entity.getId());
        doObj.setEventId(entity.getEventId());
        doObj.setFamilyId(entity.getFamilyId());
        doObj.setUserId(entity.getUserId());
        doObj.setUsername(entity.getUsername());
        doObj.setAttendeeCount(entity.getAttendeeCount());
        doObj.setRemark(entity.getRemark());
        doObj.setCreateTime(entity.getCreateTime());
        return doObj;
    }

    /**
     * DO 列表转领域对象列表
     *
     * @param doList DO 列表
     * @return 领域实体列表
     */
    public static List<EventSignup> toDomainList(List<EventSignupDO> doList) {
        if (doList == null) {
            return Collections.emptyList();
        }
        return doList.stream()
                .map(EventSignupConverter::toDomain)
                .collect(Collectors.toList());
    }
}
