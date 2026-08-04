package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.service.DeathAnniversaryService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 忌日提醒控制器
 *
 * @author Family-Tree
 * @date 2026-08-04
 */
@RestController
@RequestMapping("/api/death-anniversary")
public class DeathAnniversaryController extends BaseController {

    private final DeathAnniversaryService deathAnniversaryService;

    public DeathAnniversaryController(DeathAnniversaryService deathAnniversaryService) {
        this.deathAnniversaryService = deathAnniversaryService;
    }

    /**
     * 获取未来 N 天内的忌日提醒列表
     *
     * @param days 提前天数（默认30天）
     */
    @GetMapping
    public Result<?> upcoming(@RequestParam(defaultValue = "30") int days,
                              HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        return Result.success(deathAnniversaryService.getUpcoming(familyId, days));
    }
}
