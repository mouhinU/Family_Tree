package com.mouhin.family.tree.infrastructure.converter;

import com.mouhin.family.tree.domain.entity.User;
import com.mouhin.family.tree.infrastructure.persistence.entity.SysUserDO;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * User 转换器
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
public final class UserConverter {

    private UserConverter() {
    }

    /**
     * DO 转领域对象
     *
     * @param doObj 数据对象
     * @return 领域实体
     */
    public static User toDomain(SysUserDO doObj) {
        if (doObj == null) {
            return null;
        }
        User entity = new User();
        entity.setId(doObj.getId());
        entity.setUsername(doObj.getUsername());
        entity.setPasswordHash(doObj.getPasswordHash());
        entity.setNickname(doObj.getNickname());
        entity.setGeneration(doObj.getGeneration());
        entity.setBirthDate(doObj.getBirthDate());
        entity.setNodeId(doObj.getNodeId());
        entity.setCurrentFamilyId(doObj.getCurrentFamilyId());
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
    public static SysUserDO toDO(User entity) {
        if (entity == null) {
            return null;
        }
        SysUserDO doObj = new SysUserDO();
        doObj.setId(entity.getId());
        doObj.setUsername(entity.getUsername());
        doObj.setPasswordHash(entity.getPasswordHash());
        doObj.setNickname(entity.getNickname());
        doObj.setGeneration(entity.getGeneration());
        doObj.setBirthDate(entity.getBirthDate());
        doObj.setNodeId(entity.getNodeId());
        doObj.setCurrentFamilyId(entity.getCurrentFamilyId());
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
    public static List<User> toDomainList(List<SysUserDO> doList) {
        if (doList == null) {
            return Collections.emptyList();
        }
        return doList.stream()
                .map(UserConverter::toDomain)
                .collect(Collectors.toList());
    }
}
