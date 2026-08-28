package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.FamilyApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.FamilyCreateDTO;
import com.mouhin.family.tree.common.dto.FamilyDTO;
import com.mouhin.family.tree.common.dto.FamilyInfoUpdateDTO;
import com.mouhin.family.tree.common.dto.FamilyJoinDTO;
import com.mouhin.family.tree.common.dto.FamilyMemberDTO;
import com.mouhin.family.tree.common.dto.MemberRoleUpdateDTO;
import com.mouhin.family.tree.common.result.Result;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.mouhin.family.tree.common.constant.FamilyTreeConsts.SESSION_FAMILY_ID;

/**
 * 家族管理控制器
 *
 * @author Family-Tree
 * @date 2026-08-03
 */
@RestController
@RequestMapping("/api/family")
public class FamilyController extends BaseController {

    private final FamilyApplicationService familyService;

    public FamilyController(FamilyApplicationService familyService) {
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
        session.setAttribute(SESSION_FAMILY_ID, familyId);
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
            session.setAttribute(SESSION_FAMILY_ID, family.getId());
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
    public Result<Void> setMemberRole(@Valid @RequestBody MemberRoleUpdateDTO dto, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Long operatorUserId = getCurrentUserId(session);
        if (dto.getUserId() == null || dto.getRole() == null) {
            return Result.fail(400, "参数不完整");
        }
        familyService.setMemberRole(familyId, operatorUserId, dto.getUserId(), dto.getRole());
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
    public Result<Void> updateInfo(@Valid @RequestBody FamilyInfoUpdateDTO dto, HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        familyService.updateFamilyInfo(familyId, userId, dto.getHallName(), dto.getAncestralHome());
        return Result.success();
    }
}
