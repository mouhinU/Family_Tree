package com.mouhin.family.tree.infrastructure.converter;

import com.mouhin.family.tree.domain.entity.PhotoTag;
import com.mouhin.family.tree.infrastructure.persistence.entity.PhotoTagDO;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * PhotoTag 转换器
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
public final class PhotoTagConverter {

    private PhotoTagConverter() {
    }

    /**
     * DO 转领域对象
     *
     * @param doObj 数据对象
     * @return 领域实体
     */
    public static PhotoTag toDomain(PhotoTagDO doObj) {
        if (doObj == null) {
            return null;
        }
        PhotoTag entity = new PhotoTag();
        entity.setId(doObj.getId());
        entity.setPhotoId(doObj.getPhotoId());
        entity.setNodeId(doObj.getNodeId());
        entity.setNodeName(doObj.getNodeName());
        entity.setCreateTime(doObj.getCreateTime());
        return entity;
    }

    /**
     * 领域对象转 DO
     *
     * @param entity 领域实体
     * @return 数据对象
     */
    public static PhotoTagDO toDO(PhotoTag entity) {
        if (entity == null) {
            return null;
        }
        PhotoTagDO doObj = new PhotoTagDO();
        doObj.setId(entity.getId());
        doObj.setPhotoId(entity.getPhotoId());
        doObj.setNodeId(entity.getNodeId());
        doObj.setNodeName(entity.getNodeName());
        doObj.setCreateTime(entity.getCreateTime());
        return doObj;
    }

    /**
     * DO 列表转领域对象列表
     *
     * @param doList DO 列表
     * @return 领域实体列表
     */
    public static List<PhotoTag> toDomainList(List<PhotoTagDO> doList) {
        if (doList == null) {
            return Collections.emptyList();
        }
        return doList.stream()
                .map(PhotoTagConverter::toDomain)
                .collect(Collectors.toList());
    }
}
