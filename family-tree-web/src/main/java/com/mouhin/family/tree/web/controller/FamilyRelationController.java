package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.FamilyRelationApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.FamilyRelationDTO;
import com.mouhin.family.tree.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 族谱关系控制器
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@RestController
@RequestMapping("/api/relation")
public class FamilyRelationController extends BaseController {

    private final FamilyRelationApplicationService familyRelationService;

    public FamilyRelationController(FamilyRelationApplicationService familyRelationService) {
        this.familyRelationService = familyRelationService;
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody FamilyRelationDTO dto, HttpSession session,
                                              HttpServletRequest request) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        String username = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
        String ipAddress = getClientIp(request);
        Long relationId = familyRelationService.createRelation(familyId, userId, username, ipAddress, dto);
        Map<String, Object> data = new HashMap<>(4);
        data.put("relationId", relationId);
        return Result.success(data);
    }

    @DeleteMapping("/{relationId}")
    public Result<Void> delete(@PathVariable Long relationId, HttpSession session,
                               HttpServletRequest request) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        String username = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
        String ipAddress = getClientIp(request);
        familyRelationService.deleteRelation(familyId, relationId, userId, username, ipAddress);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@RequestBody FamilyRelationDTO dto, HttpSession session,
                               HttpServletRequest request) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        String username = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
        String ipAddress = getClientIp(request);
        familyRelationService.updateRelation(familyId, userId, username, ipAddress, dto);
        return Result.success();
    }

    @GetMapping("/node/{nodeId}")
    public Result<List<FamilyRelationDTO>> listByNode(@PathVariable Long nodeId, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        return Result.success(familyRelationService.listRelationsByNode(familyId, nodeId));
    }

    @GetMapping("/all")
    public Result<List<FamilyRelationDTO>> listAll(HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        return Result.success(familyRelationService.listAllRelations(familyId));
    }
}
