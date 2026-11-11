package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.FamilyNodeApplicationService;
import com.mouhin.family.tree.application.service.FamilyNodeVisibilityApplicationService;
import com.mouhin.family.tree.application.service.FamilyTreeApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.ColorUpdateDTO;
import com.mouhin.family.tree.common.dto.FamilyNodeDTO;
import com.mouhin.family.tree.common.dto.NodeCreateDTO;
import com.mouhin.family.tree.common.dto.NodeDetailVO;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.domain.event.OperationPerformedEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;

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
    private final FamilyTreeApplicationService familyTreeService;
    private final FamilyNodeVisibilityApplicationService visibilityService;
    private final ApplicationEventPublisher eventPublisher;

    public FamilyNodeController(FamilyNodeApplicationService familyNodeService,
                                FamilyTreeApplicationService familyTreeService,
                                FamilyNodeVisibilityApplicationService visibilityService,
                                ApplicationEventPublisher eventPublisher) {
        this.familyNodeService = familyNodeService;
        this.familyTreeService = familyTreeService;
        this.visibilityService = visibilityService;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping
    public Result<Map<String, Object>> create(@Valid @RequestBody NodeCreateDTO dto, HttpSession session,
                                              HttpServletRequest request) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        String username = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
        String ipAddress = getClientIp(request);
        Long nodeId = familyNodeService.createNode(familyId, userId, username, ipAddress, dto);

        // 操作日志由 NodeCreatedEvent 监听器统一记录（含 GEDCOM 等其他入口）
        Map<String, Object> data = new HashMap<>(4);
        data.put("nodeId", nodeId);
        return Result.success(data);
    }

    @PutMapping
    public Result<Void> update(@RequestBody FamilyNodeDTO dto, HttpSession session,
                               HttpServletRequest request) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        String username = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
        String ipAddress = getClientIp(request);
        familyNodeService.updateNode(familyId, userId, username, ipAddress, dto);
        return Result.success();
    }

    @DeleteMapping("/{nodeId}")
    public Result<Void> delete(@PathVariable Long nodeId, HttpSession session,
                               HttpServletRequest request) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        String username = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
        String ipAddress = getClientIp(request);
        familyNodeService.deleteNode(familyId, nodeId, userId, username, ipAddress);
        return Result.success();
    }

    @GetMapping("/{nodeId}")
    public Result<FamilyNodeDTO> get(@PathVariable Long nodeId, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        visibilityService.assertDetailVisible(familyId, userId, nodeId);
        FamilyNodeDTO node = familyNodeService.getNode(familyId, nodeId);
        return Result.success(node);
    }

    /**
     * 族人详情（详情弹窗专用）：返回基本信息 + 备注/农历/字字号讳/墓地等扩展字段 + 全部配偶关系明细。
     * 管理员不受限；非管理员仅可查看其直系血缘链及链上节点直接配偶的详情，范围外一律拒绝。
     */
    @GetMapping("/{nodeId}/detail")
    public Result<NodeDetailVO> detail(@PathVariable Long nodeId, HttpSession session,
                                       HttpServletRequest request) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        String username = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
        String ipAddress = getClientIp(request);
        visibilityService.assertDetailVisible(familyId, userId, nodeId);
        NodeDetailVO detail = familyTreeService.getNodeDetail(familyId, nodeId);
        eventPublisher.publishEvent(OperationPerformedEvent.of(userId, username, "NODE_VIEW",
                "查看族人详情: " + detail.getName(), "node", nodeId, familyId, ipAddress));
        return Result.success(detail);
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
        String username = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
        familyNodeService.updateColor(familyId, dto.getNodeIds(), dto.getColorLabel(),
                userId, username, getClientIp(request));
        return Result.success();
    }
}
