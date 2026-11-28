package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.PhotoApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.PhotoVO;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.domain.entity.FamilyPhoto;
import com.mouhin.family.tree.web.controller.user.PhotoController;
import com.mouhin.family.tree.web.storage.PhotoStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 相册控制器单元测试：覆盖按会话家族寻址、跨家族读取的失败关闭、上传地址与落盘目录一致性，
 * 以及删除时按 photo_url 清理家族目录文件。
 *
 * @author Family-Tree
 * @date 2026-11-17
 */
@ExtendWith(MockitoExtension.class)
class PhotoControllerTest {

    private static final long FAMILY_ID = 7L;
    private static final long OTHER_FAMILY_ID = 8L;
    private static final long USER_ID = 100L;
    private static final long PHOTO_ID = 55L;

    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4};

    @TempDir
    Path tempDir;

    @Mock
    private PhotoApplicationService photoApplicationService;

    private PhotoStorage photoStorage;
    private PhotoController photoController;

    @BeforeEach
    void setUp() {
        photoStorage = new PhotoStorage(tempDir.toString());
        photoController = new PhotoController(photoApplicationService, photoStorage);
    }

    // ========== 照片读取鉴权 ==========

    @Test
    void getPhoto_sameFamily_returnsBytes() throws IOException {
        String fileName = photoStorage.store(FAMILY_ID, USER_ID, image());

        ResponseEntity<?> response = photoController.getPhoto(FAMILY_ID, fileName, session(FAMILY_ID));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("image/jpeg", response.getHeaders().getContentType().toString());
        assertEquals(JPEG_BYTES.length, ((byte[]) response.getBody()).length);
    }

    @Test
    void getPhoto_otherFamily_forbidden() throws IOException {
        String fileName = photoStorage.store(FAMILY_ID, USER_ID, image());

        ResponseEntity<?> response = photoController.getPhoto(FAMILY_ID, fileName, session(OTHER_FAMILY_ID));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void getPhoto_withoutFamilyInSession_forbidden() throws IOException {
        String fileName = photoStorage.store(FAMILY_ID, USER_ID, image());

        ResponseEntity<?> response = photoController.getPhoto(FAMILY_ID, fileName, new MockHttpSession());

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void getPhoto_crossFamilyFileName_badRequest() {
        ResponseEntity<?> response = photoController.getPhoto(FAMILY_ID,
                "../" + OTHER_FAMILY_ID + "/100_x.jpg", session(FAMILY_ID));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void getPhoto_missingFile_notFound() {
        ResponseEntity<?> response = photoController.getPhoto(FAMILY_ID, "100_not_exists.jpg", session(FAMILY_ID));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // ========== 上传 ==========

    @Test
    void upload_usesSessionFamilyForUrlAndPersistsScopedAddress() throws IOException {
        when(photoApplicationService.savePhoto(eq(FAMILY_ID), eq(USER_ID), eq("tester"), any(FamilyPhoto.class)))
                .thenReturn(new PhotoVO());

        Result<PhotoVO> result = photoController.upload(image(), " 全家福 ", null, session(FAMILY_ID));

        assertEquals(200, result.getCode());
        ArgumentCaptor<FamilyPhoto> captor = ArgumentCaptor.forClass(FamilyPhoto.class);
        verify(photoApplicationService).savePhoto(eq(FAMILY_ID), eq(USER_ID), eq("tester"), captor.capture());
        FamilyPhoto saved = captor.getValue();
        assertEquals("全家福", saved.getTitle());
        assertNotNull(saved.getPhotoUrl());
        assertTrueSavedFile(saved.getPhotoUrl());
    }

    @Test
    void upload_emptyFile_rejected() throws IOException {
        MockMultipartFile empty = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[0]);

        Result<PhotoVO> result = photoController.upload(empty, null, null, session(FAMILY_ID));

        assertEquals(400, result.getCode());
        verify(photoApplicationService, never()).savePhoto(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void upload_oversizedFile_rejected() {
        byte[] payload = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile oversized = new MockMultipartFile("file", "a.jpg", "image/jpeg", payload);

        Result<PhotoVO> result = photoController.upload(oversized, null, null, session(FAMILY_ID));

        assertEquals(400, result.getCode());
        verify(photoApplicationService, never()).savePhoto(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void upload_nonImageContentType_rejectedByStorage() {
        MockMultipartFile text = new MockMultipartFile("file", "a.txt", "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8));

        // 类型不符由 PhotoStorage 抛业务异常，交全局处理器统一转为 400 与可读文案
        BusinessException ex = assertThrows(BusinessException.class,
                () -> photoController.upload(text, null, null, session(FAMILY_ID)));
        assertEquals("仅支持 JPG/PNG/GIF/WebP 格式", ex.getMessage());
        verify(photoApplicationService, never()).savePhoto(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void upload_oversizedTitle_rejected() {
        String title = "长".repeat(101);

        Result<PhotoVO> result = photoController.upload(image(), title, null, session(FAMILY_ID));

        assertEquals(400, result.getCode());
        verify(photoApplicationService, never()).savePhoto(anyLong(), anyLong(), anyString(), any());
    }

    // ========== 删除 ==========

    @Test
    void removePhoto_cleansScopedFile() throws IOException {
        String fileName = photoStorage.store(FAMILY_ID, USER_ID, image());
        Path saved = tempDir.resolve(FAMILY_ID + "/" + fileName);
        PhotoVO removed = new PhotoVO();
        removed.setPhotoUrl(photoStorage.urlOf(FAMILY_ID, fileName));
        when(photoApplicationService.removePhoto(PHOTO_ID, USER_ID)).thenReturn(removed);

        Result<Void> result = photoController.removePhoto(PHOTO_ID, session(FAMILY_ID));

        assertEquals(200, result.getCode());
        assertFalse(Files.exists(saved));
    }

    // ========== 辅助 ==========

    private MockHttpSession session(Long familyId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(FamilyTreeConsts.SESSION_USER_ID, USER_ID);
        session.setAttribute(FamilyTreeConsts.SESSION_USERNAME, "tester");
        if (familyId != null) {
            session.setAttribute(FamilyTreeConsts.SESSION_FAMILY_ID, familyId);
        }
        return session;
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", JPEG_BYTES);
    }

    private void assertTrueSavedFile(String photoUrl) {
        String prefix = "/api/photo/" + FAMILY_ID + "/";
        assertTrue(photoUrl.startsWith(prefix), "photo_url 应为家族作用域地址，实际：" + photoUrl);
        Path filePath = photoStorage.resolve(FAMILY_ID, photoUrl.substring(prefix.length()));
        assertTrue(Files.isRegularFile(filePath), "落盘文件应存在：" + filePath);
    }
}
