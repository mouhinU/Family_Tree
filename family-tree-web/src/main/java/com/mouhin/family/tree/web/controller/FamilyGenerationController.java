package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.FamilyApplicationService;
import com.mouhin.family.tree.application.service.FamilyGenerationApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.FamilyGenerationDTO;
import com.mouhin.family.tree.common.dto.GenerationLayoutDTO;
import com.mouhin.family.tree.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 族谱辈分（世代名称）控制器
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@RestController
@RequestMapping("/api/generation")
public class FamilyGenerationController extends BaseController {

    private final FamilyGenerationApplicationService familyGenerationService;
    private final FamilyApplicationService familyService;

    public FamilyGenerationController(FamilyGenerationApplicationService familyGenerationService,
                                      FamilyApplicationService familyService) {
        this.familyGenerationService = familyGenerationService;
        this.familyService = familyService;
    }

    /**
     * 获取当前家族的所有辈分（世代名称）
     */
    @GetMapping
    public Result<List<FamilyGenerationDTO>> list(HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        return Result.success(familyGenerationService.listGenerations(familyId));
    }

    /**
     * 批量保存辈分（世代名称）
     */
    @PutMapping
    public Result<Void> save(@RequestBody List<FamilyGenerationDTO> dtos, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        familyGenerationService.saveGenerations(familyId, dtos);
        return Result.success();
    }

    /**
     * 获取当前家族的辈分管理行列布局
     */
    @GetMapping("/layout")
    public Result<GenerationLayoutDTO> getLayout(HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        var family = familyService.getFamilyById(familyId);
        if (family == null) {
            return Result.success(new GenerationLayoutDTO(5, 5));
        }
        return Result.success(new GenerationLayoutDTO(
                family.getGenerationCols() != null ? family.getGenerationCols() : 5,
                family.getGenerationRows() != null ? family.getGenerationRows() : 5));
    }

    /**
     * 保存辈分管理行列布局
     */
    @PutMapping("/layout")
    public Result<Void> saveLayout(@RequestBody GenerationLayoutDTO dto, HttpSession session,
                                   HttpServletRequest request) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        familyService.updateGenerationLayout(familyId, userId, dto.getCols(), dto.getRows(),
                (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME), getClientIp(request));
        return Result.success();
    }
}
