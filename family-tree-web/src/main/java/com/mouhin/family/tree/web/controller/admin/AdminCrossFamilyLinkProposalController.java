package com.mouhin.family.tree.web.controller.admin;

import com.mouhin.family.tree.application.service.CrossFamilyLinkProposalApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.CrossFamilyLinkProposalCommentDTO;
import com.mouhin.family.tree.common.dto.CrossFamilyLinkProposalVO;
import com.mouhin.family.tree.common.dto.PageResult;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.web.controller.BaseController;
import com.mouhin.family.tree.web.security.RequireSystemAdmin;
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
 * 平台管理员跨族链接提案控制器。
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
@RestController
@RequestMapping("/api/admin/link-proposals")
public class AdminCrossFamilyLinkProposalController extends BaseController {

    private final CrossFamilyLinkProposalApplicationService proposalService;

    public AdminCrossFamilyLinkProposalController(CrossFamilyLinkProposalApplicationService proposalService) {
        this.proposalService = proposalService;
    }

    /**
     * 分页查询提案。
     */
    @GetMapping
    @RequireSystemAdmin
    public Result<PageResult<CrossFamilyLinkProposalVO>> listProposals(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(proposalService.listAdminProposals(status, page, size));
    }

    /**
     * 查询提案详情及完整投票、事件记录。
     */
    @GetMapping("/{proposalId}")
    @RequireSystemAdmin
    public Result<CrossFamilyLinkProposalVO> getProposal(@PathVariable Long proposalId) {
        return Result.success(proposalService.getAdminProposal(proposalId));
    }

    /**
     * 追加平台审核备注。
     */
    @PostMapping("/{proposalId}/comment")
    @RequireSystemAdmin
    public Result<CrossFamilyLinkProposalVO> comment(@PathVariable Long proposalId,
                                                     @Valid @RequestBody CrossFamilyLinkProposalCommentDTO dto,
                                                     HttpSession session, HttpServletRequest request) {
        return Result.success(proposalService.comment(proposalId, dto, getCurrentUserId(session),
                currentUsername(session), getClientIp(request)));
    }

    /**
     * 审核通过并建立正式跨族链接。
     */
    @PostMapping("/{proposalId}/approve")
    @RequireSystemAdmin
    public Result<CrossFamilyLinkProposalVO> approve(@PathVariable Long proposalId,
                                                     @Valid @RequestBody(required = false)
                                                     CrossFamilyLinkProposalCommentDTO dto,
                                                     HttpSession session, HttpServletRequest request) {
        return Result.success(proposalService.approve(proposalId, dto, getCurrentUserId(session),
                currentUsername(session), getClientIp(request)));
    }

    /**
     * 驳回提案。
     */
    @PostMapping("/{proposalId}/reject")
    @RequireSystemAdmin
    public Result<CrossFamilyLinkProposalVO> reject(@PathVariable Long proposalId,
                                                    @Valid @RequestBody CrossFamilyLinkProposalCommentDTO dto,
                                                    HttpSession session, HttpServletRequest request) {
        return Result.success(proposalService.reject(proposalId, dto, getCurrentUserId(session),
                currentUsername(session), getClientIp(request)));
    }

    private String currentUsername(HttpSession session) {
        return (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
    }
}
