package com.mouhin.family.tree.infrastructure.converter;

import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyNodeDO;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * FamilyNode 转换器
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
public final class FamilyNodeConverter {

    private FamilyNodeConverter() {
    }

    /**
     * DO 转领域对象
     *
     * @param doObj 数据对象
     * @return 领域实体
     */
    public static FamilyNode toDomain(FamilyNodeDO doObj) {
        if (doObj == null) {
            return null;
        }
        FamilyNode entity = new FamilyNode();
        entity.setId(doObj.getId());
        entity.setUserId(doObj.getUserId());
        entity.setFamilyId(doObj.getFamilyId());
        entity.setName(doObj.getName());
        entity.setGender(doObj.getGender());
        entity.setBirthDate(doObj.getBirthDate());
        entity.setDeathDate(doObj.getDeathDate());
        entity.setGeneration(doObj.getGeneration());
        entity.setBirthOrder(doObj.getBirthOrder());
        entity.setColorLabel(doObj.getColorLabel());
        entity.setAvatar(doObj.getAvatar());
        entity.setRemark(doObj.getRemark());
        entity.setLunarBirthDate(doObj.getLunarBirthDate());
        entity.setLunarDeathDate(doObj.getLunarDeathDate());
        entity.setZi(doObj.getZi());
        entity.setHao(doObj.getHao());
        entity.setHui(doObj.getHui());
        entity.setGraveLocation(doObj.getGraveLocation());
        entity.setSpouseName(doObj.getSpouseName());
        entity.setSpouseOriginFamily(doObj.getSpouseOriginFamily());
        entity.setBiography(doObj.getBiography());
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
    public static FamilyNodeDO toDO(FamilyNode entity) {
        if (entity == null) {
            return null;
        }
        FamilyNodeDO doObj = new FamilyNodeDO();
        doObj.setId(entity.getId());
        doObj.setUserId(entity.getUserId());
        doObj.setFamilyId(entity.getFamilyId());
        doObj.setName(entity.getName());
        doObj.setGender(entity.getGender());
        doObj.setBirthDate(entity.getBirthDate());
        doObj.setDeathDate(entity.getDeathDate());
        doObj.setGeneration(entity.getGeneration());
        doObj.setBirthOrder(entity.getBirthOrder());
        doObj.setColorLabel(entity.getColorLabel());
        doObj.setAvatar(entity.getAvatar());
        doObj.setRemark(entity.getRemark());
        doObj.setLunarBirthDate(entity.getLunarBirthDate());
        doObj.setLunarDeathDate(entity.getLunarDeathDate());
        doObj.setZi(entity.getZi());
        doObj.setHao(entity.getHao());
        doObj.setHui(entity.getHui());
        doObj.setGraveLocation(entity.getGraveLocation());
        doObj.setSpouseName(entity.getSpouseName());
        doObj.setSpouseOriginFamily(entity.getSpouseOriginFamily());
        doObj.setBiography(entity.getBiography());
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
    public static List<FamilyNode> toDomainList(List<FamilyNodeDO> doList) {
        if (doList == null) {
            return Collections.emptyList();
        }
        return doList.stream()
                .map(FamilyNodeConverter::toDomain)
                .collect(Collectors.toList());
    }
}
