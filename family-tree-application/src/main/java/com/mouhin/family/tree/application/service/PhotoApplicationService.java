package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.PhotoTagDTO;
import com.mouhin.family.tree.common.dto.PhotoTagVO;
import com.mouhin.family.tree.common.dto.PhotoVO;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyPhoto;
import com.mouhin.family.tree.domain.entity.PhotoTag;
import com.mouhin.family.tree.domain.event.OperationPerformedEvent;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.FamilyPhotoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 家族相册应用服务
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Service
public class PhotoApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(PhotoApplicationService.class);

    private final FamilyPhotoRepository familyPhotoRepository;
    private final FamilyNodeRepository familyNodeRepository;
    private final ApplicationEventPublisher eventPublisher;

    public PhotoApplicationService(FamilyPhotoRepository familyPhotoRepository,
                                   FamilyNodeRepository familyNodeRepository,
                                   ApplicationEventPublisher eventPublisher) {
        this.familyPhotoRepository = familyPhotoRepository;
        this.familyNodeRepository = familyNodeRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 保存照片记录（文件上传完成后调用）
     *
     * @param familyId 家族ID
     * @param userId   上传用户ID
     * @param username 上传用户名
     * @param photo    照片领域对象（含标题、描述、访问地址）
     * @return 保存后的照片展示对象
     */
    @Transactional(rollbackFor = Exception.class)
    public PhotoVO savePhoto(Long familyId, Long userId, String username, FamilyPhoto photo) {
        photo.setFamilyId(familyId);
        photo.setUserId(userId);
        photo.setUsername(username);
        photo.setCreateTime(LocalDateTime.now());
        photo.setUpdateTime(LocalDateTime.now());
        photo.validateForCreate();
        familyPhotoRepository.save(photo);
        logger.info("用户 {} 在家族 {} 上传照片: id={}", userId, familyId, photo.getId());
        operationLogPublish(userId, username, "PHOTO_UPLOAD",
                "上传家族照片: " + photo.getTitle(), "photo", photo.getId(), familyId);
        return toPhotoVO(photo, userId);
    }

    /**
     * 查询家族照片列表（含人物标记）
     *
     * @param familyId      家族ID
     * @param currentUserId 当前用户ID（用于判断删除权限）
     * @return 照片列表
     */
    public List<PhotoVO> listPhotos(Long familyId, Long currentUserId) {
        List<FamilyPhoto> photos = familyPhotoRepository.findByFamilyId(familyId);
        return photos.stream()
                .map(photo -> toPhotoVO(photo, currentUserId))
                .collect(Collectors.toList());
    }

    /**
     * 删除照片（仅上传者可删除）
     *
     * @param photoId 照片ID
     * @param userId  当前用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void removePhoto(Long photoId, Long userId) {
        FamilyPhoto photo = familyPhotoRepository.findById(photoId);
        if (photo == null) {
            throw new BusinessException("照片不存在");
        }
        if (!photo.isUploader(userId)) {
            throw new BusinessException("只能删除自己上传的照片");
        }
        familyPhotoRepository.removeTagsByPhotoId(photoId);
        familyPhotoRepository.removeById(photoId);
        logger.info("用户 {} 删除照片: id={}", userId, photoId);
        operationLogPublish(userId, photo.getUsername(), "PHOTO_DELETE",
                "删除家族照片: " + photo.getTitle(), "photo", photoId, photo.getFamilyId());
    }

    /**
     * 为照片标记人物
     *
     * @param familyId 家族ID
     * @param photoId  照片ID
     * @param userId   当前用户ID
     * @param dto      标记内容（节点ID）
     * @return 标记展示对象
     */
    @Transactional(rollbackFor = Exception.class)
    public PhotoTagVO addTag(Long familyId, Long photoId, Long userId, PhotoTagDTO dto) {
        FamilyPhoto photo = familyPhotoRepository.findById(photoId);
        if (photo == null || !Objects.equals(photo.getFamilyId(), familyId)) {
            throw new BusinessException("照片不存在");
        }
        if (dto.getNodeId() == null) {
            throw new BusinessException("请选择要标记的人物");
        }
        FamilyNode node = familyNodeRepository.findById(dto.getNodeId());
        if (node == null || !Objects.equals(node.getFamilyId(), familyId)) {
            throw new BusinessException("标记的人物不存在");
        }
        if (familyPhotoRepository.existsTag(photoId, dto.getNodeId())) {
            throw new BusinessException("该人物已被标记");
        }

        PhotoTag tag = new PhotoTag();
        tag.setPhotoId(photoId);
        tag.setNodeId(node.getId());
        tag.setNodeName(node.getName());
        tag.setCreateTime(LocalDateTime.now());
        familyPhotoRepository.saveTag(tag);
        logger.info("用户 {} 为照片 {} 标记人物: nodeId={}", userId, photoId, node.getId());
        return toTagVO(tag);
    }

    /**
     * 移除照片人物标记（仅上传者可操作）
     *
     * @param photoId 照片ID
     * @param tagId   标记ID
     * @param userId  当前用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeTag(Long photoId, Long tagId, Long userId) {
        FamilyPhoto photo = familyPhotoRepository.findById(photoId);
        if (photo == null) {
            throw new BusinessException("照片不存在");
        }
        if (!photo.isUploader(userId)) {
            throw new BusinessException("只能管理自己上传的照片标记");
        }
        PhotoTag tag = familyPhotoRepository.findTagById(tagId);
        if (tag == null || !Objects.equals(tag.getPhotoId(), photoId)) {
            throw new BusinessException("标记不存在");
        }
        familyPhotoRepository.removeTagById(tagId);
        logger.info("用户 {} 移除照片 {} 的人物标记: tagId={}", userId, photoId, tagId);
    }

    private PhotoVO toPhotoVO(FamilyPhoto photo, Long currentUserId) {
        PhotoVO vo = new PhotoVO();
        vo.setId(photo.getId());
        vo.setUserId(photo.getUserId());
        vo.setUsername(photo.getUsername());
        vo.setTitle(photo.getTitle());
        vo.setDescription(photo.getDescription());
        vo.setPhotoUrl(photo.getPhotoUrl());
        vo.setCreateTime(photo.getCreateTime());
        vo.setOwn(Objects.equals(photo.getUserId(), currentUserId));
        List<PhotoTag> tags = familyPhotoRepository.findTagsByPhotoId(photo.getId());
        vo.setTags(tags.stream().map(this::toTagVO).collect(Collectors.toList()));
        return vo;
    }

    private PhotoTagVO toTagVO(PhotoTag tag) {
        PhotoTagVO vo = new PhotoTagVO();
        vo.setId(tag.getId());
        vo.setNodeId(tag.getNodeId());
        vo.setNodeName(tag.getNodeName());
        return vo;
    }

    /**
     * 发布统一操作审计事件（相册操作无请求上下文，IP 置空）
     */
    private void operationLogPublish(Long userId, String username, String operationType,
                                     String operationDesc, String targetType, Long targetId, Long familyId) {
        eventPublisher.publishEvent(OperationPerformedEvent.of(userId, username, operationType,
                operationDesc, targetType, targetId, familyId, null));
    }
}
