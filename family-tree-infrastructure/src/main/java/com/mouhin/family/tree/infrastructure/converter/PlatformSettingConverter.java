package com.mouhin.family.tree.infrastructure.converter;

import com.mouhin.family.tree.domain.entity.PlatformSetting;
import com.mouhin.family.tree.infrastructure.persistence.entity.PlatformSettingDO;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * PlatformSetting 转换器
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
public final class PlatformSettingConverter {

    private PlatformSettingConverter() {
    }

    /**
     * DO 转领域对象
     *
     * @param doObj 数据对象
     * @return 领域实体
     */
    public static PlatformSetting toDomain(PlatformSettingDO doObj) {
        if (doObj == null) {
            return null;
        }
        PlatformSetting entity = new PlatformSetting();
        entity.setId(doObj.getId());
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
    public static PlatformSettingDO toDO(PlatformSetting entity) {
        if (entity == null) {
            return null;
        }
        PlatformSettingDO doObj = new PlatformSettingDO();
        doObj.setId(entity.getId());
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
    public static List<PlatformSetting> toDomainList(List<PlatformSettingDO> doList) {
        if (doList == null) {
            return Collections.emptyList();
        }
        return doList.stream()
                .map(PlatformSettingConverter::toDomain)
                .collect(Collectors.toList());
    }
}
