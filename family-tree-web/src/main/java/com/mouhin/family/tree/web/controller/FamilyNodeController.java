package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.ColorUpdateDTO;
import com.mouhin.family.tree.common.dto.FamilyNodeDTO;
import com.mouhin.family.tree.common.dto.NodeCreateDTO;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.service.FamilyNodeService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 族谱节点控制器
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@RestController
@RequestMapping("/api/node")
public class FamilyNodeController {

    private final FamilyNodeService familyNodeService;

    public FamilyNodeController(FamilyNodeService familyNodeService) {
        this.familyNodeService = familyNodeService;
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody NodeCreateDTO dto, HttpSession session) {
        Long userId = getCurrentUserId(session);
        Long nodeId = familyNodeService.createNode(userId, dto);
        Map<String, Object> data = new HashMap<>(4);
        data.put("nodeId", nodeId);
        return Result.success(data);
    }

    @PutMapping
    public Result<Void> update(@RequestBody FamilyNodeDTO dto, HttpSession session) {
        Long userId = getCurrentUserId(session);
        familyNodeService.updateNode(userId, dto);
        return Result.success();
    }

    @DeleteMapping("/{nodeId}")
    public Result<Void> delete(@PathVariable Long nodeId, HttpSession session) {
        Long userId = getCurrentUserId(session);
        familyNodeService.deleteNode(userId, nodeId);
        return Result.success();
    }

    @GetMapping("/{nodeId}")
    public Result<FamilyNodeDTO> get(@PathVariable Long nodeId, HttpSession session) {
        Long userId = getCurrentUserId(session);
        return Result.success(familyNodeService.getNode(userId, nodeId));
    }

    @GetMapping("/list")
    public Result<List<FamilyNodeDTO>> list(HttpSession session) {
        Long userId = getCurrentUserId(session);
        return Result.success(familyNodeService.listNodes(userId));
    }

    @PutMapping("/color")
    public Result<Void> updateColor(@RequestBody ColorUpdateDTO dto, HttpSession session) {
        Long userId = getCurrentUserId(session);
        familyNodeService.updateColor(userId, dto.getNodeIds(), dto.getColorLabel());
        return Result.success();
    }

    private Long getCurrentUserId(HttpSession session) {
        return (Long) session.getAttribute(FamilyTreeConsts.SESSION_USER_ID);
    }
}
