package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.PlatformAdminApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.FamilyAdminDetailVO;
import com.mouhin.family.tree.common.dto.FamilyAdminRowVO;
import com.mouhin.family.tree.common.dto.FamilyGovernDTO;
import com.mouhin.family.tree.common.dto.FamilySettingSaveDTO;
import com.mouhin.family.tree.common.dto.FamilySettingVO;
import com.mouhin.family.tree.common.dto.PageResult;
import com.mouhin.family.tree.common.dto.PlatformOverviewVO;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.web.security.RequireSystemAdmin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台（系统）管理员控制器：跨家族总览、家族概况与家族级配置。
 *
 * <p>所有端点由 {@link RequireSystemAdmin} 声明平台权限裁决，与家族角色无关；
 * 前端隐藏入口仅为体验优化，不作为安全边界。</p>
 *
 * @author Family-Tree
 * @date 2026-11-15
 */
@RestController
@RequestMapping("/api/admin")
public class AdminFamilyController extends BaseController {

    private final PlatformAdminApplicationService platformAdminService;

    public AdminFamilyController(PlatformAdminApplicationService platformAdminService) {
        this.platformAdminService = platformAdminService;
    }

    /**
     * 平台总览统计
     *
     * @return 总览数据
     */
    @GetMapping("/overview")
    @RequireSystemAdmin
    public Result<PlatformOverviewVO> overview() {
        return Result.success(platformAdminService.getOverview());
    }

    /**
     * 分页查询家族概况
     *
     * @param keyword 名称 / 堂号前缀或邀请码，可为空
     * @param page    页码，从 1 开始
     * @param size    每页大小
     * @return 家族概况分页结果
     */
    @GetMapping("/families")
    @RequireSystemAdmin
    public Result<PageResult<FamilyAdminRowVO>> listFamilies(@RequestParam(required = false) String keyword,
                                                             @RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        return Result.success(platformAdminService.listFamilies(normalizeSearchKeyword(keyword), page, size));
    }

    /**
     * 查询单个家族的概况详情
     *
     * @param familyId 家族ID
     * @return 家族详情
     */
    @GetMapping("/family/{familyId}")
    @RequireSystemAdmin
    public Result<FamilyAdminDetailVO> familyDetail(@PathVariable Long familyId) {
        return Result.success(platformAdminService.getFamilyDetail(familyId));
    }

    /**
     * 查询家族级配置
     *
     * @param familyId 家族ID
     * @return 配置项列表
     */
    @GetMapping("/family/{familyId}/settings")
    @RequireSystemAdmin
    public Result<List<FamilySettingVO>> listSettings(@PathVariable Long familyId) {
        return Result.success(platformAdminService.listSettings(familyId));
    }

    /**
     * 保存家族级配置
     *
     * @param familyId 家族ID
     * @param dto      配置提交对象
     * @param session  HTTP 会话
     * @param request  HTTP 请求
     * @return 空结果
     */
    @PutMapping("/family/{familyId}/settings")
    @RequireSystemAdmin
    public Result<Void> saveSettings(@PathVariable Long familyId, @RequestBody FamilySettingSaveDTO dto,
                                     HttpSession session, HttpServletRequest request) {
        Long operatorId = getCurrentUserId(session);
        String operatorName = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
        platformAdminService.saveSettings(familyId, dto, operatorId, operatorName, getClientIp(request));
        return Result.success();
    }

    /**
     * 重命名家族
     *
     * @param familyId 家族ID
     * @param dto      治理提交对象，取 name 字段
     * @param session  HTTP 会话
     * @param request  HTTP 请求
     * @return 变更后的家族概况行
     */
    @PutMapping("/family/{familyId}")
    @RequireSystemAdmin
    public Result<FamilyAdminRowVO> renameFamily(@PathVariable Long familyId, @RequestBody FamilyGovernDTO dto,
                                                 HttpSession session, HttpServletRequest request) {
        Long operatorId = getCurrentUserId(session);
        String operatorName = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
        return Result.success(platformAdminService.renameFamily(familyId, dto == null ? null : dto.getName(),
                operatorId, operatorName, getClientIp(request)));
    }

    /**
     * 变更家族状态：冻结 / 归档 / 恢复正常
     *
     * @param familyId 家族ID
     * @param dto      治理提交对象，取 status 字段
     * @param session  HTTP 会话
     * @param request  HTTP 请求
     * @return 变更后的家族概况行
     */
    @PutMapping("/family/{familyId}/status")
    @RequireSystemAdmin
    public Result<FamilyAdminRowVO> updateFamilyStatus(@PathVariable Long familyId, @RequestBody FamilyGovernDTO dto,
                                                       HttpSession session, HttpServletRequest request) {
        Long operatorId = getCurrentUserId(session);
        String operatorName = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
        return Result.success(platformAdminService.updateFamilyStatus(familyId,
                dto == null ? null : dto.getStatus(), operatorId, operatorName, getClientIp(request)));
    }

    /**
     * 转让族长：用于原族长失联或账号注销时恢复家族管理
     *
     * @param familyId 家族ID
     * @param dto      治理提交对象，取 userId 字段
     * @param session  HTTP 会话
     * @param request  HTTP 请求
     * @return 变更后的家族概况行
     */
    @PutMapping("/family/{familyId}/owner")
    @RequireSystemAdmin
    public Result<FamilyAdminRowVO> transferOwner(@PathVariable Long familyId, @RequestBody FamilyGovernDTO dto,
                                                  HttpSession session, HttpServletRequest request) {
        Long operatorId = getCurrentUserId(session);
        String operatorName = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
        return Result.success(platformAdminService.transferOwner(familyId,
                dto == null ? null : dto.getUserId(), operatorId, operatorName, getClientIp(request)));
    }
}
