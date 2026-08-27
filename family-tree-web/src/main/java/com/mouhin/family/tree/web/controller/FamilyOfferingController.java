package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.FamilyOfferingApplicationService;
import com.mouhin.family.tree.common.dto.OfferingDTO;
import com.mouhin.family.tree.common.dto.OfferingStatVO;
import com.mouhin.family.tree.common.result.Result;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 祭奠控制器（上香烛 / 烧纸）
 *
 * @author Family-Tree
 * @date 2026-08-01
 */
@RestController
@RequestMapping("/api/offering")
public class FamilyOfferingController extends BaseController {

    private final FamilyOfferingApplicationService familyOfferingService;

    public FamilyOfferingController(FamilyOfferingApplicationService familyOfferingService) {
        this.familyOfferingService = familyOfferingService;
    }

    /**
     * 记录一次上香烛 / 烧纸操作。
     */
    @PostMapping
    public Result<Void> offer(@RequestBody OfferingDTO dto, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        familyOfferingService.offer(familyId, userId, dto);
        return Result.success();
    }

    /**
     * 查询某已故节点的祭奠统计（香烛、烧纸的次数与人员明细）。
     */
    @GetMapping("/node/{nodeId}")
    public Result<List<OfferingStatVO>> listByNode(@PathVariable Long nodeId, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        return Result.success(familyOfferingService.listStatsByNode(familyId, nodeId));
    }
}
