package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.BiographyApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.BiographyUpdateDTO;
import com.mouhin.family.tree.common.result.Result;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 人物传记控制器
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@RestController
@RequestMapping("/api/biography")
public class BiographyController extends BaseController {

    private final BiographyApplicationService biographyApplicationService;

    public BiographyController(BiographyApplicationService biographyApplicationService) {
        this.biographyApplicationService = biographyApplicationService;
    }

    /**
     * 查询人物传记（富文本）
     */
    @GetMapping("/{nodeId}")
    public Result<Map<String, Object>> getBiography(@PathVariable Long nodeId, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        String biography = biographyApplicationService.getBiography(familyId, nodeId);
        Map<String, Object> data = new HashMap<>(4);
        data.put("nodeId", nodeId);
        data.put("biography", biography);
        return Result.success(data);
    }

    /**
     * 更新人物传记（富文本，服务端清洗后存储）
     */
    @PutMapping("/{nodeId}")
    public Result<Void> updateBiography(@PathVariable Long nodeId,
                                        @RequestBody BiographyUpdateDTO dto,
                                        HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        String username = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
        biographyApplicationService.updateBiography(familyId, nodeId, userId, username, dto);
        return Result.success();
    }
}
