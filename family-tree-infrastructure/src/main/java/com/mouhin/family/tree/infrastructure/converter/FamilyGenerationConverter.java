package com.mouhin.family.tree.infrastructure.converter;

import com.mouhin.family.tree.domain.entity.FamilyGeneration;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyGenerationDO;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * FamilyGeneration 转换器
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
public final class FamilyGenerationConverter {

    private FamilyGenerationConverter() {
    }

    /**
     * DO 转领域对象
     *
     * @param doObj 数据对象
     * @return 领域实体
     */
    public static FamilyGeneration toDomain(FamilyGenerationDO doObj) {
        if (doObj == null) {
            return null;
        }
        FamilyGeneration entity = new FamilyGeneration();
        entity.setId(doObj.getId());
        entity.setUserId(doObj.getUserId());
        entity.setFamilyId(doObj.getFamilyId());
        entity.setGeneration(doObj.getGeneration());
        entity.setName(doObj.getName());
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
    public static FamilyGenerationDO toDO(FamilyGeneration entity) {
        if (entity == null) {
            return null;
        }
        FamilyGenerationDO doObj = new FamilyGenerationDO();
        doObj.setId(entity.getId());
        doObj.setUserId(entity.getUserId());
        doObj.setFamilyId(entity.getFamilyId());
        doObj.setGeneration(entity.getGeneration());
        doObj.setName(entity.getName());
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
    public static List<FamilyGeneration> toDomainList(List<FamilyGenerationDO> doList) {
        if (doList == null) {
            return Collections.emptyList();
        }
        return doList.stream()
                .map(FamilyGenerationConverter::toDomain)
                .collect(Collectors.toList());
    }
}
