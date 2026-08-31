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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 家族相册应用服务单元测试。
 * 覆盖：保存照片（正常/标题为空）、列表查询（含标记与own标记）、
 * 删除照片（正常/非上传者/不存在）、人物标记（正常/节点不存在/重复标记/空节点）、
 * 移除标记（正常/非上传者/标记不存在）。
 *
 * @author Family-Tree
 * @date 2026-08-31
 */
@ExtendWith(MockitoExtension.class)
class PhotoApplicationServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long FAMILY_ID = 100L;
    private static final Long PHOTO_ID = 10L;
    private static final Long NODE_ID = 30L;

    @Mock
    private FamilyPhotoRepository familyPhotoRepository;

    @Mock
    private FamilyNodeRepository familyNodeRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PhotoApplicationService photoApplicationService;

    // ========== 保存照片 ==========

    @Test
    void savePhoto_success() {
        FamilyPhoto photo = buildPhoto(null, USER_ID, "全家福", "/api/photos/1.jpg");
        when(familyPhotoRepository.save(any(FamilyPhoto.class))).thenAnswer(invocation -> {
            FamilyPhoto arg = invocation.getArgument(0);
            arg.setId(PHOTO_ID);
            return arg;
        });
        when(familyPhotoRepository.findTagsByPhotoId(PHOTO_ID)).thenReturn(List.of());

        PhotoVO vo = photoApplicationService.savePhoto(FAMILY_ID, USER_ID, "测试用户", photo);

        assertEquals(PHOTO_ID, vo.getId());
        assertEquals(FAMILY_ID, photo.getFamilyId());
        assertEquals(USER_ID, photo.getUserId());
        assertEquals("测试用户", photo.getUsername());
        assertNotNull(photo.getCreateTime());
        assertTrue(vo.getOwn());

        ArgumentCaptor<OperationPerformedEvent> eventCaptor =
                ArgumentCaptor.forClass(OperationPerformedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        OperationPerformedEvent event = eventCaptor.getValue();
        assertEquals("PHOTO_UPLOAD", event.operationType());
        assertEquals(USER_ID, event.userId());
        assertEquals("photo", event.targetType());
        assertEquals(PHOTO_ID, event.targetId());
        assertEquals(FAMILY_ID, event.familyId());
    }

    @Test
    void savePhoto_blankTitle_throws() {
        FamilyPhoto photo = buildPhoto(null, USER_ID, "  ", "/api/photos/1.jpg");

        assertThrows(BusinessException.class, () ->
                photoApplicationService.savePhoto(FAMILY_ID, USER_ID, "测试用户", photo));
        verify(familyPhotoRepository, never()).save(any(FamilyPhoto.class));
    }

    // ========== 列表查询 ==========

    @Test
    void listPhotos_withTags_ownFlag() {
        FamilyPhoto mine = buildPhoto(1L, USER_ID, "我的照片", "/api/photos/1.jpg");
        FamilyPhoto others = buildPhoto(2L, OTHER_USER_ID, "别人的照片", "/api/photos/2.jpg");
        when(familyPhotoRepository.findByFamilyId(FAMILY_ID)).thenReturn(List.of(mine, others));

        PhotoTag tag = buildTag(90L, 1L, NODE_ID, "张三");
        when(familyPhotoRepository.findTagsByPhotoId(1L)).thenReturn(List.of(tag));
        when(familyPhotoRepository.findTagsByPhotoId(2L)).thenReturn(List.of());

        List<PhotoVO> result = photoApplicationService.listPhotos(FAMILY_ID, USER_ID);

        assertEquals(2, result.size());
        assertTrue(result.get(0).getOwn());
        assertFalse(result.get(1).getOwn());
        assertEquals(1, result.get(0).getTags().size());
        assertEquals("张三", result.get(0).getTags().get(0).getNodeName());
        assertTrue(result.get(1).getTags().isEmpty());
    }

    // ========== 删除照片 ==========

    @Test
    void removePhoto_success() {
        FamilyPhoto photo = buildPhoto(PHOTO_ID, USER_ID, "全家福", "/api/photos/1.jpg");
        when(familyPhotoRepository.findById(PHOTO_ID)).thenReturn(photo);

        photoApplicationService.removePhoto(PHOTO_ID, USER_ID);

        verify(familyPhotoRepository).removeTagsByPhotoId(PHOTO_ID);
        verify(familyPhotoRepository).removeById(PHOTO_ID);
    }

    @Test
    void removePhoto_notUploader_throws() {
        FamilyPhoto photo = buildPhoto(PHOTO_ID, OTHER_USER_ID, "别人的照片", "/api/photos/1.jpg");
        when(familyPhotoRepository.findById(PHOTO_ID)).thenReturn(photo);

        assertThrows(BusinessException.class, () ->
                photoApplicationService.removePhoto(PHOTO_ID, USER_ID));
        verify(familyPhotoRepository, never()).removeById(any());
    }

    @Test
    void removePhoto_notFound_throws() {
        when(familyPhotoRepository.findById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () ->
                photoApplicationService.removePhoto(999L, USER_ID));
        verify(familyPhotoRepository, never()).removeById(any());
    }

    // ========== 人物标记 ==========

    @Test
    void addTag_success() {
        FamilyPhoto photo = buildPhoto(PHOTO_ID, USER_ID, "全家福", "/api/photos/1.jpg");
        when(familyPhotoRepository.findById(PHOTO_ID)).thenReturn(photo);

        FamilyNode node = buildNode(NODE_ID, FAMILY_ID, "张三");
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node);
        when(familyPhotoRepository.existsTag(PHOTO_ID, NODE_ID)).thenReturn(false);

        PhotoTagDTO dto = new PhotoTagDTO();
        dto.setNodeId(NODE_ID);

        PhotoTagVO vo = photoApplicationService.addTag(FAMILY_ID, PHOTO_ID, USER_ID, dto);

        ArgumentCaptor<PhotoTag> captor = ArgumentCaptor.forClass(PhotoTag.class);
        verify(familyPhotoRepository).saveTag(captor.capture());
        assertEquals(PHOTO_ID, captor.getValue().getPhotoId());
        assertEquals(NODE_ID, captor.getValue().getNodeId());
        assertEquals("张三", vo.getNodeName());
    }

    @Test
    void addTag_nullNodeId_throws() {
        FamilyPhoto photo = buildPhoto(PHOTO_ID, USER_ID, "全家福", "/api/photos/1.jpg");
        when(familyPhotoRepository.findById(PHOTO_ID)).thenReturn(photo);

        PhotoTagDTO dto = new PhotoTagDTO();

        assertThrows(BusinessException.class, () ->
                photoApplicationService.addTag(FAMILY_ID, PHOTO_ID, USER_ID, dto));
        verify(familyPhotoRepository, never()).saveTag(any(PhotoTag.class));
    }

    @Test
    void addTag_nodeNotFound_throws() {
        FamilyPhoto photo = buildPhoto(PHOTO_ID, USER_ID, "全家福", "/api/photos/1.jpg");
        when(familyPhotoRepository.findById(PHOTO_ID)).thenReturn(photo);
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(null);

        PhotoTagDTO dto = new PhotoTagDTO();
        dto.setNodeId(NODE_ID);

        assertThrows(BusinessException.class, () ->
                photoApplicationService.addTag(FAMILY_ID, PHOTO_ID, USER_ID, dto));
        verify(familyPhotoRepository, never()).saveTag(any(PhotoTag.class));
    }

    @Test
    void addTag_duplicate_throws() {
        FamilyPhoto photo = buildPhoto(PHOTO_ID, USER_ID, "全家福", "/api/photos/1.jpg");
        when(familyPhotoRepository.findById(PHOTO_ID)).thenReturn(photo);

        FamilyNode node = buildNode(NODE_ID, FAMILY_ID, "张三");
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node);
        when(familyPhotoRepository.existsTag(PHOTO_ID, NODE_ID)).thenReturn(true);

        PhotoTagDTO dto = new PhotoTagDTO();
        dto.setNodeId(NODE_ID);

        assertThrows(BusinessException.class, () ->
                photoApplicationService.addTag(FAMILY_ID, PHOTO_ID, USER_ID, dto));
        verify(familyPhotoRepository, never()).saveTag(any(PhotoTag.class));
    }

    // ========== 移除标记 ==========

    @Test
    void removeTag_success() {
        FamilyPhoto photo = buildPhoto(PHOTO_ID, USER_ID, "全家福", "/api/photos/1.jpg");
        when(familyPhotoRepository.findById(PHOTO_ID)).thenReturn(photo);

        PhotoTag tag = buildTag(90L, PHOTO_ID, NODE_ID, "张三");
        when(familyPhotoRepository.findTagById(90L)).thenReturn(tag);

        photoApplicationService.removeTag(PHOTO_ID, 90L, USER_ID);

        verify(familyPhotoRepository).removeTagById(90L);
    }

    @Test
    void removeTag_notUploader_throws() {
        FamilyPhoto photo = buildPhoto(PHOTO_ID, OTHER_USER_ID, "别人的照片", "/api/photos/1.jpg");
        when(familyPhotoRepository.findById(PHOTO_ID)).thenReturn(photo);

        assertThrows(BusinessException.class, () ->
                photoApplicationService.removeTag(PHOTO_ID, 90L, USER_ID));
        verify(familyPhotoRepository, never()).removeTagById(any());
    }

    @Test
    void removeTag_tagNotFound_throws() {
        FamilyPhoto photo = buildPhoto(PHOTO_ID, USER_ID, "全家福", "/api/photos/1.jpg");
        when(familyPhotoRepository.findById(PHOTO_ID)).thenReturn(photo);
        when(familyPhotoRepository.findTagById(90L)).thenReturn(null);

        assertThrows(BusinessException.class, () ->
                photoApplicationService.removeTag(PHOTO_ID, 90L, USER_ID));
        verify(familyPhotoRepository, never()).removeTagById(any());
    }

    // ========== 辅助方法 ==========

    private FamilyPhoto buildPhoto(Long id, Long userId, String title, String photoUrl) {
        FamilyPhoto photo = new FamilyPhoto();
        photo.setId(id);
        photo.setFamilyId(FAMILY_ID);
        photo.setUserId(userId);
        photo.setUsername("测试用户");
        photo.setTitle(title);
        photo.setPhotoUrl(photoUrl);
        return photo;
    }

    private PhotoTag buildTag(Long id, Long photoId, Long nodeId, String nodeName) {
        PhotoTag tag = new PhotoTag();
        tag.setId(id);
        tag.setPhotoId(photoId);
        tag.setNodeId(nodeId);
        tag.setNodeName(nodeName);
        return tag;
    }

    private FamilyNode buildNode(Long id, Long familyId, String name) {
        FamilyNode node = new FamilyNode();
        node.setId(id);
        node.setFamilyId(familyId);
        node.setName(name);
        return node;
    }
}
