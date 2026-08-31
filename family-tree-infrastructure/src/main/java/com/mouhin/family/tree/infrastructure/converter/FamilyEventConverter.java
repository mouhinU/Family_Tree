package com.mouhin.family.tree.infrastructure.converter;

import com.mouhin.family.tree.domain.entity.FamilyEvent;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyEventDO;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * FamilyEvent 转换器
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
public final class FamilyEventConverter {

    private FamilyEventConverter() {
    }

    /**
     * DO 转领域对象
     *
     * @param doObj 数据对象
     * @return 领域实体
     */
    public static FamilyEvent toDomain(FamilyEventDO doObj) {
        if (doObj == null) {
            return null;
        }
        FamilyEvent entity = new FamilyEvent();
        entity.setId(doObj.getId());
        entity.setFamilyId(doObj.getFamilyId());
        entity.setUserId(doObj.getUserId());
        entity.setUsername(doObj.getUsername());
        entity.setTitle(doObj.getTitle());
        entity.setDescription(doObj.getDescription());
        entity.setEventTime(doObj.getEventTime());
        entity.setLocation(doObj.getLocation());
        entity.setTotalCost(doObj.getTotalCost());
        entity.setStatus(doObj.getStatus());
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
    public static FamilyEventDO toDO(FamilyEvent entity) {
        if (entity == null) {
            return null;
        }
        FamilyEventDO doObj = new FamilyEventDO();
        doObj.setId(entity.getId());
        doObj.setFamilyId(entity.getFamilyId());
        doObj.setUserId(entity.getUserId());
        doObj.setUsername(entity.getUsername());
        doObj.setTitle(entity.getTitle());
        doObj.setDescription(entity.getDescription());
        doObj.setEventTime(entity.getEventTime());
        doObj.setLocation(entity.getLocation());
        doObj.setTotalCost(entity.getTotalCost());
        doObj.setStatus(entity.getStatus());
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
    public static List<FamilyEvent> toDomainList(List<FamilyEventDO> doList) {
        if (doList == null) {
            return Collections.emptyList();
        }
        return doList.stream()
                .map(FamilyEventConverter::toDomain)
                .collect(Collectors.toList());
    }
}
