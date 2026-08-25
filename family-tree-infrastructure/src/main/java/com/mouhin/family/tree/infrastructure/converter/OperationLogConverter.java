package com.mouhin.family.tree.infrastructure.converter;

import com.mouhin.family.tree.domain.entity.OperationLog;
import com.mouhin.family.tree.infrastructure.persistence.entity.OperationLogDO;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * OperationLog 转换器
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
public final class OperationLogConverter {

    private OperationLogConverter() {
    }

    /**
     * DO 转领域对象
     *
     * @param doObj 数据对象
     * @return 领域实体
     */
    public static OperationLog toDomain(OperationLogDO doObj) {
        if (doObj == null) {
            return null;
        }
        OperationLog entity = new OperationLog();
        entity.setId(doObj.getId());
        entity.setUserId(doObj.getUserId());
        entity.setUsername(doObj.getUsername());
        entity.setOperationType(doObj.getOperationType());
        entity.setOperationDesc(doObj.getOperationDesc());
        entity.setTargetType(doObj.getTargetType());
        entity.setTargetId(doObj.getTargetId());
        entity.setFamilyId(doObj.getFamilyId());
        entity.setIpAddress(doObj.getIpAddress());
        entity.setCreateTime(doObj.getCreateTime());
        return entity;
    }

    /**
     * 领域对象转 DO
     *
     * @param entity 领域实体
     * @return 数据对象
     */
    public static OperationLogDO toDO(OperationLog entity) {
        if (entity == null) {
            return null;
        }
        OperationLogDO doObj = new OperationLogDO();
        doObj.setId(entity.getId());
        doObj.setUserId(entity.getUserId());
        doObj.setUsername(entity.getUsername());
        doObj.setOperationType(entity.getOperationType());
        doObj.setOperationDesc(entity.getOperationDesc());
        doObj.setTargetType(entity.getTargetType());
        doObj.setTargetId(entity.getTargetId());
        doObj.setFamilyId(entity.getFamilyId());
        doObj.setIpAddress(entity.getIpAddress());
        doObj.setCreateTime(entity.getCreateTime());
        return doObj;
    }

    /**
     * DO 列表转领域对象列表
     *
     * @param doList DO 列表
     * @return 领域实体列表
     */
    public static List<OperationLog> toDomainList(List<OperationLogDO> doList) {
        if (doList == null) {
            return Collections.emptyList();
        }
        return doList.stream()
                .map(OperationLogConverter::toDomain)
                .collect(Collectors.toList());
    }
}
