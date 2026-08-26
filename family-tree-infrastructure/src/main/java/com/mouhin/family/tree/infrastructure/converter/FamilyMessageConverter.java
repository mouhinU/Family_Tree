package com.mouhin.family.tree.infrastructure.converter;

import com.mouhin.family.tree.domain.entity.FamilyMessage;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyMessageDO;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * FamilyMessage 转换器
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
public final class FamilyMessageConverter {

    private FamilyMessageConverter() {
    }

    /**
     * DO 转领域对象
     *
     * @param doObj 数据对象
     * @return 领域实体
     */
    public static FamilyMessage toDomain(FamilyMessageDO doObj) {
        if (doObj == null) {
            return null;
        }
        FamilyMessage entity = new FamilyMessage();
        entity.setId(doObj.getId());
        entity.setFamilyId(doObj.getFamilyId());
        entity.setUserId(doObj.getUserId());
        entity.setUsername(doObj.getUsername());
        entity.setContent(doObj.getContent());
        entity.setLikeCount(doObj.getLikeCount());
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
    public static FamilyMessageDO toDO(FamilyMessage entity) {
        if (entity == null) {
            return null;
        }
        FamilyMessageDO doObj = new FamilyMessageDO();
        doObj.setId(entity.getId());
        doObj.setFamilyId(entity.getFamilyId());
        doObj.setUserId(entity.getUserId());
        doObj.setUsername(entity.getUsername());
        doObj.setContent(entity.getContent());
        doObj.setLikeCount(entity.getLikeCount());
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
    public static List<FamilyMessage> toDomainList(List<FamilyMessageDO> doList) {
        if (doList == null) {
            return Collections.emptyList();
        }
        return doList.stream()
                .map(FamilyMessageConverter::toDomain)
                .collect(Collectors.toList());
    }
}
