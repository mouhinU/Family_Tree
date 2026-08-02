package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.OfferingDTO;
import com.mouhin.family.tree.common.dto.OfferingStatVO;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.service.FamilyOfferingService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 祭奠控制器（上香烛 / 烧纸）
 *
 * @author Family-Tree
 * @date 2026-08-01
 */
@RestController
@RequestMapping("/api/offering")
public class FamilyOfferingController {

    private final FamilyOfferingService familyOfferingService;

    public FamilyOfferingController(FamilyOfferingService familyOfferingService) {
        this.familyOfferingService = familyOfferingService;
    }

    /**
     * 记录一次上香烛 / 烧纸操作。
     *
     * @param dto     祭奠信息（节点ID + 类型）
     * @param session 会话
     * @return 统一响应
     */
    @PostMapping
    public Result<Void> offer(@RequestBody OfferingDTO dto, HttpSession session) {
        Long userId = getCurrentUserId(session);
        familyOfferingService.offer(userId, dto);
        return Result.success();
    }

    /**
     * 查询某已故节点的祭奠统计（香烛、烧纸的次数与人员明细）。
     *
     * @param nodeId  节点ID
     * @param session 会话
     * @return 祭奠统计列表
     */
    @GetMapping("/node/{nodeId}")
    public Result<List<OfferingStatVO>> listByNode(@PathVariable Long nodeId, HttpSession session) {
        Long userId = getCurrentUserId(session);
        return Result.success(familyOfferingService.listStatsByNode(userId, nodeId));
    }

    private Long getCurrentUserId(HttpSession session) {
        return (Long) session.getAttribute(FamilyTreeConsts.SESSION_USER_ID);
    }
}
