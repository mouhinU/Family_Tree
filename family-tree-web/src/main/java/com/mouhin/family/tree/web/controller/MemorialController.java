package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.MemorialApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.MemorialMessageDTO;
import com.mouhin.family.tree.common.dto.MemorialMessageVO;
import com.mouhin.family.tree.common.result.Result;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 祭堂缅怀留言控制器
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@RestController
@RequestMapping("/api/memorial")
public class MemorialController extends BaseController {

    private final MemorialApplicationService memorialApplicationService;

    public MemorialController(MemorialApplicationService memorialApplicationService) {
        this.memorialApplicationService = memorialApplicationService;
    }

    /**
     * 查询节点的缅怀留言列表
     */
    @GetMapping("/node/{nodeId}")
    public Result<List<MemorialMessageVO>> listMessages(@PathVariable Long nodeId, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        return Result.success(memorialApplicationService.listMessages(familyId, nodeId, userId));
    }

    /**
     * 发布缅怀留言
     */
    @PostMapping("/node/{nodeId}")
    public Result<MemorialMessageVO> postMessage(@PathVariable Long nodeId,
                                                 @Valid @RequestBody MemorialMessageDTO dto,
                                                 HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        String username = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
        return Result.success(memorialApplicationService.postMessage(familyId, nodeId, userId, username, dto));
    }

    /**
     * 删除缅怀留言（仅留言人）
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteMessage(@PathVariable Long id, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        memorialApplicationService.deleteMessage(familyId, id, getCurrentUserId(session));
        return Result.success();
    }
}
