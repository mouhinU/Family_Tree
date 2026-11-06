package com.mouhin.family.tree.infrastructure.converter;

import com.mouhin.family.tree.domain.entity.FamilyUserNode;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyUserNodeDO;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * FamilyUserNode 转换器
 *
 * @author Family-Tree
 * @date 2026-11-05
 */
public final class FamilyUserNodeConverter {

    private FamilyUserNodeConverter() {
    }

    /**
     * DO 转领域对象
     *
     * @param doObj 数据对象
     * @return 领域实体
     */
    public static FamilyUserNode toDomain(FamilyUserNodeDO doObj) {
        if (doObj == null) {
            return null;
        }
        FamilyUserNode entity = new FamilyUserNode();
        entity.setId(doObj.getId());
        entity.setFamilyId(doObj.getFamilyId());
        entity.setUserId(doObj.getUserId());
        entity.setNodeId(doObj.getNodeId());
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
    public static FamilyUserNodeDO toDO(FamilyUserNode entity) {
        if (entity == null) {
            return null;
        }
        FamilyUserNodeDO doObj = new FamilyUserNodeDO();
        doObj.setId(entity.getId());
        doObj.setFamilyId(entity.getFamilyId());
        doObj.setUserId(entity.getUserId());
        doObj.setNodeId(entity.getNodeId());
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
    public static List<FamilyUserNode> toDomainList(List<FamilyUserNodeDO> doList) {
        if (doList == null) {
            return Collections.emptyList();
        }
        return doList.stream()
                .map(FamilyUserNodeConverter::toDomain)
                .collect(Collectors.toList());
    }
}
