package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.FamilyEventApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.EventDTO;
import com.mouhin.family.tree.common.dto.EventSignupVO;
import com.mouhin.family.tree.common.dto.EventVO;
import com.mouhin.family.tree.common.result.Result;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 家族活动组织控制器
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@RestController
@RequestMapping("/api/event")
public class EventController extends BaseController {

    private final FamilyEventApplicationService familyEventApplicationService;

    public EventController(FamilyEventApplicationService familyEventApplicationService) {
        this.familyEventApplicationService = familyEventApplicationService;
    }

    /**
     * 查询家族活动列表
     */
    @GetMapping
    public Result<List<EventVO>> listEvents(HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        return Result.success(familyEventApplicationService.listEvents(familyId, userId));
    }

    /**
     * 发起活动
     */
    @PostMapping
    public Result<Map<String, Object>> createEvent(@Valid @RequestBody EventDTO dto, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        String username = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
        Long eventId = familyEventApplicationService.createEvent(familyId, userId, username, dto);
        Map<String, Object> data = new HashMap<>(4);
        data.put("id", eventId);
        return Result.success(data);
    }

    /**
     * 查询活动详情（含报名明细与人均费用）
     */
    @GetMapping("/{id}")
    public Result<EventVO> getEvent(@PathVariable Long id, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        return Result.success(familyEventApplicationService.getEvent(familyId, id, userId));
    }

    /**
     * 删除活动（仅发起人）
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteEvent(@PathVariable Long id, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        familyEventApplicationService.deleteEvent(familyId, id, getCurrentUserId(session));
        return Result.success();
    }

    /**
     * 报名活动（可携带随行人数与备注）
     */
    @PostMapping("/{id}/signup")
    public Result<EventSignupVO> signup(@PathVariable Long id,
                                        @RequestBody(required = false) EventSignupVO signup,
                                        HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        String username = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
        return Result.success(familyEventApplicationService.signup(familyId, id, userId, username, signup));
    }

    /**
     * 取消报名
     */
    @DeleteMapping("/{id}/signup")
    public Result<Void> cancelSignup(@PathVariable Long id, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        familyEventApplicationService.cancelSignup(familyId, id, getCurrentUserId(session));
        return Result.success();
    }

    /**
     * 截止报名（仅发起人）
     */
    @PostMapping("/{id}/close")
    public Result<Void> closeEvent(@PathVariable Long id, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        familyEventApplicationService.updateStatus(familyId, id, getCurrentUserId(session), false);
        return Result.success();
    }

    /**
     * 重新开放报名（仅发起人）
     */
    @PostMapping("/{id}/open")
    public Result<Void> openEvent(@PathVariable Long id, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        familyEventApplicationService.updateStatus(familyId, id, getCurrentUserId(session), true);
        return Result.success();
    }
}
