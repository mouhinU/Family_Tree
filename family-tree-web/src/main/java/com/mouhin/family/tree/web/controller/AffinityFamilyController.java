package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.AffinityFamilyApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.AffinityCandidateVO;
import com.mouhin.family.tree.common.dto.AffinityLinkDTO;
import com.mouhin.family.tree.common.dto.FamilyBriefVO;
import com.mouhin.family.tree.common.dto.NodeAffinityFamilyVO;
import com.mouhin.family.tree.common.dto.NodeMemberFamilyVO;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.web.security.NodeAccessActionEnum;
import com.mouhin.family.tree.web.security.RequireFamilyManager;
import com.mouhin.family.tree.web.security.RequireNodeAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 跨族亲族控制器：亲族家族/归属家族展示、人工建链解链、候选清单与家族只读摘要
 *
 * <p>详情可见性收口：亲族解析入口与节点详情同级（{@link RequireNodeAccess} VIEW）；
 * 归属家族清单为隐私受限数据，服务内另设"仅本人或本族管理者"门。
 * 建链/候选仅本族管理者（{@link RequireFamilyManager}）；解链由服务层显式校验
 * 链接两端家族任一管理者身份（切面只覆盖会话当前家族，无法校验他族端）。</p>
 *
 * @author Family-Tree
 * @date 2026-11-20
 */
@RestController
@RequestMapping("/api")
public class AffinityFamilyController extends BaseController {

    private final AffinityFamilyApplicationService affinityFamilyService;

    public AffinityFamilyController(AffinityFamilyApplicationService affinityFamilyService) {
        this.affinityFamilyService = affinityFamilyService;
    }

    /**
     * 节点亲族家族清单（父/母/妻/夫族），可见详情者均可读
     */
    @GetMapping("/node/{nodeId}/affinity-families")
    @RequireNodeAccess(node = "#nodeId", action = NodeAccessActionEnum.VIEW)
    public Result<List<NodeAffinityFamilyVO>> affinityFamilies(@PathVariable Long nodeId, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        return Result.success(affinityFamilyService.listAffinityFamilies(familyId, nodeId));
    }

    /**
     * 节点绑定账号归属的其他家族清单（Phase 0）：仅本人与本族管理者可见
     */
    @GetMapping("/node/{nodeId}/member-families")
    public Result<List<NodeMemberFamilyVO>> memberFamilies(@PathVariable Long nodeId, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        return Result.success(affinityFamilyService.listMemberFamilies(familyId, nodeId, userId));
    }

    /**
     * 人工指认建链：本族管理者把本族节点关联到他族节点
     */
    @PostMapping("/node/{nodeId}/affinity-links")
    @RequireFamilyManager
    public Result<Map<String, Object>> linkNode(@PathVariable Long nodeId,
                                                @Valid @RequestBody AffinityLinkDTO dto,
                                                HttpSession session, HttpServletRequest request) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        String username = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
        Long linkId = affinityFamilyService.linkNode(familyId, nodeId, dto.getDstNodeId(),
                dto.getLinkType(), userId, username, getClientIp(request));
        Map<String, Object> data = new HashMap<>(2);
        data.put("linkId", linkId);
        return Result.success(data);
    }

    /**
     * 解除亲族关联：链接两端家族任一管理者可操作
     */
    @DeleteMapping("/node/{nodeId}/affinity-links/{linkId}")
    public Result<Void> unlinkNode(@PathVariable Long nodeId, @PathVariable Long linkId,
                                   HttpSession session, HttpServletRequest request) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        String username = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
        affinityFamilyService.unlinkNode(familyId, nodeId, linkId, userId, username, getClientIp(request));
        return Result.success();
    }

    /**
     * 跨族亲族候选清单（姓名+生日证据）：仅本族管理员可见，确认后才成链
     */
    @GetMapping("/node/{nodeId}/affinity-candidates")
    @RequireFamilyManager
    public Result<List<AffinityCandidateVO>> affinityCandidates(@PathVariable Long nodeId, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        return Result.success(affinityFamilyService.suggestCandidates(familyId, nodeId));
    }

    /**
     * 家族只读摘要（家族名/始祖/代数/成员数），供亲族区块展示与跳转前预览
     */
    @GetMapping("/family/{familyId}/brief")
    public Result<FamilyBriefVO> familyBrief(@PathVariable Long familyId) {
        return Result.success(affinityFamilyService.getFamilyBrief(familyId));
    }
}
