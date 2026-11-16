package com.mouhin.family.tree.infrastructure.converter;

import com.mouhin.family.tree.domain.entity.FamilySetting;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilySettingDO;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * FamilySetting 转换器
 *
 * @author Family-Tree
 * @date 2026-11-15
 */
public final class FamilySettingConverter {

    private FamilySettingConverter() {
    }

    /**
     * DO 转领域对象
     *
     * @param doObj 数据对象
     * @return 领域实体
     */
    public static FamilySetting toDomain(FamilySettingDO doObj) {
        if (doObj == null) {
            return null;
        }
        FamilySetting entity = new FamilySetting();
        entity.setId(doObj.getId());
        entity.setFamilyId(doObj.getFamilyId());
        entity.setSettingKey(doObj.getSettingKey());
        entity.setSettingValue(doObj.getSettingValue());
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
    public static FamilySettingDO toDO(FamilySetting entity) {
        if (entity == null) {
            return null;
        }
        FamilySettingDO doObj = new FamilySettingDO();
        doObj.setId(entity.getId());
        doObj.setFamilyId(entity.getFamilyId());
        doObj.setSettingKey(entity.getSettingKey());
        doObj.setSettingValue(entity.getSettingValue());
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
    public static List<FamilySetting> toDomainList(List<FamilySettingDO> doList) {
        if (doList == null) {
            return Collections.emptyList();
        }
        return doList.stream()
                .map(FamilySettingConverter::toDomain)
                .collect(Collectors.toList());
    }
}
