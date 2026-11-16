package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.PlatformAdminApplicationService;
import com.mouhin.family.tree.common.dto.PageResult;
import com.mouhin.family.tree.common.dto.PlatformOperationLogVO;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.web.security.RequireSystemAdmin;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 平台（系统）管理员操作日志控制器：跨家族的日志检索。
 *
 * <p>检索是只读能力，不改动任何留痕；家族维度可选，不传即查全平台。
 * 日期区间按整天语义解释：起始日取当天 00:00:00，截止日取当天 23:59:59，两端都包含。</p>
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@RestController
@RequestMapping("/api/admin")
public class AdminLogController extends BaseController {

    /**
     * 截止日的收尾时刻，留痕时间列以秒存储，取 23:59:59 即可覆盖整天
     */
    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

    private final PlatformAdminApplicationService platformAdminService;

    public AdminLogController(PlatformAdminApplicationService platformAdminService) {
        this.platformAdminService = platformAdminService;
    }

    /**
     * 分页检索平台操作日志
     *
     * @param familyId      家族ID，不传表示全部家族
     * @param operationType 操作类型编码，不传表示全部
     * @param keyword       用户名 / 操作描述前缀
     * @param startDate     起始日期，格式 yyyy-MM-dd
     * @param endDate       截止日期，格式 yyyy-MM-dd
     * @param page          页码，从 1 开始
     * @param size          每页大小
     * @return 日志分页结果
     */
    @GetMapping("/logs")
    @RequireSystemAdmin
    public Result<PageResult<PlatformOperationLogVO>> listLogs(
            @RequestParam(required = false) Long familyId,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(platformAdminService.listOperationLogs(familyId, operationType,
                normalizeSearchKeyword(keyword), toStartOfDay(startDate), toEndOfDay(endDate), page, size));
    }

    /**
     * 列出日志中实际出现过的操作类型，供检索表单填充下拉候选
     *
     * @return 去重并字典序排列的操作类型编码
     */
    @GetMapping("/log-types")
    @RequireSystemAdmin
    public Result<List<String>> listOperationTypes() {
        return Result.success(platformAdminService.listOperationTypes());
    }

    /**
     * 把起始日换算为当天起点
     */
    private LocalDateTime toStartOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    /**
     * 把截止日换算为当天末点
     */
    private LocalDateTime toEndOfDay(LocalDate date) {
        return date == null ? null : date.atTime(END_OF_DAY);
    }
}
