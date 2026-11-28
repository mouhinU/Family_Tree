package com.mouhin.family.tree.web.controller.admin;

import com.mouhin.family.tree.application.service.PlatformSettingApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.PlatformSettingSaveDTO;
import com.mouhin.family.tree.common.dto.PlatformSettingVO;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.web.controller.BaseController;
import com.mouhin.family.tree.web.security.RequireSystemAdmin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台（系统）管理员配置控制器：全站默认值与平台独有开关的读写。
 *
 * <p>平台配置是全站生效链的最高默认层：家族未覆盖时取此处的值，因此写入影响面大于家族级配置，
 * 仅白名单账号可操作并全部留痕。</p>
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@RestController
@RequestMapping("/api/admin")
public class AdminSettingController extends BaseController {

    private final PlatformSettingApplicationService platformSettingService;

    public AdminSettingController(PlatformSettingApplicationService platformSettingService) {
        this.platformSettingService = platformSettingService;
    }

    /**
     * 查询全部平台级配置项及其生效值
     *
     * @return 配置项列表
     */
    @GetMapping("/platform-settings")
    @RequireSystemAdmin
    public Result<List<PlatformSettingVO>> listPlatformSettings() {
        return Result.success(platformSettingService.listPlatformSettings());
    }

    /**
     * 保存平台级配置覆盖值
     *
     * @param dto     配置提交对象
     * @param session HTTP 会话
     * @param request HTTP 请求
     * @return 空结果
     */
    @PutMapping("/platform-settings")
    @RequireSystemAdmin
    public Result<Void> savePlatformSettings(@RequestBody PlatformSettingSaveDTO dto,
                                             HttpSession session, HttpServletRequest request) {
        Long operatorId = getCurrentUserId(session);
        String operatorName = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
        platformSettingService.savePlatformSettings(dto, operatorId, operatorName, getClientIp(request));
        return Result.success();
    }
}
