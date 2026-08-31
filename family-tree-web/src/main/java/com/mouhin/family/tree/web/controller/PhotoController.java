package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.PhotoApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.PhotoTagDTO;
import com.mouhin.family.tree.common.dto.PhotoTagVO;
import com.mouhin.family.tree.common.dto.PhotoVO;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.domain.entity.FamilyPhoto;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * 家族相册控制器
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@RestController
@RequestMapping("/api/photo")
public class PhotoController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(PhotoController.class);

    /**
     * 允许的图片类型
     */
    private static final String[] ALLOWED_TYPES = {"image/jpeg", "image/png", "image/gif", "image/webp"};

    /**
     * 单张图片最大 5MB
     */
    private static final long MAX_SIZE = 5L * 1024 * 1024;

    /**
     * 照片标题最大长度
     */
    private static final int MAX_TITLE_LENGTH = 100;

    private final PhotoApplicationService photoApplicationService;

    @Value("${family-tree.photo.dir:./data/photos}")
    private String photoDir;

    public PhotoController(PhotoApplicationService photoApplicationService) {
        this.photoApplicationService = photoApplicationService;
    }

    /**
     * 查询家族相册列表
     */
    @GetMapping
    public Result<List<PhotoVO>> listPhotos(HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        return Result.success(photoApplicationService.listPhotos(familyId, userId));
    }

    /**
     * 上传照片（先存文件，再落库）
     *
     * @param file        图片文件
     * @param title       照片标题（可选）
     * @param description 照片描述（可选）
     */
    @PostMapping("/upload")
    public Result<PhotoVO> upload(@RequestParam("file") MultipartFile file,
                                  @RequestParam(value = "title", required = false) String title,
                                  @RequestParam(value = "description", required = false) String description,
                                  HttpSession session) {
        Long userId = getCurrentUserId(session);
        Long familyId = getCurrentFamilyId(session);
        String username = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);

        if (file.isEmpty()) {
            return Result.fail(400, "请选择图片文件");
        }
        if (file.getSize() > MAX_SIZE) {
            return Result.fail(400, "图片大小不能超过5MB");
        }
        String contentType = file.getContentType();
        boolean typeOk = false;
        for (String allowed : ALLOWED_TYPES) {
            if (allowed.equals(contentType)) {
                typeOk = true;
                break;
            }
        }
        if (!typeOk) {
            return Result.fail(400, "仅支持 JPG/PNG/GIF/WebP 格式");
        }
        if (title != null && title.length() > MAX_TITLE_LENGTH) {
            return Result.fail(400, "标题长度不能超过" + MAX_TITLE_LENGTH + "个字符");
        }

        try {
            Path dirPath = Paths.get(photoDir);
            Files.createDirectories(dirPath);
            String ext = contentType.substring(contentType.lastIndexOf('/') + 1);
            String fileName = userId + "_" + UUID.randomUUID() + "." + ext;
            file.transferTo(dirPath.resolve(fileName).toFile());
            String url = "/api/photo/" + fileName;

            FamilyPhoto photo = new FamilyPhoto();
            photo.setTitle(title != null ? title.trim() : null);
            photo.setDescription(description != null ? description.trim() : null);
            photo.setPhotoUrl(url);
            PhotoVO vo = photoApplicationService.savePhoto(familyId, userId, username, photo);
            return Result.success(vo);
        } catch (IOException e) {
            logger.error("Failed to upload photo for user={}: {}", userId, e.getMessage(), e);
            return Result.fail(500, "上传失败");
        }
    }

    /**
     * 删除照片（仅上传者）
     */
    @DeleteMapping("/{id}")
    public Result<Void> removePhoto(@PathVariable Long id, HttpSession session) {
        photoApplicationService.removePhoto(id, getCurrentUserId(session));
        return Result.success();
    }

    /**
     * 为照片标记人物
     */
    @PostMapping("/{photoId}/tags")
    public Result<PhotoTagVO> addTag(@PathVariable Long photoId,
                                     @RequestBody PhotoTagDTO dto,
                                     HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        return Result.success(photoApplicationService.addTag(familyId, photoId, userId, dto));
    }

    /**
     * 移除照片人物标记
     */
    @DeleteMapping("/{photoId}/tags/{tagId}")
    public Result<Void> removeTag(@PathVariable Long photoId,
                                  @PathVariable Long tagId,
                                  HttpSession session) {
        photoApplicationService.removeTag(photoId, tagId, getCurrentUserId(session));
        return Result.success();
    }

    /**
     * 获取照片文件
     *
     * @param fileName 文件名
     */
    @GetMapping("/{fileName:.+}")
    public ResponseEntity<?> getPhoto(@PathVariable String fileName) {
        try {
            Path dirPath = Paths.get(photoDir).normalize();
            Path filePath = dirPath.resolve(fileName);
            // 防止路径遍历
            if (!filePath.normalize().startsWith(dirPath)) {
                return ResponseEntity.badRequest().body("Invalid path");
            }
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }
            byte[] bytes = Files.readAllBytes(filePath);
            String ext = fileName.substring(fileName.lastIndexOf('.') + 1);
            String contentType = switch (ext.toLowerCase()) {
                case "jpg", "jpeg" -> "image/jpeg";
                case "png" -> "image/png";
                case "gif" -> "image/gif";
                case "webp" -> "image/webp";
                default -> "application/octet-stream";
            };
            return ResponseEntity.ok()
                    .header("Content-Type", contentType)
                    .header("Cache-Control", "max-age=86400")
                    .body(bytes);
        } catch (IOException e) {
            logger.error("Failed to read photo file={}: {}", fileName, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
