package com.mouhin.family.tree.infrastructure.converter;

import com.mouhin.family.tree.domain.entity.PrivateMessage;
import com.mouhin.family.tree.infrastructure.persistence.entity.PrivateMessageDO;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * PrivateMessage 转换器
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
public final class PrivateMessageConverter {

    private PrivateMessageConverter() {
    }

    /**
     * DO 转领域对象
     *
     * @param doObj 数据对象
     * @return 领域实体
     */
    public static PrivateMessage toDomain(PrivateMessageDO doObj) {
        if (doObj == null) {
            return null;
        }
        PrivateMessage entity = new PrivateMessage();
        entity.setId(doObj.getId());
        entity.setFamilyId(doObj.getFamilyId());
        entity.setSenderId(doObj.getSenderId());
        entity.setSenderName(doObj.getSenderName());
        entity.setReceiverId(doObj.getReceiverId());
        entity.setContent(doObj.getContent());
        entity.setRead(doObj.getIsRead() != null && doObj.getIsRead() == 1);
        entity.setCreateTime(doObj.getCreateTime());
        return entity;
    }

    /**
     * 领域对象转 DO
     *
     * @param entity 领域实体
     * @return 数据对象
     */
    public static PrivateMessageDO toDO(PrivateMessage entity) {
        if (entity == null) {
            return null;
        }
        PrivateMessageDO doObj = new PrivateMessageDO();
        doObj.setId(entity.getId());
        doObj.setFamilyId(entity.getFamilyId());
        doObj.setSenderId(entity.getSenderId());
        doObj.setSenderName(entity.getSenderName());
        doObj.setReceiverId(entity.getReceiverId());
        doObj.setContent(entity.getContent());
        doObj.setIsRead(Boolean.TRUE.equals(entity.getRead()) ? 1 : 0);
        doObj.setCreateTime(entity.getCreateTime());
        return doObj;
    }

    /**
     * DO 列表转领域对象列表
     *
     * @param doList DO 列表
     * @return 领域实体列表
     */
    public static List<PrivateMessage> toDomainList(List<PrivateMessageDO> doList) {
        if (doList == null) {
            return Collections.emptyList();
        }
        return doList.stream()
                .map(PrivateMessageConverter::toDomain)
                .collect(Collectors.toList());
    }
}
