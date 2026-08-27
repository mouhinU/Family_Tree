package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.MessageCreateDTO;
import com.mouhin.family.tree.common.dto.MessageVO;
import com.mouhin.family.tree.common.dto.PageResult;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.application.service.FamilyMessageApplicationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 家族留言控制器
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@RestController
@RequestMapping("/api/message")
public class FamilyMessageController extends BaseController {

    private final FamilyMessageApplicationService messageService;

    public FamilyMessageController(FamilyMessageApplicationService messageService) {
        this.messageService = messageService;
    }

    /**
     * 发布留言
     *
     * @param dto     留言内容
     * @param session 当前会话
     * @return 操作结果
     */
    @PostMapping
    public Result<Void> postMessage(@RequestBody MessageCreateDTO dto, HttpSession session) {
        Long userId = getCurrentUserId(session);
        Long familyId = getCurrentFamilyId(session);
        String username = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
        messageService.postMessage(familyId, userId, username, dto);
        return Result.success();
    }

    /**
     * 分页查询留言列表
     *
     * @param page     页码（默认 1）
     * @param size     每页大小（默认 20）
     * @param category 留言分类（可选，GENERAL-普通留言, FEATURE-功能需求，不传则查全部）
     * @param session  当前会话
     * @return 分页留言列表
     */
    @GetMapping
    public Result<PageResult<MessageVO>> listMessages(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String category,
            HttpSession session) {
        Long userId = getCurrentUserId(session);
        Long familyId = getCurrentFamilyId(session);
        PageResult<MessageVO> result = messageService.listMessages(familyId, userId, category, page, size);
        return Result.success(result);
    }

    /**
     * 查询留言的回复列表
     *
     * @param id      留言ID
     * @param session 当前会话
     * @return 回复列表
     */
    @GetMapping("/{id}/replies")
    public Result<List<MessageVO>> listReplies(@PathVariable Long id, HttpSession session) {
        Long userId = getCurrentUserId(session);
        List<MessageVO> replies = messageService.listReplies(id, userId);
        return Result.success(replies);
    }

    /**
     * 删除留言
     *
     * @param id      留言ID
     * @param session 当前会话
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteMessage(@PathVariable Long id, HttpSession session) {
        Long userId = getCurrentUserId(session);
        messageService.deleteMessage(id, userId);
        return Result.success();
    }

    /**
     * 点赞留言
     *
     * @param id      留言ID
     * @param session 当前会话
     * @return 操作结果
     */
    @PostMapping("/{id}/like")
    public Result<Void> likeMessage(@PathVariable Long id, HttpSession session) {
        Long userId = getCurrentUserId(session);
        Long familyId = getCurrentFamilyId(session);
        messageService.likeMessage(id, userId, familyId);
        return Result.success();
    }

    /**
     * 取消点赞
     *
     * @param id      留言ID
     * @param session 当前会话
     * @return 操作结果
     */
    @DeleteMapping("/{id}/like")
    public Result<Void> unlikeMessage(@PathVariable Long id, HttpSession session) {
        Long userId = getCurrentUserId(session);
        messageService.unlikeMessage(id, userId);
        return Result.success();
    }
}
