package com.mouhin.family.tree.web.storage;

import com.mouhin.family.tree.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 相册文件存储单元测试：覆盖家族目录布局、服务端文件名生成、Content-Type 与魔数双重校验、
 * 路径遍历与跨家族越界拒绝，以及按 photo_url 删除的容错语义。
 *
 * @author Family-Tree
 * @date 2026-11-17
 */
class PhotoStorageTest {

    private static final long FAMILY_ID = 7L;
    private static final long OTHER_FAMILY_ID = 8L;
    private static final long USER_ID = 100L;

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final String UUID_PATTERN = "100_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @TempDir
    Path tempDir;

    private PhotoStorage photoStorage;

    @BeforeEach
    void setUp() {
        photoStorage = new PhotoStorage(tempDir.toString());
    }

    // ========== 上传落盘 ==========

    @Test
    void store_writesIntoFamilyDirWithGeneratedName() throws IOException {
        String fileName = photoStorage.store(FAMILY_ID, USER_ID, image("image/jpeg", jpegBytes()));

        assertTrue(fileName.matches(UUID_PATTERN + "\\.jpg"), "文件名应为 {userId}_{uuid}.jpg，实际：" + fileName);
        Path saved = tempDir.resolve(FAMILY_ID + "/" + fileName);
        assertTrue(Files.isRegularFile(saved));
        assertStartsWith(JPEG_MAGIC, Files.readAllBytes(saved));
    }

    @Test
    void store_keepsFamiliesInSeparateDirs() throws IOException {
        String mine = photoStorage.store(FAMILY_ID, USER_ID, image("image/png", pngBytes()));
        String others = photoStorage.store(OTHER_FAMILY_ID, USER_ID, image("image/png", pngBytes()));

        assertTrue(Files.isRegularFile(tempDir.resolve(FAMILY_ID + "/" + mine)));
        assertTrue(Files.isRegularFile(tempDir.resolve(OTHER_FAMILY_ID + "/" + others)));
    }

    @Test
    void store_ignoresClientFileNameAndKeepsFullPayload() throws IOException {
        byte[] payload = pngBytes();
        String fileName = photoStorage.store(FAMILY_ID, USER_ID,
                new MockMultipartFile("file", "../../etc/passwd.jpg", "image/png", payload));

        assertTrue(fileName.endsWith(".png"));
        assertEquals(payload.length, Files.size(tempDir.resolve(FAMILY_ID + "/" + fileName)));
    }

    @Test
    void store_rejectsNonImageContentType() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                photoStorage.store(FAMILY_ID, USER_ID, image("text/plain", jpegBytes())));

        assertEquals("仅支持 JPG/PNG/GIF/WebP 格式", ex.getMessage());
    }

    @Test
    void store_rejectsMissingContentType() {
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", null, jpegBytes());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                photoStorage.store(FAMILY_ID, USER_ID, file));
        assertEquals("仅支持 JPG/PNG/GIF/WebP 格式", ex.getMessage());
    }

    @Test
    void store_rejectsContentTypeSpoofedOverOtherMagic() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                photoStorage.store(FAMILY_ID, USER_ID, image("image/png", jpegBytes())));

        assertEquals("图片内容与声明格式不一致", ex.getMessage());
    }

    @Test
    void store_rejectsFakeWebpWithoutRiffHeader() {
        assertThrows(BusinessException.class, () ->
                photoStorage.store(FAMILY_ID, USER_ID, image("image/webp", pngBytes())));
    }

    @Test
    void store_rejectsRealWebpMissingSecondMarker() {
        byte[] head = "RIFF----XXXX".getBytes(StandardCharsets.US_ASCII);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                photoStorage.store(FAMILY_ID, USER_ID, image("image/webp", head)));
        assertEquals("图片内容与声明格式不一致", ex.getMessage());
    }

    @Test
    void store_acceptsRealWebp() throws IOException {
        byte[] webp = ("RIFF----WEBPVP8 ").getBytes(StandardCharsets.US_ASCII);

        String fileName = photoStorage.store(FAMILY_ID, USER_ID, image("image/webp", webp));

        assertTrue(fileName.endsWith(".webp"));
        assertEquals(webp.length, Files.size(tempDir.resolve(FAMILY_ID + "/" + fileName)));
    }

    @Test
    void store_doesNotLeaveIncompleteFile() throws IOException {
        Path familyDir = tempDir.resolve(String.valueOf(FAMILY_ID));

        assertThrows(BusinessException.class, () ->
                photoStorage.store(FAMILY_ID, USER_ID, image("image/gif", jpegBytes())));

        assertFalse(hasRegularFile(familyDir));
    }

    @Test
    void store_rejectsMissingFamilyId() {
        assertThrows(BusinessException.class, () ->
                photoStorage.store(null, USER_ID, image("image/jpeg", jpegBytes())));
        assertThrows(BusinessException.class, () ->
                photoStorage.store(0L, USER_ID, image("image/jpeg", jpegBytes())));
    }

    // ========== 访问地址与寻址 ==========

    @Test
    void urlOf_usesFamilyScopedPath() {
        assertEquals("/api/photo/7/100_x.jpg", photoStorage.urlOf(FAMILY_ID, "100_x.jpg"));
    }

    @Test
    void resolve_staysInsideFamilyDir() throws IOException {
        String fileName = photoStorage.store(FAMILY_ID, USER_ID, image("image/jpeg", jpegBytes()));

        assertEquals(tempDir.resolve(FAMILY_ID + "/" + fileName), photoStorage.resolve(FAMILY_ID, fileName));
        assertTrue(photoStorage.exists(FAMILY_ID, fileName));
    }

    @Test
    void resolve_rejectsTraversalAndCrossFamilyPaths() {
        List<String> hostile = List.of("../8/100_x.jpg", "sub/100_x.jpg", "/etc/passwd", "100_x.jpg/../../9");

        for (String fileName : hostile) {
            assertThrows(BusinessException.class, () -> photoStorage.resolve(FAMILY_ID, fileName),
                    "应拒绝：" + fileName);
            assertFalse(photoStorage.exists(FAMILY_ID, fileName), "exists 应同样拒绝：" + fileName);
        }
    }

    @Test
    void resolve_rejectsBlankAndNulByteNames() {
        assertThrows(BusinessException.class, () -> photoStorage.resolve(FAMILY_ID, "  "));
        assertThrows(BusinessException.class, () -> photoStorage.resolve(FAMILY_ID, "100_x\u0000.jpg"));
        assertThrows(BusinessException.class, () -> photoStorage.resolve(FAMILY_ID, null));
    }

    // ========== 按地址删除 ==========

    @Test
    void deleteByUrl_removesFamilyScopedFile() throws IOException {
        String fileName = photoStorage.store(FAMILY_ID, USER_ID, image("image/jpeg", jpegBytes()));
        Path saved = tempDir.resolve(FAMILY_ID + "/" + fileName);

        photoStorage.deleteByUrl(photoStorage.urlOf(FAMILY_ID, fileName));

        assertFalse(Files.exists(saved));
    }

    @Test
    void deleteByUrl_ignoresLegacyAndIllegalUrls() throws IOException {
        String fileName = photoStorage.store(FAMILY_ID, USER_ID, image("image/jpeg", jpegBytes()));
        Path saved = tempDir.resolve(FAMILY_ID + "/" + fileName);

        // 旧版平铺地址（无家族目录段）与非法地址一律跳过，交由上层以数据库记录已删除为准
        for (String legacy : List.of("/api/photo/" + fileName, "/api/photo/", "/api/photos/1.jpg",
                "/api/photo/abc/1.jpg", "/api/photo/7/../8/" + fileName)) {
            photoStorage.deleteByUrl(legacy);
        }
        photoStorage.deleteByUrl(null);

        assertTrue(Files.exists(saved));
    }

    @Test
    void deleteByUrl_missingFileDoesNotThrow() {
        photoStorage.deleteByUrl("/api/photo/7/100_not_exists.jpg");
    }

    // ========== 辅助 ==========

    private MockMultipartFile image(String contentType, byte[] content) {
        return new MockMultipartFile("file", "photo.jpg", contentType, content);
    }

    private byte[] jpegBytes() {
        return concat(JPEG_MAGIC, "fake-jpeg-payload".getBytes(StandardCharsets.UTF_8));
    }

    private byte[] pngBytes() {
        return concat(PNG_MAGIC, "fake-png-payload".getBytes(StandardCharsets.UTF_8));
    }

    private byte[] concat(byte[] head, byte[] tail) {
        byte[] all = Arrays.copyOf(head, head.length + tail.length);
        System.arraycopy(tail, 0, all, head.length, tail.length);
        return all;
    }

    private void assertStartsWith(byte[] expected, byte[] actual) {
        assertTrue(actual.length >= expected.length);
        assertEquals(Arrays.toString(expected), Arrays.toString(Arrays.copyOf(actual, expected.length)));
    }

    private boolean hasRegularFile(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.anyMatch(Files::isRegularFile);
        }
    }
}
