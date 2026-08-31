package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.FamilyApplicationService;
import com.mouhin.family.tree.application.service.UserAuthApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.*;
import com.mouhin.family.tree.common.dto.UserProfileDTO;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.domain.event.OperationPerformedEvent;
import com.mouhin.family.tree.web.filter.CsrfFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final UserAuthApplicationService userService;
    private final FamilyApplicationService familyService;
    private final ApplicationEventPublisher eventPublisher;

    public AuthController(UserAuthApplicationService userService, FamilyApplicationService familyService,
                          ApplicationEventPublisher eventPublisher) {
        this.userService = userService;
        this.familyService = familyService;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterDTO dto, HttpServletRequest request) {
        Long userId = userService.register(dto);
        String ip = getClientIp(request);

        // 注册时填写了邀请码 → 自动加入对应家族
        Long familyId = null;
        if (dto.getInviteCode() != null && !dto.getInviteCode().isBlank()) {
            FamilyJoinDTO joinDTO = new FamilyJoinDTO();
            joinDTO.setInviteCode(dto.getInviteCode().trim());
            familyService.joinFamily(userId, joinDTO);
            FamilyDTO family = familyService.getCurrentFamily(userId);
            if (family != null) {
                familyId = family.getId();
            }
        }

        eventPublisher.publishEvent(OperationPerformedEvent.of(userId, dto.getUsername().trim(), "REGISTER",
                "用户注册" + (familyId != null ? "并加入家族" : ""), "user", userId, familyId, ip));

        Map<String, Object> data = new HashMap<>(4);
        data.put("userId", userId);
        return Result.success(data);
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        String ip = getClientIp(request);
        String username = dto.getUsername() != null ? dto.getUsername().trim() : "";

        Long userId;
        try {
            userId = userService.login(dto);
        } catch (BusinessException e) {
            // 登录失败也记录日志
            eventPublisher.publishEvent(OperationPerformedEvent.of(null, username, "LOGIN_FAIL",
                    "登录失败: " + e.getMessage(), "user", null, null, ip));
            throw e;
        }

        String nickname = userService.getNickname(userId);

        // 重建会话，防止会话固定攻击（Session Fixation）
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(FamilyTreeConsts.SESSION_USER_ID, userId);
        session.setAttribute(FamilyTreeConsts.SESSION_USERNAME, nickname);

        // 解析用户所属家族，写入会话
        FamilyDTO family = familyService.getCurrentFamily(userId);
        if (family != null) {
            session.setAttribute(FamilyTreeConsts.SESSION_FAMILY_ID, family.getId());
        }

        eventPublisher.publishEvent(OperationPerformedEvent.of(userId, username, "LOGIN", "用户登录",
                "user", userId, family != null ? family.getId() : null, ip));

        // 生成 CSRF Token 并写入响应
        String csrfToken = CsrfFilter.generateToken(session);

        Map<String, Object> data = new HashMap<>(8);
        data.put("userId", userId);
        data.put("nickname", nickname);
        data.put("csrfToken", csrfToken);
        data.put("hasFamily", family != null);
        if (family != null) {
            data.put("familyId", family.getId());
            data.put("familyName", family.getName());
            data.put("familyRole", family.getCurrentRole());
        }
        return Result.success(data);
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpSession session, HttpServletRequest request) {
        Long userId = (Long) session.getAttribute(FamilyTreeConsts.SESSION_USER_ID);
        String username = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
        Long familyId = (Long) session.getAttribute(FamilyTreeConsts.SESSION_FAMILY_ID);
        String ip = getClientIp(request);

        session.invalidate();

        eventPublisher.publishEvent(OperationPerformedEvent.of(userId, username, "LOGOUT", "用户登出",
                "user", userId, familyId, ip));
        return Result.success();
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me(HttpSession session) {
        Long userId = (Long) session.getAttribute(FamilyTreeConsts.SESSION_USER_ID);
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        // 每次请求刷新 CSRF Token，保持 token 新鲜（防止服务端重启后 token 失效）
        String csrfToken = CsrfFilter.generateToken(session);

        // 一次查询获取用户所有信息
        UserProfileDTO profile = userService.getUserProfile(userId);

        Map<String, Object> data = new HashMap<>(8);
        data.put("userId", userId);
        data.put("nickname", profile != null ? profile.getNickname() : null);
        data.put("generation", profile != null ? profile.getGeneration() : null);
        data.put("birthDate", profile != null ? profile.getBirthDate() : null);
        data.put("nodeId", profile != null ? profile.getNodeId() : null);
        data.put("csrfToken", csrfToken);

        // 返回家族信息
        FamilyDTO family = familyService.getCurrentFamily(userId);
        data.put("hasFamily", family != null);
        if (family != null) {
            data.put("familyId", family.getId());
            data.put("familyName", family.getName());
            data.put("familyRole", family.getCurrentRole());
            data.put("inviteCode", family.getInviteCode());
        }
        return Result.success(data);
    }

    /**
     * 更新当前登录用户的个人信息（昵称、出生日期、辈分）
     */
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody ProfileUpdateDTO dto, HttpSession session,
                                      HttpServletRequest request) {
        Long userId = (Long) session.getAttribute(FamilyTreeConsts.SESSION_USER_ID);
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        String username = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
        Long familyId = (Long) session.getAttribute(FamilyTreeConsts.SESSION_FAMILY_ID);

        userService.updateProfile(userId, dto);

        // 昵称变更时同步更新会话
        if (dto.getNickname() != null && !dto.getNickname().trim().isEmpty()) {
            session.setAttribute(FamilyTreeConsts.SESSION_USERNAME, dto.getNickname().trim());
        }

        eventPublisher.publishEvent(OperationPerformedEvent.of(userId, username, "PROFILE_UPDATE",
                "更新个人信息", "user", userId, familyId, getClientIp(request)));
        return Result.success();
    }

    /**
     * 标记当前用户在族谱中的位置（关联节点ID）
     */
    @PutMapping("/my-node")
    public Result<Void> updateMyNodeId(@RequestBody Map<String, Long> body, HttpSession session,
                                       HttpServletRequest request) {
        Long userId = (Long) session.getAttribute(FamilyTreeConsts.SESSION_USER_ID);
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        String username = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
        Long familyId = (Long) session.getAttribute(FamilyTreeConsts.SESSION_FAMILY_ID);
        Long nodeId = body.get("nodeId");
        userService.updateNodeId(userId, nodeId);

        eventPublisher.publishEvent(OperationPerformedEvent.of(userId, username, "MARK_SELF",
                nodeId != null ? "标记自己为节点" + nodeId : "取消节点标记",
                "node", nodeId, familyId, getClientIp(request)));
        return Result.success();
    }
}
