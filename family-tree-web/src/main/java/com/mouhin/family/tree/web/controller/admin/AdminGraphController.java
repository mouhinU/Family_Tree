package com.mouhin.family.tree.web.controller.admin;

import com.mouhin.family.tree.application.service.AdminGraphApplicationService;
import com.mouhin.family.tree.common.dto.AdminGraphDetailVO;
import com.mouhin.family.tree.common.dto.AdminGraphNetworkVO;
import com.mouhin.family.tree.common.dto.AdminGraphOverviewVO;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.web.controller.BaseController;
import com.mouhin.family.tree.web.security.RequireSystemAdmin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台跨族图谱控制器：向系统管理员提供只读的跨族链接治理视图。
 *
 * @author Family-Tree
 * @date 2026-11-26
 */
@RestController
@RequestMapping("/api/admin/graph")
public class AdminGraphController extends BaseController {

    private final AdminGraphApplicationService adminGraphService;

    public AdminGraphController(AdminGraphApplicationService adminGraphService) {
        this.adminGraphService = adminGraphService;
    }

    /**
     * 获取家族级跨族图谱总览
     *
     * @return 家族节点、聚合边与链接统计
     */
    @GetMapping
    @RequireSystemAdmin
    public Result<AdminGraphOverviewVO> overview() {
        return Result.success(adminGraphService.getOverview());
    }

    /**
     * 获取指定家族的跨族链接下钻明细
     *
     * @param familyId 家族ID
     * @return 家族摘要、端点节点与链接明细
     */
    @GetMapping("/families/{familyId}")
    @RequireSystemAdmin
    public Result<AdminGraphDetailVO> familyDetail(@PathVariable Long familyId) {
        return Result.success(adminGraphService.getFamilyDetail(familyId));
    }

    /**
     * 获取指定家族的展示型图谱网络
     *
     * @param familyId 家族ID
     * @return 族内节点、族内关系与跨族链接
     */
    @GetMapping("/families/{familyId}/network")
    @RequireSystemAdmin
    public Result<AdminGraphNetworkVO> familyNetwork(@PathVariable Long familyId) {
        return Result.success(adminGraphService.getFamilyNetwork(familyId));
    }
}
