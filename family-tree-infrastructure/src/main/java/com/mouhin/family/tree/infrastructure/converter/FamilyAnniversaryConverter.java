package com.mouhin.family.tree.infrastructure.converter;

import com.mouhin.family.tree.domain.entity.FamilyAnniversary;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyAnniversaryDO;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * FamilyAnniversary 转换器
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
public final class FamilyAnniversaryConverter {

    private FamilyAnniversaryConverter() {
    }

    /**
     * DO 转领域对象
     *
     * @param doObj 数据对象
     * @return 领域实体
     */
    public static FamilyAnniversary toDomain(FamilyAnniversaryDO doObj) {
        if (doObj == null) {
            return null;
        }
        FamilyAnniversary entity = new FamilyAnniversary();
        entity.setId(doObj.getId());
        entity.setFamilyId(doObj.getFamilyId());
        entity.setNodeId(doObj.getNodeId());
        entity.setUserId(doObj.getUserId());
        entity.setTitle(doObj.getTitle());
        entity.setCategory(doObj.getCategory());
        entity.setAnniversaryDate(doObj.getAnniversaryDate());
        entity.setRemark(doObj.getRemark());
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
    public static FamilyAnniversaryDO toDO(FamilyAnniversary entity) {
        if (entity == null) {
            return null;
        }
        FamilyAnniversaryDO doObj = new FamilyAnniversaryDO();
        doObj.setId(entity.getId());
        doObj.setFamilyId(entity.getFamilyId());
        doObj.setNodeId(entity.getNodeId());
        doObj.setUserId(entity.getUserId());
        doObj.setTitle(entity.getTitle());
        doObj.setCategory(entity.getCategory());
        doObj.setAnniversaryDate(entity.getAnniversaryDate());
        doObj.setRemark(entity.getRemark());
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
    public static List<FamilyAnniversary> toDomainList(List<FamilyAnniversaryDO> doList) {
        if (doList == null) {
            return Collections.emptyList();
        }
        return doList.stream()
                .map(FamilyAnniversaryConverter::toDomain)
                .collect(Collectors.toList());
    }
}
