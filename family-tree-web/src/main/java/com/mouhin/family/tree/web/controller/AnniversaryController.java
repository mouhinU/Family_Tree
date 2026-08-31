package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.AnniversaryApplicationService;
import com.mouhin.family.tree.common.dto.AnniversaryDTO;
import com.mouhin.family.tree.common.dto.AnniversaryVO;
import com.mouhin.family.tree.common.result.Result;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 家族纪念日控制器
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@RestController
@RequestMapping("/api/anniversary")
public class AnniversaryController extends BaseController {

    private final AnniversaryApplicationService anniversaryApplicationService;

    public AnniversaryController(AnniversaryApplicationService anniversaryApplicationService) {
        this.anniversaryApplicationService = anniversaryApplicationService;
    }

    /**
     * 查询家族纪念日列表（含距下次纪念日天数）
     */
    @GetMapping
    public Result<List<AnniversaryVO>> listAnniversaries(HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        return Result.success(anniversaryApplicationService.listAnniversaries(familyId, userId));
    }

    /**
     * 新增纪念日
     */
    @PostMapping
    public Result<Map<String, Object>> createAnniversary(@Valid @RequestBody AnniversaryDTO dto,
                                                         HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        Long id = anniversaryApplicationService.createAnniversary(familyId, userId, dto);
        Map<String, Object> data = new HashMap<>(4);
        data.put("id", id);
        return Result.success(data);
    }

    /**
     * 更新纪念日（仅创建者）
     */
    @PutMapping("/{id}")
    public Result<Void> updateAnniversary(@PathVariable Long id,
                                          @Valid @RequestBody AnniversaryDTO dto,
                                          HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        anniversaryApplicationService.updateAnniversary(familyId, id, userId, dto);
        return Result.success();
    }

    /**
     * 删除纪念日（仅创建者）
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteAnniversary(@PathVariable Long id, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        anniversaryApplicationService.deleteAnniversary(familyId, id, getCurrentUserId(session));
        return Result.success();
    }
}
