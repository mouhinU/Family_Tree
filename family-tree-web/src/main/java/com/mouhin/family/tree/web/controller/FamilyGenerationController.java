package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.common.dto.FamilyGenerationDTO;
import com.mouhin.family.tree.common.dto.GenerationLayoutDTO;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.service.FamilyGenerationService;
import com.mouhin.family.tree.service.FamilyService;
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
public class FamilyGenerationController extends BaseController {

    private final FamilyGenerationService familyGenerationService;
    private final FamilyService familyService;

    public FamilyGenerationController(FamilyGenerationService familyGenerationService,
                                      FamilyService familyService) {
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
    public Result<Void> saveLayout(@RequestBody GenerationLayoutDTO dto, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        familyService.updateGenerationLayout(familyId, userId, dto.getCols(), dto.getRows());
        return Result.success();
    }
}
