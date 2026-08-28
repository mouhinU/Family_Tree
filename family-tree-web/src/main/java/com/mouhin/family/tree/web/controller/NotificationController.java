package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.common.dto.NotificationVO;
import com.mouhin.family.tree.common.dto.PageResult;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.domain.entity.Notification;
import com.mouhin.family.tree.domain.repository.NotificationRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 通知管理控制器
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
@RestController
@RequestMapping("/api/notification")
public class NotificationController extends BaseController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * 分页查询当前用户的通知列表
     */
    @GetMapping("/list")
    public Result<PageResult<NotificationVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpSession session) {
        Long userId = getCurrentUserId(session);
        int offset = (page - 1) * size;
        List<Notification> notifications = notificationRepository.findByUserId(userId, offset, size);
        List<NotificationVO> voList = notifications.stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        long total = notificationRepository.countByUserId(userId);
        return Result.success(new PageResult<>(voList, total, page, size));
    }

    /**
     * 获取当前用户未读通知数量
     */
    @GetMapping("/unread-count")
    public Result<Long> unreadCount(HttpSession session) {
        Long userId = getCurrentUserId(session);
        long count = notificationRepository.countUnreadByUserId(userId);
        return Result.success(count);
    }

    /**
     * 标记单条通知为已读
     */
    @PostMapping("/{id}/read")
    public Result<Void> markAsRead(@PathVariable Long id) {
        notificationRepository.markAsRead(id);
        return Result.success(null);
    }

    /**
     * 标记当前用户所有通知为已读
     */
    @PostMapping("/read-all")
    public Result<Void> markAllAsRead(HttpSession session) {
        Long userId = getCurrentUserId(session);
        notificationRepository.markAllAsRead(userId);
        return Result.success(null);
    }

    private NotificationVO toVO(Notification n) {
        NotificationVO vo = new NotificationVO();
        vo.setId(n.getId());
        vo.setFamilyId(n.getFamilyId());
        vo.setUserId(n.getUserId());
        vo.setTitle(n.getTitle());
        vo.setContent(n.getContent());
        vo.setNotificationType(n.getNotificationType());
        vo.setRelatedId(n.getRelatedId());
        vo.setRead(n.getRead());
        vo.setCreateTime(n.getCreateTime());
        vo.setTimeAgo(formatTimeAgo(n.getCreateTime()));
        return vo;
    }

    private String formatTimeAgo(LocalDateTime time) {
        if (time == null) {
            return "";
        }
        Duration duration = Duration.between(time, LocalDateTime.now());
        long minutes = duration.toMinutes();
        if (minutes < 1) {
            return "刚刚";
        }
        if (minutes < 60) {
            return minutes + "分钟前";
        }
        long hours = duration.toHours();
        if (hours < 24) {
            return hours + "小时前";
        }
        long days = duration.toDays();
        if (days < 30) {
            return days + "天前";
        }
        return time.toLocalDate().toString();
    }
}
