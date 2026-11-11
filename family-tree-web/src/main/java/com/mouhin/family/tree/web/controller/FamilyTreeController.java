package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.FamilyNodeVisibilityApplicationService;
import com.mouhin.family.tree.application.service.FamilyTreeApplicationService;
import com.mouhin.family.tree.common.dto.DetailScopeVO;
import com.mouhin.family.tree.common.dto.TreeNodeVO;
import com.mouhin.family.tree.common.result.Result;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
    private final FamilyNodeVisibilityApplicationService visibilityService;

    public FamilyTreeController(FamilyTreeApplicationService familyTreeService,
                                FamilyNodeVisibilityApplicationService visibilityService) {
        this.familyTreeService = familyTreeService;
        this.visibilityService = visibilityService;
    }

    @GetMapping("/full")
    public Result<List<TreeNodeVO>> fullTree(HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        return Result.success(familyTreeService.getFullTree(familyId));
    }

    @GetMapping("/subtree")
    public Result<TreeNodeVO> subTree(@RequestParam Long nodeId, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        visibilityService.assertDetailVisible(familyId, getCurrentUserId(session), nodeId);
        return Result.success(familyTreeService.getSubTree(familyId, nodeId));
    }

    /**
     * 查询当前用户可查看详情的节点范围（供前端预判，后端各详情接口仍独立校验）
     */
    @GetMapping("/detail-scope")
    public Result<DetailScopeVO> detailScope(HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Set<Long> scope = visibilityService.resolveDetailVisibleNodeIds(familyId, getCurrentUserId(session));
        DetailScopeVO vo = new DetailScopeVO();
        if (scope == null) {
            vo.setUnrestricted(true);
        } else {
            vo.setUnrestricted(false);
            vo.setNodeIds(new ArrayList<>(scope));
        }
        return Result.success(vo);
    }
}
