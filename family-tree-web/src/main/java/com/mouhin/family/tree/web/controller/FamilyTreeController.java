package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.TreeNodeVO;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.application.service.FamilyTreeApplicationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 族谱树形结构控制器
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@RestController
@RequestMapping("/api/tree")
public class FamilyTreeController extends BaseController {

    private final FamilyTreeApplicationService familyTreeService;

    public FamilyTreeController(FamilyTreeApplicationService familyTreeService) {
        this.familyTreeService = familyTreeService;
    }

    @GetMapping("/full")
    public Result<List<TreeNodeVO>> fullTree(HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        return Result.success(familyTreeService.getFullTree(familyId));
    }

    @GetMapping("/subtree")
    public Result<TreeNodeVO> subTree(@RequestParam Long nodeId, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        return Result.success(familyTreeService.getSubTree(familyId, nodeId));
    }
}
