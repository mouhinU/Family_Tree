package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.result.Result;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 头像上传控制器
 *
 * @author Family-Tree
 * @date 2026-08-04
 */
@RestController
@RequestMapping("/api/avatar")
public class AvatarController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(AvatarController.class);

    /**
     * 允许的文件类型
     */
    private static final String[] ALLOWED_TYPES = {"image/jpeg", "image/png", "image/gif", "image/webp"};

    /**
     * 最大文件大小：2MB
     */
    private static final long MAX_SIZE = 2 * 1024 * 1024;

    @Value("${family-tree.avatar.dir:./data/avatars}")
    private String avatarDir;

    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                              HttpSession session) {
        Long userId = (Long) session.getAttribute(FamilyTreeConsts.SESSION_USER_ID);
        if (userId == null) {
            return Result.fail(401, "请先登录");
        }

        // 校验文件
        if (file.isEmpty()) {
            return Result.fail(400, "请选择文件");
        }
        if (file.getSize() > MAX_SIZE) {
            return Result.fail(400, "文件大小不能超过2MB");
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

        try {
            // 确保目录存在
            Path dirPath = Paths.get(avatarDir);
            Files.createDirectories(dirPath);

            // 生成文件名
            String ext = contentType.substring(contentType.lastIndexOf('/') + 1);
            String fileName = userId + "_" + UUID.randomUUID() + "." + ext;
            Path filePath = dirPath.resolve(fileName);
            file.transferTo(filePath.toFile());

            String url = "/api/avatar/" + fileName;
            logger.info("Uploaded avatar for user={}: {}", userId, url);

            Map<String, Object> data = new HashMap<>(4);
            data.put("url", url);
            data.put("fileName", fileName);
            return Result.success(data);
        } catch (IOException e) {
            logger.error("Failed to upload avatar for user={}: {}", userId, e.getMessage(), e);
            return Result.fail(500, "上传失败");
        }
    }

    /**
     * 获取头像文件
     *
     * @param fileName 文件名
     */
    @GetMapping("/{fileName:.+}")
    public ResponseEntity<?> getAvatar(@PathVariable String fileName) {
        try {
            Path filePath = Paths.get(avatarDir).resolve(fileName);
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }
            // 防止路径遍历
            if (!filePath.normalize().startsWith(Paths.get(avatarDir).normalize())) {
                return ResponseEntity.badRequest().body("Invalid path");
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
            logger.error("Failed to read avatar file={}: {}", fileName, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
