package com.mouhin.family.tree.web.controller.admin;

import com.mouhin.family.tree.application.service.DataInspectionApplicationService;
import com.mouhin.family.tree.common.dto.InspectionReportVO;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.web.controller.BaseController;
import com.mouhin.family.tree.web.security.RequireSystemAdmin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台数据巡检控制器：向系统管理员提供只读的异常数据体检报告。
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@RestController
@RequestMapping("/api/admin")
public class AdminInspectionController extends BaseController {

    private final DataInspectionApplicationService dataInspectionService;

    public AdminInspectionController(DataInspectionApplicationService dataInspectionService) {
        this.dataInspectionService = dataInspectionService;
    }

    /**
     * 生成全平台数据巡检报告
     *
     * @return 巡检报告，含各巡检项的异常总数与样本
     */
    @GetMapping("/inspection")
    @RequireSystemAdmin
    public Result<InspectionReportVO> inspectionReport() {
        return Result.success(dataInspectionService.inspectAll());
    }
}
