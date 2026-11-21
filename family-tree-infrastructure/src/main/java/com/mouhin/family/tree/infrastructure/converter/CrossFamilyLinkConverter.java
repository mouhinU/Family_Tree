package com.mouhin.family.tree.infrastructure.converter;

import com.mouhin.family.tree.domain.entity.CrossFamilyLink;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyNodeLinkDO;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CrossFamilyLink 转换器
 *
 * @author Family-Tree
 * @date 2026-11-20
 */
public final class CrossFamilyLinkConverter {

    private CrossFamilyLinkConverter() {
    }

    /**
     * DO 转领域对象
     *
     * @param doObj 数据对象
     * @return 领域实体
     */
    public static CrossFamilyLink toDomain(FamilyNodeLinkDO doObj) {
        if (doObj == null) {
            return null;
        }
        CrossFamilyLink entity = new CrossFamilyLink();
        entity.setId(doObj.getId());
        entity.setSrcFamilyId(doObj.getSrcFamilyId());
        entity.setSrcNodeId(doObj.getSrcNodeId());
        entity.setDstFamilyId(doObj.getDstFamilyId());
        entity.setDstNodeId(doObj.getDstNodeId());
        entity.setLinkType(doObj.getLinkType());
        entity.setSource(doObj.getSource());
        entity.setConfirmedBy(doObj.getConfirmedBy());
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
    public static FamilyNodeLinkDO toDO(CrossFamilyLink entity) {
        if (entity == null) {
            return null;
        }
        FamilyNodeLinkDO doObj = new FamilyNodeLinkDO();
        doObj.setId(entity.getId());
        doObj.setSrcFamilyId(entity.getSrcFamilyId());
        doObj.setSrcNodeId(entity.getSrcNodeId());
        doObj.setDstFamilyId(entity.getDstFamilyId());
        doObj.setDstNodeId(entity.getDstNodeId());
        doObj.setLinkType(entity.getLinkType());
        doObj.setSource(entity.getSource());
        doObj.setConfirmedBy(entity.getConfirmedBy());
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
    public static List<CrossFamilyLink> toDomainList(List<FamilyNodeLinkDO> doList) {
        if (doList == null) {
            return Collections.emptyList();
        }
        return doList.stream()
                .map(CrossFamilyLinkConverter::toDomain)
                .collect(Collectors.toList());
    }
}
