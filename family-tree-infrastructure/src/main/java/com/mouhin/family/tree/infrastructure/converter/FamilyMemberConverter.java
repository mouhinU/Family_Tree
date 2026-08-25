package com.mouhin.family.tree.infrastructure.converter;

import com.mouhin.family.tree.domain.entity.FamilyMember;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyMemberDO;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * FamilyMember 转换器
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
public final class FamilyMemberConverter {

    private FamilyMemberConverter() {
    }

    /**
     * DO 转领域对象
     *
     * @param doObj 数据对象
     * @return 领域实体
     */
    public static FamilyMember toDomain(FamilyMemberDO doObj) {
        if (doObj == null) {
            return null;
        }
        FamilyMember entity = new FamilyMember();
        entity.setId(doObj.getId());
        entity.setFamilyId(doObj.getFamilyId());
        entity.setUserId(doObj.getUserId());
        entity.setRole(doObj.getRole());
        entity.setJoinedTime(doObj.getJoinedTime());
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
    public static FamilyMemberDO toDO(FamilyMember entity) {
        if (entity == null) {
            return null;
        }
        FamilyMemberDO doObj = new FamilyMemberDO();
        doObj.setId(entity.getId());
        doObj.setFamilyId(entity.getFamilyId());
        doObj.setUserId(entity.getUserId());
        doObj.setRole(entity.getRole());
        doObj.setJoinedTime(entity.getJoinedTime());
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
    public static List<FamilyMember> toDomainList(List<FamilyMemberDO> doList) {
        if (doList == null) {
            return Collections.emptyList();
        }
        return doList.stream()
                .map(FamilyMemberConverter::toDomain)
                .collect(Collectors.toList());
    }
}
