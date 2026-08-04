package com.mouhin.family.tree.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.persistence.entity.FamilyNodeDO;
import com.mouhin.family.tree.persistence.entity.FamilyRelationDO;
import com.mouhin.family.tree.persistence.mapper.FamilyNodeMapper;
import com.mouhin.family.tree.persistence.mapper.FamilyRelationMapper;
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
 * @date 2026-08-04
 */
@RestController
@RequestMapping("/api/timeline")
public class TimelineController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(TimelineController.class);

    private final FamilyNodeMapper familyNodeMapper;
    private final FamilyRelationMapper familyRelationMapper;

    public TimelineController(FamilyNodeMapper familyNodeMapper,
                              FamilyRelationMapper familyRelationMapper) {
        this.familyNodeMapper = familyNodeMapper;
        this.familyRelationMapper = familyRelationMapper;
    }

    /**
     * 获取家族时间线事件列表
     *
     * @param year 指定年份（可选，不传则返回全部）
     */
    @GetMapping
    public Result<List<Map<String, Object>>> getTimeline(
            @RequestParam(required = false) Integer year,
            HttpSession session) {
        Long familyId = getCurrentFamilyId(session);

        LambdaQueryWrapper<FamilyNodeDO> nodeQuery = new LambdaQueryWrapper<>();
        nodeQuery.eq(FamilyNodeDO::getFamilyId, familyId);
        List<FamilyNodeDO> nodes = familyNodeMapper.selectList(nodeQuery);

        // 构建节点名映射
        Map<Long, String> nodeNameMap = new HashMap<>();
        for (FamilyNodeDO node : nodes) {
            nodeNameMap.put(node.getId(), node.getName());
        }

        List<Map<String, Object>> events = new ArrayList<>();

        for (FamilyNodeDO node : nodes) {
            if (node.getBirthDate() != null) {
                if (year == null || node.getBirthDate().getYear() == year) {
                    Map<String, Object> event = new HashMap<>();
                    event.put("date", node.getBirthDate().toString());
                    event.put("type", "BIRTH");
                    event.put("description", node.getName() + " 出生");
                    event.put("nodeId", node.getId());
                    events.add(event);
                }
            }
            if (node.getDeathDate() != null) {
                if (year == null || node.getDeathDate().getYear() == year) {
                    Map<String, Object> event = new HashMap<>();
                    event.put("date", node.getDeathDate().toString());
                    event.put("type", "DEATH");
                    event.put("description", node.getName() + " 去世");
                    event.put("nodeId", node.getId());
                    events.add(event);
                }
            }
        }

        // 婚姻关系事件（仅 SPOUSE 类型，relationType=2）
        LambdaQueryWrapper<FamilyRelationDO> relQuery = new LambdaQueryWrapper<>();
        relQuery.eq(FamilyRelationDO::getFamilyId, familyId)
                .eq(FamilyRelationDO::getRelationType, 2)
                .isNotNull(FamilyRelationDO::getMarriageDate);
        List<FamilyRelationDO> relations = familyRelationMapper.selectList(relQuery);

        for (FamilyRelationDO rel : relations) {
            if (year == null || rel.getMarriageDate().getYear() == year) {
                String fromName = nodeNameMap.getOrDefault(rel.getFromNodeId(), "未知");
                String toName = nodeNameMap.getOrDefault(rel.getToNodeId(), "未知");
                Map<String, Object> event = new HashMap<>();
                event.put("date", rel.getMarriageDate().toString());
                event.put("type", "MARRIAGE");
                event.put("description", fromName + " 与 " + toName + " 结婚");
                event.put("relationId", rel.getId());
                events.add(event);
            }
        }

        // 按日期排序
        events.sort(Comparator.comparing(e -> (String) e.get("date")));

        return Result.success(events);
    }
}
