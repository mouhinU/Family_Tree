package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.FamilyNodeApplicationService;
import com.mouhin.family.tree.application.service.FamilyRelationApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.FamilyNodeDTO;
import com.mouhin.family.tree.common.dto.FamilyRelationDTO;
import com.mouhin.family.tree.common.dto.PageResult;
import com.mouhin.family.tree.common.result.Result;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
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

    private final FamilyNodeApplicationService familyNodeService;
    private final FamilyRelationApplicationService familyRelationService;

    public TimelineController(FamilyNodeApplicationService familyNodeService,
                              FamilyRelationApplicationService familyRelationService) {
        this.familyNodeService = familyNodeService;
        this.familyRelationService = familyRelationService;
    }

    /**
     * 获取家族时间线事件列表（分页）
     *
     * @param year  指定年份（可选，不传则返回全部年份的事件）
     * @param page  当前页码，默认1
     * @param size  每页大小，默认50
     * @param session HTTP会话
     * @return 分页后的时间线事件
     */
    @GetMapping
    public Result<PageResult<Map<String, Object>>> getTimeline(
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "" + FamilyTreeConsts.DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = "" + FamilyTreeConsts.DEFAULT_PAGE_SIZE) int size,
            HttpSession session) {
        Long familyId = getCurrentFamilyId(session);

        List<FamilyNodeDTO> nodes = familyNodeService.listNodes(familyId);

        // 构建节点名映射
        Map<Long, String> nodeNameMap = new HashMap<>((int) (nodes.size() / 0.75) + 1);
        for (FamilyNodeDTO node : nodes) {
            nodeNameMap.put(node.getId(), node.getName());
        }

        List<Map<String, Object>> events = new ArrayList<>();

        for (FamilyNodeDTO node : nodes) {
            if (node.getBirthDate() != null && !node.getBirthDate().isBlank()) {
                int birthYear = parseYear(node.getBirthDate());
                if (birthYear > 0 && (year == null || birthYear == year)) {
                    Map<String, Object> event = new HashMap<>(8);
                    event.put("date", node.getBirthDate());
                    event.put("type", "BIRTH");
                    event.put("description", node.getName() + " 出生");
                    event.put("nodeId", node.getId());
                    events.add(event);
                }
            }
            if (node.getDeathDate() != null && !node.getDeathDate().isBlank()) {
                int deathYear = parseYear(node.getDeathDate());
                if (deathYear > 0 && (year == null || deathYear == year)) {
                    Map<String, Object> event = new HashMap<>(8);
                    event.put("date", node.getDeathDate());
                    event.put("type", "DEATH");
                    event.put("description", node.getName() + " 去世");
                    event.put("nodeId", node.getId());
                    events.add(event);
                }
            }
        }

        // 婚姻关系事件（仅 SPOUSE 类型，relationType=2）
        List<FamilyRelationDTO> allRelations = familyRelationService.listAllRelations(familyId);
        for (FamilyRelationDTO rel : allRelations) {
            if (rel.getRelationType() != null && rel.getRelationType() == 2
                    && rel.getMarriageDate() != null) {
                int marriageYear = rel.getMarriageDate().getYear();
                if (year == null || marriageYear == year) {
                    String fromName = nodeNameMap.getOrDefault(rel.getFromNodeId(), "未知");
                    String toName = nodeNameMap.getOrDefault(rel.getToNodeId(), "未知");
                    Map<String, Object> event = new HashMap<>(8);
                    event.put("date", rel.getMarriageDate().toString());
                    event.put("type", "MARRIAGE");
                    event.put("description", fromName + " 与 " + toName + " 结婚");
                    event.put("relationId", rel.getId());
                    events.add(event);
                }
            }
        }

        // 按日期排序
        events.sort(Comparator.comparing(e -> (String) e.get("date")));

        // 分页处理
        long total = events.size();
        int fromIndex = (page - 1) * size;
        List<Map<String, Object>> pageRecords;
        if (fromIndex >= total) {
            pageRecords = new ArrayList<>();
        } else {
            int toIndex = (int) Math.min(fromIndex + size, total);
            pageRecords = new ArrayList<>(events.subList(fromIndex, toIndex));
        }

        logger.info("Timeline query: familyId={}, year={}, page={}, size={}, totalEvents={}",
                familyId, year, page, size, total);

        PageResult<Map<String, Object>> pageResult = new PageResult<>(pageRecords, total, page, size);
        return Result.success(pageResult);
    }

    /**
     * 从日期字符串中解析年份（支持 yyyy-MM-dd 格式）
     *
     * @param dateStr 日期字符串
     * @return 年份，解析失败返回 -1
     */
    private int parseYear(String dateStr) {
        if (dateStr != null && dateStr.length() >= 4) {
            try {
                return Integer.parseInt(dateStr.substring(0, 4));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }
}
