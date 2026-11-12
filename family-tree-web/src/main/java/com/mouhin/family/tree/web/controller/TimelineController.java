package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.TimelineApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.PageResult;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.domain.entity.TimelineEvent;
import com.mouhin.family.tree.web.security.RequireFamilyManager;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 家族动态时间线控制器
 *
 * @author Family-Tree
 * @date 2026-08-09
 */
@RestController
@RequestMapping("/api/timeline")
public class TimelineController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(TimelineController.class);

    private final TimelineApplicationService timelineApplicationService;

    public TimelineController(TimelineApplicationService timelineApplicationService) {
        this.timelineApplicationService = timelineApplicationService;
    }

    /**
     * 获取家族时间线事件列表（分页）
     *
     * @param year      指定年份（可选，不传则返回全部年份的事件）
     * @param eventType 事件类型过滤（可选：BIRTH/DEATH/MARRIAGE/ACTIVITY）
     * @param page      当前页码，默认1
     * @param size      每页大小，默认50
     * @param session   HTTP会话
     * @return 分页后的时间线事件
     */
    @GetMapping
    public Result<PageResult<Map<String, Object>>> getTimeline(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "" + FamilyTreeConsts.DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = "" + FamilyTreeConsts.DEFAULT_PAGE_SIZE) int size,
            HttpSession session) {
        Long familyId = getCurrentFamilyId(session);

        List<TimelineEvent> events;
        if (eventType != null && !eventType.isBlank()) {
            events = timelineApplicationService.listEvents(familyId, eventType);
        } else {
            events = timelineApplicationService.listEventsByYear(familyId, year);
        }

        // 年份过滤（当按 eventType 查询时仍需过滤年份）
        if (year != null && eventType != null && !eventType.isBlank()) {
            events = events.stream()
                    .filter(e -> e.getEventDate() != null && e.getEventDate().getYear() == year)
                    .toList();
        }

        // 转换为前端格式
        List<Map<String, Object>> eventMaps = new ArrayList<>(events.size());
        for (TimelineEvent event : events) {
            Map<String, Object> map = new HashMap<>(8);
            map.put("date", event.getEventDate() != null ? event.getEventDate().toString() : "");
            map.put("type", event.getEventType());
            map.put("description", event.getDescription());
            map.put("nodeId", event.getNodeId());
            map.put("relationId", event.getRelationId());
            map.put("isNew", event.getIsNew());
            map.put("id", event.getId());
            eventMaps.add(map);
        }

        // 分页处理
        long total = eventMaps.size();
        int fromIndex = (page - 1) * size;
        List<Map<String, Object>> pageRecords;
        if (fromIndex >= total) {
            pageRecords = new ArrayList<>();
        } else {
            int toIndex = (int) Math.min(fromIndex + size, total);
            pageRecords = new ArrayList<>(eventMaps.subList(fromIndex, toIndex));
        }

        logger.info("Timeline query: familyId={}, year={}, eventType={}, page={}, size={}, totalEvents={}",
                familyId, year, eventType, page, size, total);

        PageResult<Map<String, Object>> pageResult = new PageResult<>(pageRecords, total, page, size);
        return Result.success(pageResult);
    }

    /**
     * 存量数据回填（将现有节点/关系/活动的日期数据写入时间线表）
     *
     * @param session HTTP会话
     * @return 回填的事件数量
     */
    @PostMapping("/backfill")
    @RequireFamilyManager
    public Result<Integer> backfill(HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        int count = timelineApplicationService.backfill(familyId);
        logger.info("Timeline backfill: familyId={}, count={}", familyId, count);
        return Result.success(count);
    }
}
