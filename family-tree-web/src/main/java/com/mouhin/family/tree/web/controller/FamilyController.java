package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.common.dto.FamilyCreateDTO;
import com.mouhin.family.tree.common.dto.FamilyDTO;
import com.mouhin.family.tree.common.dto.FamilyJoinDTO;
import com.mouhin.family.tree.common.dto.FamilyMemberDTO;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.service.FamilyService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 家族管理控制器
 *
 * @author Family-Tree
 * @date 2026-08-03
 */
@RestController
@RequestMapping("/api/family")
public class FamilyController extends BaseController {

    private final FamilyService familyService;

    public FamilyController(FamilyService familyService) {
        this.familyService = familyService;
    }

    /**
     * 创建家族
     */
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody FamilyCreateDTO dto, HttpSession session) {
        Long userId = getCurrentUserId(session);
        Long familyId = familyService.createFamily(userId, dto);
        // 将家族ID写入会话
        session.setAttribute(com.mouhin.family.tree.common.constant.FamilyTreeConsts.SESSION_FAMILY_ID, familyId);
        Map<String, Object> data = new HashMap<>(4);
        data.put("familyId", familyId);
        return Result.success(data);
    }

    /**
     * 通过邀请码加入家族
     */
    @PostMapping("/join")
    public Result<Void> join(@RequestBody FamilyJoinDTO dto, HttpSession session) {
        Long userId = getCurrentUserId(session);
        familyService.joinFamily(userId, dto);
        // 查询家族信息并写入会话
        FamilyDTO family = familyService.getCurrentFamily(userId);
        if (family != null) {
            session.setAttribute(com.mouhin.family.tree.common.constant.FamilyTreeConsts.SESSION_FAMILY_ID, family.getId());
        }
        return Result.success();
    }

    /**
     * 获取当前家族信息
     */
    @GetMapping
    public Result<FamilyDTO> get(HttpSession session) {
        Long userId = getCurrentUserId(session);
        FamilyDTO family = familyService.getCurrentFamily(userId);
        return Result.success(family);
    }

    /**
     * 获取家族成员列表
     */
    @GetMapping("/members")
    public Result<List<FamilyMemberDTO>> members(HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        return Result.success(familyService.listMembers(familyId));
    }

    /**
     * 移除家族成员（仅族长）
     */
    @DeleteMapping("/member/{userId}")
    public Result<Void> removeMember(@PathVariable Long userId, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Long operatorUserId = getCurrentUserId(session);
        familyService.removeMember(familyId, operatorUserId, userId);
        return Result.success();
    }

    /**
     * 刷新邀请码（族长/管理员）
     */
    @PutMapping("/invite-code")
    public Result<Map<String, Object>> refreshInviteCode(HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        String newCode = familyService.refreshInviteCode(familyId, userId);
        Map<String, Object> data = new HashMap<>(4);
        data.put("inviteCode", newCode);
        return Result.success(data);
    }

    /**
     * 设置成员角色（仅族长可操作，可将成员设为管理员或取消管理员）
     */
    @PutMapping("/member/role")
    public Result<Void> setMemberRole(@RequestBody Map<String, Object> body, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Long operatorUserId = getCurrentUserId(session);
        Object userIdObj = body.get("userId");
        Object roleObj = body.get("role");
        if (userIdObj == null || roleObj == null) {
            return Result.fail(400, "参数不完整");
        }
        Long targetUserId = Long.valueOf(userIdObj.toString());
        String role = roleObj.toString();
        familyService.setMemberRole(familyId, operatorUserId, targetUserId, role);
        return Result.success();
    }

    /**
     * 切换当前激活的家族（P2-7 多家族管理）
     */
    @PutMapping("/switch/{familyId}")
    public Result<Void> switchFamily(@PathVariable Long familyId, HttpSession session) {
        Long userId = getCurrentUserId(session);
        familyService.switchFamily(userId, familyId);
        session.setAttribute(FamilyTreeConsts.SESSION_FAMILY_ID, familyId);
        return Result.success();
    }

    /**
     * 获取用户所有家族列表（P2-7 多家族管理）
     */
    @GetMapping("/my-list")
    public Result<List<FamilyDTO>> myFamilies(HttpSession session) {
        Long userId = getCurrentUserId(session);
        return Result.success(familyService.listMyFamilies(userId));
    }

    /**
     * 更新家族信息（堂号、籍贯，仅管理员可操作）
     */
    @PutMapping("/info")
    public Result<Void> updateInfo(@RequestBody Map<String, Object> body, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        String hallName = body.get("hallName") != null ? body.get("hallName").toString() : null;
        String ancestralHome = body.get("ancestralHome") != null ? body.get("ancestralHome").toString() : null;
        familyService.updateFamilyInfo(familyId, userId, hallName, ancestralHome);
        return Result.success();
    }
}
