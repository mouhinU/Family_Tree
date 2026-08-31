package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.PrivateMessageApplicationService;
import com.mouhin.family.tree.common.dto.ConversationVO;
import com.mouhin.family.tree.common.dto.PrivateMessageDTO;
import com.mouhin.family.tree.common.dto.PrivateMessageVO;
import com.mouhin.family.tree.common.dto.UserContactVO;
import com.mouhin.family.tree.common.result.Result;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 私信消息控制器
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@RestController
@RequestMapping("/api/private-message")
public class PrivateMessageController extends BaseController {

    private final PrivateMessageApplicationService privateMessageApplicationService;

    public PrivateMessageController(PrivateMessageApplicationService privateMessageApplicationService) {
        this.privateMessageApplicationService = privateMessageApplicationService;
    }

    /**
     * 发送私信
     */
    @PostMapping
    public Result<PrivateMessageVO> sendMessage(@Valid @RequestBody PrivateMessageDTO dto,
                                                HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        return Result.success(privateMessageApplicationService.sendMessage(familyId, userId, dto));
    }

    /**
     * 查询会话列表
     */
    @GetMapping("/conversations")
    public Result<List<ConversationVO>> listConversations(HttpSession session) {
        return Result.success(privateMessageApplicationService.listConversations(getCurrentUserId(session)));
    }

    /**
     * 查询与某用户的会话消息（自动标记已读）
     */
    @GetMapping("/conversation/{peerId}")
    public Result<List<PrivateMessageVO>> getConversation(@PathVariable Long peerId, HttpSession session) {
        Long userId = getCurrentUserId(session);
        return Result.success(privateMessageApplicationService.getConversation(userId, peerId));
    }

    /**
     * 查询当前家族的私信联系人（不含自己）
     */
    @GetMapping("/contacts")
    public Result<List<UserContactVO>> listContacts(HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        return Result.success(privateMessageApplicationService.listContacts(familyId, userId));
    }

    /**
     * 查询未读私信总数
     */
    @GetMapping("/unread-count")
    public Result<Map<String, Object>> countUnread(HttpSession session) {
        long count = privateMessageApplicationService.countUnread(getCurrentUserId(session));
        Map<String, Object> data = new HashMap<>(4);
        data.put("count", count);
        return Result.success(data);
    }
}
