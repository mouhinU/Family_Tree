package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.FamilyGenerationDTO;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.service.FamilyGenerationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 族谱辈分（世代名称）控制器
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@RestController
@RequestMapping("/api/generation")
public class FamilyGenerationController {

    private final FamilyGenerationService familyGenerationService;

    public FamilyGenerationController(FamilyGenerationService familyGenerationService) {
        this.familyGenerationService = familyGenerationService;
    }

    /**
     * 获取当前用户的所有辈分（世代名称）
     */
    @GetMapping
    public Result<List<FamilyGenerationDTO>> list(HttpSession session) {
        Long userId = getCurrentUserId(session);
        return Result.success(familyGenerationService.listGenerations(userId));
    }

    /**
     * 批量保存辈分（世代名称）
     */
    @PutMapping
    public Result<Void> save(@RequestBody List<FamilyGenerationDTO> dtos, HttpSession session) {
        Long userId = getCurrentUserId(session);
        familyGenerationService.saveGenerations(userId, dtos);
        return Result.success();
    }

    private Long getCurrentUserId(HttpSession session) {
        return (Long) session.getAttribute(FamilyTreeConsts.SESSION_USER_ID);
    }
}
