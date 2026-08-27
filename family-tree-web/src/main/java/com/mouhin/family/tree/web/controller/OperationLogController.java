package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.FamilyApplicationService;
import com.mouhin.family.tree.application.service.OperationLogApplicationService;
import com.mouhin.family.tree.common.dto.FamilyDTO;
import com.mouhin.family.tree.common.dto.OperationLogDTO;
import com.mouhin.family.tree.common.dto.PageResult;
import com.mouhin.family.tree.common.enums.FamilyMemberRoleEnum;
import com.mouhin.family.tree.common.result.Result;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志控制器（族长/管理员可查询）
 *
 * @author Family-Tree
 * @date 2026-08-03
 */
@RestController
@RequestMapping("/api/operation-log")
public class OperationLogController extends BaseController {

    private final OperationLogApplicationService operationLogService;
    private final FamilyApplicationService familyService;

    public OperationLogController(OperationLogApplicationService operationLogService,
                                  FamilyApplicationService familyService) {
        this.operationLogService = operationLogService;
        this.familyService = familyService;
    }

    /**
     * 分页查询操作日志（族长/管理员可访问）
     *
     * @param operationType 操作类型筛选（可选）
     * @param page          页码，默认 1
     * @param size          每页大小，默认 20
     */
    @GetMapping
    public Result<PageResult<OperationLogDTO>> list(
            @RequestParam(required = false) String operationType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);

        // 校验权限：仅族长和管理员可查询
        FamilyDTO family = familyService.getCurrentFamily(userId);
        if (family == null) {
            return Result.fail(403, "请先加入家族");
        }
        String role = family.getCurrentRole();
        if (!FamilyMemberRoleEnum.OWNER.getCode().equals(role)
                && !FamilyMemberRoleEnum.ADMIN.getCode().equals(role)) {
            return Result.fail(403, "仅管理员可查看操作日志");
        }

        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 100) {
            size = 20;
        }

        PageResult<OperationLogDTO> result = operationLogService.listLogs(familyId, operationType, page, size);
        return Result.success(result);
    }
}
