package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.PhotoApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.PhotoTagDTO;
import com.mouhin.family.tree.common.dto.PhotoTagVO;
import com.mouhin.family.tree.common.dto.PhotoVO;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.domain.entity.FamilyPhoto;
import com.mouhin.family.tree.web.storage.PhotoStorage;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
     * 单张图片最大 5MB
     */
    private static final long MAX_SIZE = 5L * 1024 * 1024;

    /**
     * 照片标题最大长度
     */
    private static final int MAX_TITLE_LENGTH = 100;

    private final PhotoApplicationService photoApplicationService;

    private final PhotoStorage photoStorage;

    public PhotoController(PhotoApplicationService photoApplicationService, PhotoStorage photoStorage) {
        this.photoApplicationService = photoApplicationService;
        this.photoStorage = photoStorage;
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
        if (title != null && title.length() > MAX_TITLE_LENGTH) {
            return Result.fail(400, "标题长度不能超过" + MAX_TITLE_LENGTH + "个字符");
        }

        try {
            // 类型与魔数校验、家族子目录落盘都在 PhotoStorage 内完成，familyId 只取自会话
            String fileName = photoStorage.store(familyId, userId, file);

            FamilyPhoto photo = new FamilyPhoto();
            photo.setTitle(title != null ? title.trim() : null);
            photo.setDescription(description != null ? description.trim() : null);
            photo.setPhotoUrl(photoStorage.urlOf(familyId, fileName));
            PhotoVO vo = photoApplicationService.savePhoto(familyId, userId, username, photo);
            return Result.success(vo);
        } catch (IOException e) {
            logger.error("Failed to upload photo for user={}: {}", userId, e.getMessage(), e);
            return Result.fail(500, "上传失败");
        }
    }

    /**
     * 删除照片（仅上传者）：先删记录，再清理家族目录下的文件
     */
    @DeleteMapping("/{id}")
    public Result<Void> removePhoto(@PathVariable Long id, HttpSession session) {
        PhotoVO removed = photoApplicationService.removePhoto(id, getCurrentUserId(session));
        photoStorage.deleteByUrl(removed.getPhotoUrl());
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
     * 获取照片文件（按家族目录寻址）
     *
     * @param familyId 家族ID（目录名）
     * @param fileName 文件名
     */
    @GetMapping("/{familyId}/{fileName:.+}")
    public ResponseEntity<?> getPhoto(@PathVariable Long familyId, @PathVariable String fileName) {
        Path filePath;
        try {
            filePath = photoStorage.resolve(familyId, fileName);
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().build();
        }
        if (!Files.isRegularFile(filePath)) {
            return ResponseEntity.notFound().build();
        }
        try {
            String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
            String contentType = switch (ext) {
                case "jpg", "jpeg" -> "image/jpeg";
                case "png" -> "image/png";
                case "gif" -> "image/gif";
                case "webp" -> "image/webp";
                default -> "application/octet-stream";
            };
            return ResponseEntity.ok()
                    .header("Content-Type", contentType)
                    .header("Cache-Control", "max-age=86400")
                    .body(Files.readAllBytes(filePath));
        } catch (IOException e) {
            logger.error("Failed to read photo file={}: {}", fileName, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
