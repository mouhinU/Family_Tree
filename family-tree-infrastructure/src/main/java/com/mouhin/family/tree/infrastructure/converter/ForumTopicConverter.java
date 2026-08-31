package com.mouhin.family.tree.infrastructure.converter;

import com.mouhin.family.tree.domain.entity.ForumTopic;
import com.mouhin.family.tree.infrastructure.persistence.entity.ForumTopicDO;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ForumTopic 转换器
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
public final class ForumTopicConverter {

    private ForumTopicConverter() {
    }

    /**
     * DO 转领域对象
     *
     * @param doObj 数据对象
     * @return 领域实体
     */
    public static ForumTopic toDomain(ForumTopicDO doObj) {
        if (doObj == null) {
            return null;
        }
        ForumTopic entity = new ForumTopic();
        entity.setId(doObj.getId());
        entity.setFamilyId(doObj.getFamilyId());
        entity.setUserId(doObj.getUserId());
        entity.setUsername(doObj.getUsername());
        entity.setTitle(doObj.getTitle());
        entity.setContent(doObj.getContent());
        entity.setViewCount(doObj.getViewCount());
        entity.setReplyCount(doObj.getReplyCount());
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
    public static ForumTopicDO toDO(ForumTopic entity) {
        if (entity == null) {
            return null;
        }
        ForumTopicDO doObj = new ForumTopicDO();
        doObj.setId(entity.getId());
        doObj.setFamilyId(entity.getFamilyId());
        doObj.setUserId(entity.getUserId());
        doObj.setUsername(entity.getUsername());
        doObj.setTitle(entity.getTitle());
        doObj.setContent(entity.getContent());
        doObj.setViewCount(entity.getViewCount());
        doObj.setReplyCount(entity.getReplyCount());
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
    public static List<ForumTopic> toDomainList(List<ForumTopicDO> doList) {
        if (doList == null) {
            return Collections.emptyList();
        }
        return doList.stream()
                .map(ForumTopicConverter::toDomain)
                .collect(Collectors.toList());
    }
}
