package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.ColorUpdateDTO;
import com.mouhin.family.tree.common.dto.FamilyNodeDTO;
import com.mouhin.family.tree.common.dto.NodeCreateDTO;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.application.service.FamilyNodeApplicationService;
import com.mouhin.family.tree.application.service.OperationLogApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
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
 * 族谱节点控制器
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@RestController
@RequestMapping("/api/node")
public class FamilyNodeController extends BaseController {

    private final FamilyNodeApplicationService familyNodeService;
    private final OperationLogApplicationService operationLogService;

    public FamilyNodeController(FamilyNodeApplicationService familyNodeService,
                                OperationLogApplicationService operationLogService) {
        this.familyNodeService = familyNodeService;
        this.operationLogService = operationLogService;
    }

    @PostMapping
    public Result<Map<String, Object>> create(@Valid @RequestBody NodeCreateDTO dto, HttpSession session,
                                              HttpServletRequest request) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        Long nodeId = familyNodeService.createNode(familyId, userId, dto);

        operationLogService.log(userId, (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME),
                "NODE_CREATE", "创建节点: " + dto.getName(),
                "node", nodeId, familyId, getClientIp(request));

        Map<String, Object> data = new HashMap<>(4);
        data.put("nodeId", nodeId);
        return Result.success(data);
    }

    @PutMapping
    public Result<Void> update(@RequestBody FamilyNodeDTO dto, HttpSession session,
                               HttpServletRequest request) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        familyNodeService.updateNode(familyId, dto);

        operationLogService.log(userId, (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME),
                "NODE_UPDATE", "更新节点: id=" + dto.getId(),
                "node", dto.getId(), familyId, getClientIp(request));
        return Result.success();
    }

    @DeleteMapping("/{nodeId}")
    public Result<Void> delete(@PathVariable Long nodeId, HttpSession session,
                               HttpServletRequest request) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        familyNodeService.deleteNode(familyId, nodeId);

        operationLogService.log(userId, (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME),
                "NODE_DELETE", "删除节点: id=" + nodeId,
                "node", nodeId, familyId, getClientIp(request));
        return Result.success();
    }

    @GetMapping("/{nodeId}")
    public Result<FamilyNodeDTO> get(@PathVariable Long nodeId, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        return Result.success(familyNodeService.getNode(familyId, nodeId));
    }

    @GetMapping("/list")
    public Result<List<FamilyNodeDTO>> list(HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        return Result.success(familyNodeService.listNodes(familyId));
    }

    /**
     * 按姓名关键字搜索节点
     */
    @GetMapping("/search")
    public Result<List<FamilyNodeDTO>> search(@RequestParam String keyword, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        return Result.success(familyNodeService.searchNodes(familyId, keyword));
    }

    @PutMapping("/color")
    public Result<Void> updateColor(@RequestBody ColorUpdateDTO dto, HttpSession session,
                                    HttpServletRequest request) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        familyNodeService.updateColor(familyId, dto.getNodeIds(), dto.getColorLabel());

        operationLogService.log(userId, (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME),
                "NODE_COLOR", "批量修改颜色: " + dto.getNodeIds().size() + "个节点",
                "node", null, familyId, getClientIp(request));
        return Result.success();
    }
}
