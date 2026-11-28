package com.mouhin.family.tree.web.controller.user;

import com.mouhin.family.tree.application.service.CrossFamilyLinkProposalApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.AffinityLinkDTO;
import com.mouhin.family.tree.common.dto.CrossFamilyLinkProposalVO;
import com.mouhin.family.tree.common.dto.PageResult;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.web.controller.BaseController;
import com.mouhin.family.tree.web.security.RequireFamilyManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 普通用户跨族链接提案控制器。
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
@RestController
@RequestMapping("/api")
public class CrossFamilyLinkProposalController extends BaseController {

    private final CrossFamilyLinkProposalApplicationService proposalService;

    public CrossFamilyLinkProposalController(CrossFamilyLinkProposalApplicationService proposalService) {
        this.proposalService = proposalService;
    }

    /**
     * 创建人工跨族链接提案。
     */
    @PostMapping("/node/{nodeId}/link-proposals")
    @RequireFamilyManager
    public Result<CrossFamilyLinkProposalVO> createProposal(@PathVariable Long nodeId,
                                                           @Valid @RequestBody AffinityLinkDTO dto,
                                                           HttpSession session, HttpServletRequest request) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        String username = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
        return Result.success(proposalService.createProposal(dto, familyId, nodeId, userId,
                username, getClientIp(request)));
    }

    /**
     * 查询当前家族相关提案。
     */
    @GetMapping("/link-proposals")
    public Result<PageResult<CrossFamilyLinkProposalVO>> listProposals(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size, HttpSession session) {
        return Result.success(proposalService.listFamilyProposals(getCurrentFamilyId(session),
                getCurrentUserId(session), page, size));
    }

    /**
     * 查询当前家族可见的提案详情。
     */
    @GetMapping("/link-proposals/{proposalId}")
    public Result<CrossFamilyLinkProposalVO> getProposal(@PathVariable Long proposalId, HttpSession session) {
        return Result.success(proposalService.getFamilyProposal(proposalId, getCurrentFamilyId(session),
                getCurrentUserId(session)));
    }

    /**
     * 当前账号对其在本家族绑定的节点表示同意。
     */
    @PostMapping("/link-proposals/{proposalId}/agree")
    public Result<CrossFamilyLinkProposalVO> agree(@PathVariable Long proposalId, HttpSession session,
                                                  HttpServletRequest request) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        String username = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
        return Result.success(proposalService.agree(proposalId, familyId, userId,
                username, getClientIp(request)));
    }

    /**
     * 撤回当前账号的同意。
     */
    @PostMapping("/link-proposals/{proposalId}/withdraw")
    public Result<CrossFamilyLinkProposalVO> withdraw(@PathVariable Long proposalId, HttpSession session,
                                                     HttpServletRequest request) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        String username = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
        return Result.success(proposalService.withdraw(proposalId, familyId, userId,
                username, getClientIp(request)));
    }

    /**
     * 发起人或任一端家族管理者取消提案。
     */
    @PostMapping("/link-proposals/{proposalId}/cancel")
    public Result<CrossFamilyLinkProposalVO> cancel(@PathVariable Long proposalId, HttpSession session,
                                                   HttpServletRequest request) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        String username = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
        return Result.success(proposalService.cancel(proposalId, familyId, userId,
                username, getClientIp(request)));
    }
}
