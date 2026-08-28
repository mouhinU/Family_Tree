package com.mouhin.family.tree.infrastructure.converter;

import com.mouhin.family.tree.domain.entity.NodeHistory;
import com.mouhin.family.tree.infrastructure.persistence.entity.NodeHistoryDO;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * NodeHistory 转换器
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
public final class NodeHistoryConverter {

    private NodeHistoryConverter() {
    }

    /**
     * DO 转领域对象
     *
     * @param doObj 数据对象
     * @return 领域实体
     */
    public static NodeHistory toDomain(NodeHistoryDO doObj) {
        if (doObj == null) {
            return null;
        }
        NodeHistory entity = new NodeHistory();
        entity.setId(doObj.getId());
        entity.setNodeId(doObj.getNodeId());
        entity.setFamilyId(doObj.getFamilyId());
        entity.setOperationType(doObj.getOperationType());
        entity.setOperatorId(doObj.getOperatorId());
        entity.setOperatorName(doObj.getOperatorName());
        entity.setBeforeData(doObj.getBeforeData());
        entity.setAfterData(doObj.getAfterData());
        entity.setChangeSummary(doObj.getChangeSummary());
        entity.setIpAddress(doObj.getIpAddress());
        entity.setCreateTime(doObj.getCreateTime());
        entity.setVersionNumber(doObj.getVersionNumber());
        return entity;
    }

    /**
     * 领域对象转 DO
     *
     * @param entity 领域实体
     * @return 数据对象
     */
    public static NodeHistoryDO toDO(NodeHistory entity) {
        if (entity == null) {
            return null;
        }
        NodeHistoryDO doObj = new NodeHistoryDO();
        doObj.setId(entity.getId());
        doObj.setNodeId(entity.getNodeId());
        doObj.setFamilyId(entity.getFamilyId());
        doObj.setOperationType(entity.getOperationType());
        doObj.setOperatorId(entity.getOperatorId());
        doObj.setOperatorName(entity.getOperatorName());
        doObj.setBeforeData(entity.getBeforeData());
        doObj.setAfterData(entity.getAfterData());
        doObj.setChangeSummary(entity.getChangeSummary());
        doObj.setIpAddress(entity.getIpAddress());
        doObj.setCreateTime(entity.getCreateTime());
        doObj.setVersionNumber(entity.getVersionNumber());
        return doObj;
    }

    /**
     * DO 列表转领域对象列表
     *
     * @param doList DO 列表
     * @return 领域实体列表
     */
    public static List<NodeHistory> toDomainList(List<NodeHistoryDO> doList) {
        if (doList == null) {
            return Collections.emptyList();
        }
        return doList.stream()
                .map(NodeHistoryConverter::toDomain)
                .collect(Collectors.toList());
    }
}
