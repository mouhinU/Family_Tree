package com.mouhin.family.tree.domain.repository;

import com.mouhin.family.tree.domain.entity.FamilyPhoto;
import com.mouhin.family.tree.domain.entity.PhotoTag;

import java.util.List;

/**
 * 家族相册仓储接口
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
public interface FamilyPhotoRepository {

    /**
     * 保存照片
     *
     * @param photo 照片领域对象
     * @return 保存后的照片（含ID）
     */
    FamilyPhoto save(FamilyPhoto photo);

    /**
     * 根据ID查询照片
     *
     * @param id 照片ID
     * @return 照片领域对象，不存在返回null
     */
    FamilyPhoto findById(Long id);

    /**
     * 查询家族照片列表（按创建时间倒序）
     *
     * @param familyId 家族ID
     * @return 照片列表
     */
    List<FamilyPhoto> findByFamilyId(Long familyId);

    /**
     * 删除照片
     *
     * @param id 照片ID
     */
    void removeById(Long id);

    /**
     * 保存照片人物标记
     *
     * @param tag 标记领域对象
     * @return 保存后的标记（含ID）
     */
    PhotoTag saveTag(PhotoTag tag);

    /**
     * 查询照片的人物标记列表
     *
     * @param photoId 照片ID
     * @return 标记列表
     */
    List<PhotoTag> findTagsByPhotoId(Long photoId);

    /**
     * 查询某照片是否已标记指定节点
     *
     * @param photoId 照片ID
     * @param nodeId  节点ID
     * @return 是否已标记
     */
    boolean existsTag(Long photoId, Long nodeId);

    /**
     * 删除指定标记
     *
     * @param tagId 标记ID
     */
    void removeTagById(Long tagId);

    /**
     * 删除照片的全部标记
     *
     * @param photoId 照片ID
     */
    void removeTagsByPhotoId(Long photoId);

    /**
     * 查询指定标记
     *
     * @param tagId 标记ID
     * @return 标记领域对象，不存在返回null
     */
    PhotoTag findTagById(Long tagId);
}
