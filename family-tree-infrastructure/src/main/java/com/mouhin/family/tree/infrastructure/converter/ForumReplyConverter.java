package com.mouhin.family.tree.infrastructure.converter;

import com.mouhin.family.tree.domain.entity.ForumReply;
import com.mouhin.family.tree.infrastructure.persistence.entity.ForumReplyDO;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ForumReply 转换器
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
public final class ForumReplyConverter {

    private ForumReplyConverter() {
    }

    /**
     * DO 转领域对象
     *
     * @param doObj 数据对象
     * @return 领域实体
     */
    public static ForumReply toDomain(ForumReplyDO doObj) {
        if (doObj == null) {
            return null;
        }
        ForumReply entity = new ForumReply();
        entity.setId(doObj.getId());
        entity.setTopicId(doObj.getTopicId());
        entity.setFamilyId(doObj.getFamilyId());
        entity.setUserId(doObj.getUserId());
        entity.setUsername(doObj.getUsername());
        entity.setContent(doObj.getContent());
        entity.setCreateTime(doObj.getCreateTime());
        return entity;
    }

    /**
     * 领域对象转 DO
     *
     * @param entity 领域实体
     * @return 数据对象
     */
    public static ForumReplyDO toDO(ForumReply entity) {
        if (entity == null) {
            return null;
        }
        ForumReplyDO doObj = new ForumReplyDO();
        doObj.setId(entity.getId());
        doObj.setTopicId(entity.getTopicId());
        doObj.setFamilyId(entity.getFamilyId());
        doObj.setUserId(entity.getUserId());
        doObj.setUsername(entity.getUsername());
        doObj.setContent(entity.getContent());
        doObj.setCreateTime(entity.getCreateTime());
        return doObj;
    }

    /**
     * DO 列表转领域对象列表
     *
     * @param doList DO 列表
     * @return 领域实体列表
     */
    public static List<ForumReply> toDomainList(List<ForumReplyDO> doList) {
        if (doList == null) {
            return Collections.emptyList();
        }
        return doList.stream()
                .map(ForumReplyConverter::toDomain)
                .collect(Collectors.toList());
    }
}
