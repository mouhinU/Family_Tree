package com.mouhin.family.tree.infrastructure.converter;

import com.mouhin.family.tree.domain.entity.FamilyOffering;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyOfferingDO;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * FamilyOffering 转换器
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
public final class FamilyOfferingConverter {

    private FamilyOfferingConverter() {
    }

    /**
     * DO 转领域对象
     *
     * @param doObj 数据对象
     * @return 领域实体
     */
    public static FamilyOffering toDomain(FamilyOfferingDO doObj) {
        if (doObj == null) {
            return null;
        }
        FamilyOffering entity = new FamilyOffering();
        entity.setId(doObj.getId());
        entity.setUserId(doObj.getUserId());
        entity.setFamilyId(doObj.getFamilyId());
        entity.setNodeId(doObj.getNodeId());
        entity.setOfferingType(doObj.getOfferingType());
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
    public static FamilyOfferingDO toDO(FamilyOffering entity) {
        if (entity == null) {
            return null;
        }
        FamilyOfferingDO doObj = new FamilyOfferingDO();
        doObj.setId(entity.getId());
        doObj.setUserId(entity.getUserId());
        doObj.setFamilyId(entity.getFamilyId());
        doObj.setNodeId(entity.getNodeId());
        doObj.setOfferingType(entity.getOfferingType());
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
    public static List<FamilyOffering> toDomainList(List<FamilyOfferingDO> doList) {
        if (doList == null) {
            return Collections.emptyList();
        }
        return doList.stream()
                .map(FamilyOfferingConverter::toDomain)
                .collect(Collectors.toList());
    }
}
