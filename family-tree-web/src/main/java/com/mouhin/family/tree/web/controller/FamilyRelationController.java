package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.FamilyRelationDTO;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.service.FamilyRelationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
public class FamilyRelationController {

    private final FamilyRelationService familyRelationService;

    public FamilyRelationController(FamilyRelationService familyRelationService) {
        this.familyRelationService = familyRelationService;
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody FamilyRelationDTO dto, HttpSession session) {
        Long userId = getCurrentUserId(session);
        Long relationId = familyRelationService.createRelation(userId, dto);
        Map<String, Object> data = new HashMap<>(4);
        data.put("relationId", relationId);
        return Result.success(data);
    }

    @DeleteMapping("/{relationId}")
    public Result<Void> delete(@PathVariable Long relationId, HttpSession session) {
        Long userId = getCurrentUserId(session);
        familyRelationService.deleteRelation(userId, relationId);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@RequestBody FamilyRelationDTO dto, HttpSession session) {
        Long userId = getCurrentUserId(session);
        familyRelationService.updateRelation(userId, dto);
        return Result.success();
    }

    @GetMapping("/node/{nodeId}")
    public Result<List<FamilyRelationDTO>> listByNode(@PathVariable Long nodeId, HttpSession session) {
        Long userId = getCurrentUserId(session);
        return Result.success(familyRelationService.listRelationsByNode(userId, nodeId));
    }

    @GetMapping("/all")
    public Result<List<FamilyRelationDTO>> listAll(HttpSession session) {
        Long userId = getCurrentUserId(session);
        return Result.success(familyRelationService.listAllRelations(userId));
    }

    private Long getCurrentUserId(HttpSession session) {
        return (Long) session.getAttribute(FamilyTreeConsts.SESSION_USER_ID);
    }
}
