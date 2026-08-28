package com.mouhin.family.tree.infrastructure.converter;

import com.mouhin.family.tree.domain.entity.Notification;
import com.mouhin.family.tree.infrastructure.persistence.entity.NotificationDO;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Notification 转换器
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
public final class NotificationConverter {

    private NotificationConverter() {
    }

    /**
     * DO 转领域对象
     *
     * @param doObj 数据对象
     * @return 领域实体
     */
    public static Notification toDomain(NotificationDO doObj) {
        if (doObj == null) {
            return null;
        }
        Notification entity = new Notification();
        entity.setId(doObj.getId());
        entity.setFamilyId(doObj.getFamilyId());
        entity.setUserId(doObj.getUserId());
        entity.setTitle(doObj.getTitle());
        entity.setContent(doObj.getContent());
        entity.setNotificationType(doObj.getNotificationType());
        entity.setRelatedId(doObj.getRelatedId());
        entity.setRead(doObj.getRead());
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
    public static NotificationDO toDO(Notification entity) {
        if (entity == null) {
            return null;
        }
        NotificationDO doObj = new NotificationDO();
        doObj.setId(entity.getId());
        doObj.setFamilyId(entity.getFamilyId());
        doObj.setUserId(entity.getUserId());
        doObj.setTitle(entity.getTitle());
        doObj.setContent(entity.getContent());
        doObj.setNotificationType(entity.getNotificationType());
        doObj.setRelatedId(entity.getRelatedId());
        doObj.setRead(entity.getRead());
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
    public static List<Notification> toDomainList(List<NotificationDO> doList) {
        if (doList == null) {
            return Collections.emptyList();
        }
        return doList.stream()
                .map(NotificationConverter::toDomain)
                .collect(Collectors.toList());
    }
}
