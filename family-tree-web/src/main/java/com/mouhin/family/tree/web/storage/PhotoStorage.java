package com.mouhin.family.tree.web.storage;

import com.mouhin.family.tree.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 家族相册文件存储。
 * <p>
 * 目录布局为 {@code {family-tree.photo.dir}/{familyId}/{userId}_{uuid}.{ext}}，按家族分目录隔离，
 * 便于容量统计与家族归档时整体清理。文件类型以内容魔数为准，不信任客户端上报的 Content-Type；
 * 文件名一律服务端生成，客户端文件名不参与路径拼接。
 *
 * @author Family-Tree
 * @date 2026-11-17
 */
@Component
public class PhotoStorage {

    private static final Logger logger = LoggerFactory.getLogger(PhotoStorage.class);

    /**
     * 照片访问地址前缀，与 PhotoController 的读取端点保持一致
     */
    private static final String URL_PREFIX = "/api/photo/";

    /**
     * 允许的图片类型（Content-Type 子类型）
     */
    private static final Set<String> ALLOWED_SUBTYPES = Set.of("jpeg", "png", "gif", "webp");

    /**
     * 魔数识别所需的头部字节数（WebP 的标识位于第 8~11 字节）
     */
    private static final int MAGIC_BYTES = 12;

    private final Path rootDir;

    public PhotoStorage(@Value("${family-tree.photo.dir:./data/photos}") String photoDir) {
        this.rootDir = Paths.get(photoDir).toAbsolutePath().normalize();
    }

    /**
     * 保存上传的图片到家族目录。
     *
     * @param familyId 家族ID（来自会话，不接受客户端传入）
     * @param userId   上传者用户ID，参与文件名生成
     * @param file     上传的图片文件
     * @return 服务端生成的文件名
     * @throws IOException 写文件失败
     */
    public String store(Long familyId, Long userId, MultipartFile file) throws IOException {
        String subType = resolveSubType(file.getContentType());
        Path dir = familyDir(familyId);
        Files.createDirectories(dir);

        String fileName = userId + "_" + UUID.randomUUID() + "." + extensionOf(subType);
        Path target = dir.resolve(fileName);
        try (InputStream in = file.getInputStream();
             OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
            byte[] head = in.readNBytes(MAGIC_BYTES);
            verifyMagic(subType, head);
            out.write(head);
            in.transferTo(out);
        } catch (IOException | RuntimeException e) {
            deleteQuietly(target);
            throw e;
        }
        return fileName;
    }

    /**
     * 拼照片访问地址，作为 family_photo.photo_url 的唯一存储形式
     *
     * @param familyId 家族ID
     * @param fileName 文件名
     * @return 访问地址
     */
    public String urlOf(Long familyId, String fileName) {
        return URL_PREFIX + familyId + "/" + fileName;
    }

    /**
     * 解析照片文件的绝对路径，越出根目录或家族目录时拒绝
     *
     * @param familyId 家族ID
     * @param fileName 文件名（来自 URL 路径段）
     * @return 文件路径
     */
    public Path resolve(Long familyId, String fileName) {
        Path dir = familyDir(familyId);
        if (fileName == null || fileName.isBlank() || fileName.indexOf('\0') >= 0) {
            throw new BusinessException("照片不存在");
        }
        Path filePath;
        try {
            filePath = dir.resolve(fileName).normalize();
        } catch (InvalidPathException e) {
            throw new BusinessException("照片不存在");
        }
        if (!filePath.startsWith(dir) || !filePath.getParent().equals(dir)) {
            throw new BusinessException("照片不存在");
        }
        return filePath;
    }

    /**
     * 按 photo_url 删除照片文件，文件缺失只告警不抛出（以数据库记录已删除为准）
     *
     * @param photoUrl 照片访问地址
     */
    public void deleteByUrl(String photoUrl) {
        Path filePath = parseUrl(photoUrl);
        if (filePath == null) {
            return;
        }
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            logger.warn("Failed to delete photo file: url={}, reason={}", photoUrl, e.getMessage());
        }
    }

    /**
     * 照片文件是否存在
     *
     * @param familyId 家族ID
     * @param fileName 文件名
     * @return 存在返回 true
     */
    public boolean exists(Long familyId, String fileName) {
        try {
            return Files.isRegularFile(resolve(familyId, fileName));
        } catch (BusinessException e) {
            return false;
        }
    }

    private Path familyDir(Long familyId) {
        if (familyId == null || familyId <= 0L) {
            throw new BusinessException("请先加入或创建家族");
        }
        return rootDir.resolve(String.valueOf(familyId));
    }

    private String resolveSubType(String contentType) {
        if (contentType == null) {
            throw new BusinessException("仅支持 JPG/PNG/GIF/WebP 格式");
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        int slash = lower.indexOf('/');
        String subType = slash < 0 ? lower : lower.substring(slash + 1);
        int param = subType.indexOf(';');
        if (param >= 0) {
            subType = subType.substring(0, param);
        }
        if (!ALLOWED_SUBTYPES.contains(subType)) {
            throw new BusinessException("仅支持 JPG/PNG/GIF/WebP 格式");
        }
        return subType;
    }

    private String extensionOf(String subType) {
        return "jpeg".equals(subType) ? "jpg" : subType;
    }

    /**
     * 以文件头魔数复核类型，防止扩展名伪装
     */
    private void verifyMagic(String subType, byte[] head) {
        boolean matched = switch (subType) {
            case "jpeg" -> startsWith(head, 0xFF, 0xD8, 0xFF);
            case "png" -> startsWith(head, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "gif" -> startsWithAscii(head, "GIF8");
            case "webp" -> startsWithAscii(head, "RIFF") && asciiAt(head, 8, "WEBP");
            default -> false;
        };
        if (!matched) {
            throw new BusinessException("图片内容与声明格式不一致");
        }
    }

    private boolean startsWith(byte[] head, int... expected) {
        if (head == null || head.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((head[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean startsWithAscii(byte[] head, String text) {
        return matchesAscii(head, 0, text);
    }

    private boolean asciiAt(byte[] head, int offset, String text) {
        return matchesAscii(head, offset, text);
    }

    private boolean matchesAscii(byte[] head, int offset, String text) {
        if (head == null || head.length < offset + text.length()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if ((char) (head[offset + i] & 0xFF) != text.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private Path parseUrl(String photoUrl) {
        if (photoUrl == null || !photoUrl.startsWith(URL_PREFIX)) {
            return null;
        }
        String relative = photoUrl.substring(URL_PREFIX.length());
        int slash = relative.indexOf('/');
        if (slash <= 0 || slash == relative.length() - 1) {
            return null;
        }
        try {
            Long familyId = Long.valueOf(relative.substring(0, slash));
            Path filePath = resolve(familyId, relative.substring(slash + 1));
            return filePath.startsWith(rootDir) ? filePath : null;
        } catch (NumberFormatException | BusinessException e) {
            logger.warn("Ignore invalid photo url on delete: {}", photoUrl);
            return null;
        }
    }

    private void deleteQuietly(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            logger.warn("Failed to clean up incomplete photo file: {}", target);
        }
    }
}
