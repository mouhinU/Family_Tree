package com.mouhin.family.tree.infrastructure.converter;

import com.mouhin.family.tree.domain.entity.MemorialMessage;
import com.mouhin.family.tree.infrastructure.persistence.entity.MemorialMessageDO;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MemorialMessage 转换器
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
public final class MemorialMessageConverter {

    private MemorialMessageConverter() {
    }

    /**
     * DO 转领域对象
     *
     * @param doObj 数据对象
     * @return 领域实体
     */
    public static MemorialMessage toDomain(MemorialMessageDO doObj) {
        if (doObj == null) {
            return null;
        }
        MemorialMessage entity = new MemorialMessage();
        entity.setId(doObj.getId());
        entity.setFamilyId(doObj.getFamilyId());
        entity.setNodeId(doObj.getNodeId());
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
    public static MemorialMessageDO toDO(MemorialMessage entity) {
        if (entity == null) {
            return null;
        }
        MemorialMessageDO doObj = new MemorialMessageDO();
        doObj.setId(entity.getId());
        doObj.setFamilyId(entity.getFamilyId());
        doObj.setNodeId(entity.getNodeId());
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
    public static List<MemorialMessage> toDomainList(List<MemorialMessageDO> doList) {
        if (doList == null) {
            return Collections.emptyList();
        }
        return doList.stream()
                .map(MemorialMessageConverter::toDomain)
                .collect(Collectors.toList());
    }
}
