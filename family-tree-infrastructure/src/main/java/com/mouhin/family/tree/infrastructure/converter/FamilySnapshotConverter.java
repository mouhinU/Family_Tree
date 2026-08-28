package com.mouhin.family.tree.infrastructure.converter;

import com.mouhin.family.tree.domain.entity.FamilySnapshot;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilySnapshotDO;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * FamilySnapshot 转换器
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
public final class FamilySnapshotConverter {

    private FamilySnapshotConverter() {
    }

    /**
     * DO 转领域对象
     *
     * @param doObj 数据对象
     * @return 领域实体
     */
    public static FamilySnapshot toDomain(FamilySnapshotDO doObj) {
        if (doObj == null) {
            return null;
        }
        FamilySnapshot entity = new FamilySnapshot();
        entity.setId(doObj.getId());
        entity.setFamilyId(doObj.getFamilyId());
        entity.setSnapshotName(doObj.getSnapshotName());
        entity.setDescription(doObj.getDescription());
        entity.setCreatorId(doObj.getCreatorId());
        entity.setCreatorName(doObj.getCreatorName());
        entity.setNodeCount(doObj.getNodeCount());
        entity.setRelationCount(doObj.getRelationCount());
        entity.setSnapshotData(doObj.getSnapshotData());
        entity.setCreateTime(doObj.getCreateTime());
        return entity;
    }

    /**
     * 领域对象转 DO
     *
     * @param entity 领域实体
     * @return 数据对象
     */
    public static FamilySnapshotDO toDO(FamilySnapshot entity) {
        if (entity == null) {
            return null;
        }
        FamilySnapshotDO doObj = new FamilySnapshotDO();
        doObj.setId(entity.getId());
        doObj.setFamilyId(entity.getFamilyId());
        doObj.setSnapshotName(entity.getSnapshotName());
        doObj.setDescription(entity.getDescription());
        doObj.setCreatorId(entity.getCreatorId());
        doObj.setCreatorName(entity.getCreatorName());
        doObj.setNodeCount(entity.getNodeCount());
        doObj.setRelationCount(entity.getRelationCount());
        doObj.setSnapshotData(entity.getSnapshotData());
        doObj.setCreateTime(entity.getCreateTime());
        return doObj;
    }

    /**
     * DO 列表转领域对象列表
     *
     * @param doList DO 列表
     * @return 领域实体列表
     */
    public static List<FamilySnapshot> toDomainList(List<FamilySnapshotDO> doList) {
        if (doList == null) {
            return Collections.emptyList();
        }
        return doList.stream()
                .map(FamilySnapshotConverter::toDomain)
                .collect(Collectors.toList());
    }
}
