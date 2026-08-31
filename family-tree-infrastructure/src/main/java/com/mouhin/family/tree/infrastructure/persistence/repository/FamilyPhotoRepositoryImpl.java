package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.family.tree.domain.entity.FamilyPhoto;
import com.mouhin.family.tree.domain.entity.PhotoTag;
import com.mouhin.family.tree.domain.repository.FamilyPhotoRepository;
import com.mouhin.family.tree.infrastructure.converter.FamilyPhotoConverter;
import com.mouhin.family.tree.infrastructure.converter.PhotoTagConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyPhotoDO;
import com.mouhin.family.tree.infrastructure.persistence.entity.PhotoTagDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.FamilyPhotoMapper;
import com.mouhin.family.tree.infrastructure.persistence.mapper.PhotoTagMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * FamilyPhoto 仓储实现
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Repository
public class FamilyPhotoRepositoryImpl implements FamilyPhotoRepository {

    private final FamilyPhotoMapper photoMapper;
    private final PhotoTagMapper tagMapper;

    public FamilyPhotoRepositoryImpl(FamilyPhotoMapper photoMapper, PhotoTagMapper tagMapper) {
        this.photoMapper = photoMapper;
        this.tagMapper = tagMapper;
    }

    @Override
    public FamilyPhoto save(FamilyPhoto photo) {
        FamilyPhotoDO doObj = FamilyPhotoConverter.toDO(photo);
        photoMapper.insert(doObj);
        photo.setId(doObj.getId());
        return photo;
    }

    @Override
    public FamilyPhoto findById(Long id) {
        FamilyPhotoDO doObj = photoMapper.selectById(id);
        return FamilyPhotoConverter.toDomain(doObj);
    }

    @Override
    public List<FamilyPhoto> findByFamilyId(Long familyId) {
        LambdaQueryWrapper<FamilyPhotoDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyPhotoDO::getFamilyId, familyId)
                .orderByDesc(FamilyPhotoDO::getCreateTime);
        List<FamilyPhotoDO> doList = photoMapper.selectList(wrapper);
        return FamilyPhotoConverter.toDomainList(doList);
    }

    @Override
    public void removeById(Long id) {
        photoMapper.deleteById(id);
    }

    @Override
    public PhotoTag saveTag(PhotoTag tag) {
        PhotoTagDO doObj = PhotoTagConverter.toDO(tag);
        tagMapper.insert(doObj);
        tag.setId(doObj.getId());
        return tag;
    }

    @Override
    public List<PhotoTag> findTagsByPhotoId(Long photoId) {
        LambdaQueryWrapper<PhotoTagDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PhotoTagDO::getPhotoId, photoId)
                .orderByAsc(PhotoTagDO::getCreateTime);
        List<PhotoTagDO> doList = tagMapper.selectList(wrapper);
        return PhotoTagConverter.toDomainList(doList);
    }

    @Override
    public boolean existsTag(Long photoId, Long nodeId) {
        LambdaQueryWrapper<PhotoTagDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PhotoTagDO::getPhotoId, photoId)
                .eq(PhotoTagDO::getNodeId, nodeId);
        return tagMapper.selectCount(wrapper) > 0;
    }

    @Override
    public void removeTagById(Long tagId) {
        tagMapper.deleteById(tagId);
    }

    @Override
    public void removeTagsByPhotoId(Long photoId) {
        LambdaQueryWrapper<PhotoTagDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PhotoTagDO::getPhotoId, photoId);
        tagMapper.delete(wrapper);
    }

    @Override
    public PhotoTag findTagById(Long tagId) {
        PhotoTagDO doObj = tagMapper.selectById(tagId);
        return PhotoTagConverter.toDomain(doObj);
    }
}
