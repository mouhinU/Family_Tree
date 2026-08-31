package com.mouhin.family.tree.infrastructure.converter;

import com.mouhin.family.tree.domain.entity.FamilyPhoto;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyPhotoDO;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * FamilyPhoto 转换器
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
public final class FamilyPhotoConverter {

    private FamilyPhotoConverter() {
    }

    /**
     * DO 转领域对象
     *
     * @param doObj 数据对象
     * @return 领域实体
     */
    public static FamilyPhoto toDomain(FamilyPhotoDO doObj) {
        if (doObj == null) {
            return null;
        }
        FamilyPhoto entity = new FamilyPhoto();
        entity.setId(doObj.getId());
        entity.setFamilyId(doObj.getFamilyId());
        entity.setUserId(doObj.getUserId());
        entity.setUsername(doObj.getUsername());
        entity.setTitle(doObj.getTitle());
        entity.setDescription(doObj.getDescription());
        entity.setPhotoUrl(doObj.getPhotoUrl());
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
    public static FamilyPhotoDO toDO(FamilyPhoto entity) {
        if (entity == null) {
            return null;
        }
        FamilyPhotoDO doObj = new FamilyPhotoDO();
        doObj.setId(entity.getId());
        doObj.setFamilyId(entity.getFamilyId());
        doObj.setUserId(entity.getUserId());
        doObj.setUsername(entity.getUsername());
        doObj.setTitle(entity.getTitle());
        doObj.setDescription(entity.getDescription());
        doObj.setPhotoUrl(entity.getPhotoUrl());
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
    public static List<FamilyPhoto> toDomainList(List<FamilyPhotoDO> doList) {
        if (doList == null) {
            return Collections.emptyList();
        }
        return doList.stream()
                .map(FamilyPhotoConverter::toDomain)
                .collect(Collectors.toList());
    }
}
