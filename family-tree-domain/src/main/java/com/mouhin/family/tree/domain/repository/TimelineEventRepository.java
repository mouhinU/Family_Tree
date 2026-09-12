package com.mouhin.family.tree.domain.repository;

import com.mouhin.family.tree.domain.entity.TimelineEvent;

import java.util.List;

/**
 * 时间线事件仓储接口
 *
 * @author Family-Tree
 * @date 2026-09-12
 */
public interface TimelineEventRepository {

    /**
     * 保存时间线事件
     *
     * @param event 时间线事件
     */
    void save(TimelineEvent event);

    /**
     * 根据来源类型、来源ID和事件类型查询（用于去重判断）
     *
     * @param sourceType 来源类型
     * @param sourceId   来源ID
     * @param eventType  事件类型
     * @return 时间线事件，不存在返回 null
     */
    TimelineEvent findBySourceAndType(String sourceType, Long sourceId, String eventType);

    /**
     * 更新时间线事件
     *
     * @param event 时间线事件
     */
    void update(TimelineEvent event);

    /**
     * 根据ID删除时间线事件
     *
     * @param id 事件ID
     */
    void removeById(Long id);

    /**
     * 按家族ID查询时间线事件列表（按事件日期降序）
     *
     * @param familyId 家族ID
     * @return 时间线事件列表
     */
    List<TimelineEvent> listByFamilyId(Long familyId);

    /**
     * 按家族ID和事件类型查询时间线事件列表
     *
     * @param familyId  家族ID
     * @param eventType 事件类型（可选，null 表示查全部）
     * @return 时间线事件列表
     */
    List<TimelineEvent> listByFamilyIdAndEventType(Long familyId, String eventType);
}
