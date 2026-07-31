package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.LoginDTO;
import com.mouhin.family.tree.common.dto.RegisterDTO;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody RegisterDTO dto) {
        Long userId = userService.register(dto);
        Map<String, Object> data = new HashMap<>(4);
        data.put("userId", userId);
        return Result.success(data);
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginDTO dto, HttpServletRequest request) {
        Long userId = userService.login(dto);
        String nickname = userService.getNickname(userId);

        // 重建会话，防止会话固定攻击（Session Fixation）
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(FamilyTreeConsts.SESSION_USER_ID, userId);
        session.setAttribute(FamilyTreeConsts.SESSION_USERNAME, nickname);

        Map<String, Object> data = new HashMap<>(4);
        data.put("userId", userId);
        data.put("nickname", nickname);
        return Result.success(data);
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpSession session) {
        session.invalidate();
        return Result.success();
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me(HttpSession session) {
        Long userId = (Long) session.getAttribute(FamilyTreeConsts.SESSION_USER_ID);
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        Map<String, Object> data = new HashMap<>(4);
        data.put("userId", userId);
        data.put("nickname", session.getAttribute(FamilyTreeConsts.SESSION_USERNAME));
        data.put("generation", userService.getGeneration(userId));
        return Result.success(data);
    }
}
