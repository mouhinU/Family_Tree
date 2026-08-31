package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.BirthdayReminderApplicationService;
import com.mouhin.family.tree.common.dto.BirthdayReminderVO;
import com.mouhin.family.tree.common.result.Result;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 生日提醒控制器
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@RestController
@RequestMapping("/api/birthday")
public class BirthdayReminderController extends BaseController {

    private final BirthdayReminderApplicationService birthdayReminderApplicationService;

    public BirthdayReminderController(BirthdayReminderApplicationService birthdayReminderApplicationService) {
        this.birthdayReminderApplicationService = birthdayReminderApplicationService;
    }

    /**
     * 获取未来 N 天内过生日的在世成员列表
     *
     * @param days 提前天数（默认30天）
     */
    @GetMapping
    public Result<List<BirthdayReminderVO>> upcoming(@RequestParam(defaultValue = "30") int days,
                                                     HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        return Result.success(birthdayReminderApplicationService.getUpcoming(familyId, days));
    }
}
