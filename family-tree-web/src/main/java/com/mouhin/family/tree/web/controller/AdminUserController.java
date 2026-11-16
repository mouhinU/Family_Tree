package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.PlatformAdminApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.PageResult;
import com.mouhin.family.tree.common.dto.UserAdminDetailVO;
import com.mouhin.family.tree.common.dto.UserAdminRowVO;
import com.mouhin.family.tree.common.dto.UserPasswordResetVO;
import com.mouhin.family.tree.common.enums.UserAccountTypeEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.web.security.RequireSystemAdmin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 平台（系统）管理员用户控制器：跨家族账号检索与单账号概况。
 *
 * <p>所有端点由 {@link RequireSystemAdmin} 裁决平台权限；联系方式一律脱敏返回。</p>
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@RestController
@RequestMapping("/api/admin")
public class AdminUserController extends BaseController {

    private final PlatformAdminApplicationService platformAdminService;

    public AdminUserController(PlatformAdminApplicationService platformAdminService) {
        this.platformAdminService = platformAdminService;
    }

    /**
     * 分页查询平台用户
     *
     * @param keyword     用户名 / 昵称前缀或手机号邮箱精确词，可为空
     * @param accountType 账号类型：ALL / REAL / AUTO，缺省为 ALL
     * @param page        页码，从 1 开始
     * @param size        每页大小
     * @return 用户分页结果
     */
    @GetMapping("/users")
    @RequireSystemAdmin
    public Result<PageResult<UserAdminRowVO>> listUsers(@RequestParam(required = false) String keyword,
                                                         @RequestParam(required = false) String accountType,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        return Result.success(platformAdminService.listUsers(
                normalizeSearchKeyword(keyword), parseAccountType(accountType), page, size));
    }

    /**
     * 查询单个账号的平台侧概况
     *
     * @param userId 用户ID
     * @return 用户详情
     */
    @GetMapping("/user/{userId}")
    @RequireSystemAdmin
    public Result<UserAdminDetailVO> userDetail(@PathVariable Long userId) {
        return Result.success(platformAdminService.getUserDetail(userId));
    }

    /**
     * 重置账号登录口令：随机一次性口令 + 首登强制改密 + 解除登录锁定
     *
     * @param userId  目标用户ID
     * @param session HTTP 会话
     * @param request HTTP 请求
     * @return 重置结果，一次性口令仅本次返回
     */
    @PostMapping("/user/{userId}/password/reset")
    @RequireSystemAdmin
    public Result<UserPasswordResetVO> resetPassword(@PathVariable Long userId,
                                                     HttpSession session, HttpServletRequest request) {
        return Result.success(platformAdminService.resetPassword(userId, getCurrentUserId(session),
                (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME), getClientIp(request)));
    }

    /**
     * 解除账号的登录失败锁定
     *
     * @param userId  目标用户ID
     * @param session HTTP 会话
     * @param request HTTP 请求
     * @return 空结果
     */
    @PostMapping("/user/{userId}/unlock")
    @RequireSystemAdmin
    public Result<Void> unlockUser(@PathVariable Long userId,
                                   HttpSession session, HttpServletRequest request) {
        platformAdminService.unlockUser(userId, getCurrentUserId(session),
                (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME), getClientIp(request));
        return Result.success();
    }

    /**
     * 解析账号类型筛选，非法取值直接拒绝以免静默放宽检索范围
     */
    private UserAccountTypeEnum parseAccountType(String accountType) {
        if (accountType == null || accountType.isBlank()) {
            return UserAccountTypeEnum.ALL;
        }
        Optional<UserAccountTypeEnum> matched = UserAccountTypeEnum.findByCode(accountType);
        if (matched.isEmpty()) {
            throw new BusinessException("未知的账号类型：" + accountType.trim());
        }
        return matched.get();
    }
}
