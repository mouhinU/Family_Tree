package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.ForumApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.ForumReplyVO;
import com.mouhin.family.tree.common.dto.ForumTopicDTO;
import com.mouhin.family.tree.common.dto.ForumTopicVO;
import com.mouhin.family.tree.common.dto.PageResult;
import com.mouhin.family.tree.common.result.Result;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
 * 家族论坛控制器
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@RestController
@RequestMapping("/api/forum")
public class ForumController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(ForumController.class);

    /**
     * 允许的图片类型（论坛富文本配图）
     */
    private static final String[] ALLOWED_TYPES = {"image/jpeg", "image/png", "image/gif", "image/webp"};

    /**
     * 配图最大 5MB
     */
    private static final long MAX_SIZE = 5L * 1024 * 1024;

    private final ForumApplicationService forumApplicationService;

    @Value("${family-tree.photo.dir:./data/photos}")
    private String photoDir;

    public ForumController(ForumApplicationService forumApplicationService) {
        this.forumApplicationService = forumApplicationService;
    }

    /**
     * 分页查询主题列表
     *
     * @param page 页码（默认 1）
     * @param size 每页大小（默认 20）
     */
    @GetMapping
    public Result<PageResult<ForumTopicVO>> listTopics(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size,
                                                       HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        return Result.success(forumApplicationService.listTopics(familyId, userId, page, size));
    }

    /**
     * 发布主题（富文本）
     */
    @PostMapping
    public Result<Map<String, Object>> postTopic(@Valid @RequestBody ForumTopicDTO dto, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        String username = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
        Long topicId = forumApplicationService.postTopic(familyId, userId, username, dto);
        Map<String, Object> data = new HashMap<>(4);
        data.put("id", topicId);
        return Result.success(data);
    }

    /**
     * 查询主题详情（含回复列表）
     */
    @GetMapping("/{id}")
    public Result<ForumTopicVO> getTopic(@PathVariable Long id, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        return Result.success(forumApplicationService.getTopic(familyId, id, userId));
    }

    /**
     * 删除主题（仅发帖人）
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteTopic(@PathVariable Long id, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        forumApplicationService.deleteTopic(familyId, id, getCurrentUserId(session));
        return Result.success();
    }

    /**
     * 回复主题
     */
    @PostMapping("/{id}/reply")
    public Result<ForumReplyVO> replyTopic(@PathVariable Long id,
                                           @Valid @RequestBody ForumReplyVO reply,
                                           HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        String username = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
        return Result.success(forumApplicationService.replyTopic(familyId, id, userId, username, reply));
    }

    /**
     * 删除回复（仅回复人）
     */
    @DeleteMapping("/reply/{replyId}")
    public Result<Void> deleteReply(@PathVariable Long replyId, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        forumApplicationService.deleteReply(familyId, replyId, getCurrentUserId(session));
        return Result.success();
    }

    /**
     * 论坛富文本配图上传（仅存文件，不进入相册）
     *
     * @param file 图片文件
     * @return 图片访问地址
     */
    @PostMapping("/upload-image")
    public Result<Map<String, Object>> uploadImage(@RequestParam("file") MultipartFile file,
                                                   HttpSession session) {
        Long userId = getCurrentUserId(session);
        getCurrentFamilyId(session);

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

        try {
            Path dirPath = Paths.get(photoDir);
            Files.createDirectories(dirPath);
            String ext = contentType.substring(contentType.lastIndexOf('/') + 1);
            String fileName = "forum_" + userId + "_" + UUID.randomUUID() + "." + ext;
            file.transferTo(dirPath.resolve(fileName).toFile());
            String url = "/api/photo/" + fileName;
            logger.info("用户 {} 上传论坛配图: {}", userId, url);

            Map<String, Object> data = new HashMap<>(4);
            data.put("url", url);
            data.put("fileName", fileName);
            return Result.success(data);
        } catch (IOException e) {
            logger.error("Failed to upload forum image for user={}: {}", userId, e.getMessage(), e);
            return Result.fail(500, "上传失败");
        }
    }
}
