package com.mouhin.family.tree.infrastructure.converter;

import com.mouhin.family.tree.domain.entity.FamilyRelation;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyRelationDO;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * FamilyRelation 转换器
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
public final class FamilyRelationConverter {

    private FamilyRelationConverter() {
    }

    /**
     * DO 转领域对象
     *
     * @param doObj 数据对象
     * @return 领域实体
     */
    public static FamilyRelation toDomain(FamilyRelationDO doObj) {
        if (doObj == null) {
            return null;
        }
        FamilyRelation entity = new FamilyRelation();
        entity.setId(doObj.getId());
        entity.setUserId(doObj.getUserId());
        entity.setFamilyId(doObj.getFamilyId());
        entity.setFromNodeId(doObj.getFromNodeId());
        entity.setToNodeId(doObj.getToNodeId());
        entity.setRelationType(doObj.getRelationType());
        entity.setMarriageDate(doObj.getMarriageDate());
        entity.setDivorceDate(doObj.getDivorceDate());
        entity.setDivorced(doObj.getDivorced());
        entity.setWidowed(doObj.getWidowed());
        entity.setMarriageOrder(doObj.getMarriageOrder());
        entity.setEndType(doObj.getEndType());
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
    public static FamilyRelationDO toDO(FamilyRelation entity) {
        if (entity == null) {
            return null;
        }
        FamilyRelationDO doObj = new FamilyRelationDO();
        doObj.setId(entity.getId());
        doObj.setUserId(entity.getUserId());
        doObj.setFamilyId(entity.getFamilyId());
        doObj.setFromNodeId(entity.getFromNodeId());
        doObj.setToNodeId(entity.getToNodeId());
        doObj.setRelationType(entity.getRelationType());
        doObj.setMarriageDate(entity.getMarriageDate());
        doObj.setDivorceDate(entity.getDivorceDate());
        doObj.setDivorced(entity.getDivorced());
        doObj.setWidowed(entity.getWidowed());
        doObj.setMarriageOrder(entity.getMarriageOrder());
        doObj.setEndType(entity.getEndType());
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
    public static List<FamilyRelation> toDomainList(List<FamilyRelationDO> doList) {
        if (doList == null) {
            return Collections.emptyList();
        }
        return doList.stream()
                .map(FamilyRelationConverter::toDomain)
                .collect(Collectors.toList());
    }
}
