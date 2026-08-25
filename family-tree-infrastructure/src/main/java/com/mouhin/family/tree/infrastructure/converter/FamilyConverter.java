package com.mouhin.family.tree.infrastructure.converter;

import com.mouhin.family.tree.domain.entity.Family;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyDO;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Family 转换器
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
public final class FamilyConverter {

    private FamilyConverter() {
    }

    /**
     * DO 转领域对象
     *
     * @param doObj 数据对象
     * @return 领域实体
     */
    public static Family toDomain(FamilyDO doObj) {
        if (doObj == null) {
            return null;
        }
        Family entity = new Family();
        entity.setId(doObj.getId());
        entity.setName(doObj.getName());
        entity.setInviteCode(doObj.getInviteCode());
        entity.setCreatorId(doObj.getCreatorId());
        entity.setHallName(doObj.getHallName());
        entity.setAncestralHome(doObj.getAncestralHome());
        entity.setGenerationCols(doObj.getGenerationCols());
        entity.setGenerationRows(doObj.getGenerationRows());
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
    public static FamilyDO toDO(Family entity) {
        if (entity == null) {
            return null;
        }
        FamilyDO doObj = new FamilyDO();
        doObj.setId(entity.getId());
        doObj.setName(entity.getName());
        doObj.setInviteCode(entity.getInviteCode());
        doObj.setCreatorId(entity.getCreatorId());
        doObj.setHallName(entity.getHallName());
        doObj.setAncestralHome(entity.getAncestralHome());
        doObj.setGenerationCols(entity.getGenerationCols());
        doObj.setGenerationRows(entity.getGenerationRows());
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
    public static List<Family> toDomainList(List<FamilyDO> doList) {
        if (doList == null) {
            return Collections.emptyList();
        }
        return doList.stream()
                .map(FamilyConverter::toDomain)
                .collect(Collectors.toList());
    }
}
