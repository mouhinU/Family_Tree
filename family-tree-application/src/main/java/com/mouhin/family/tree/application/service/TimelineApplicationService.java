package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.enums.TimelineEventTypeEnum;
import com.mouhin.family.tree.common.enums.TimelineSourceTypeEnum;
import com.mouhin.family.tree.domain.entity.FamilyEvent;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyRelation;
import com.mouhin.family.tree.domain.entity.TimelineEvent;
import com.mouhin.family.tree.domain.repository.FamilyEventRepository;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.FamilyRelationRepository;
import com.mouhin.family.tree.domain.repository.TimelineEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 时间线事件应用服务
 * <p>
 * 负责在数据变更时记录时间线事件，通过唯一约束 (source_type, source_id, event_type) 防止重复。
 * 新增数据时 is_new=true，修改数据时 is_new=false。
 *
 * @author Family-Tree
 * @date 2026-09-12
 */
@Service
public class TimelineApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(TimelineApplicationService.class);

    private final TimelineEventRepository timelineEventRepository;
    private final FamilyNodeRepository familyNodeRepository;
    private final FamilyRelationRepository familyRelationRepository;
    private final FamilyEventRepository familyEventRepository;

    public TimelineApplicationService(TimelineEventRepository timelineEventRepository,
                                     FamilyNodeRepository familyNodeRepository,
                                     FamilyRelationRepository familyRelationRepository,
                                     FamilyEventRepository familyEventRepository) {
        this.timelineEventRepository = timelineEventRepository;
        this.familyNodeRepository = familyNodeRepository;
        this.familyRelationRepository = familyRelationRepository;
        this.familyEventRepository = familyEventRepository;
    }

    /**
     * 记录节点出生事件
     *
     * @param familyId  家族ID
     * @param nodeId    节点ID
     * @param nodeName  节点姓名
     * @param birthDate 出生日期（yyyy-MM-dd 格式）
     * @param isNew     是否为新增节点
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordBirthEvent(Long familyId, Long nodeId, String nodeName,
                                 LocalDate birthDate, boolean isNew) {
        if (birthDate == null) {
            return;
        }
        upsertEvent(familyId, TimelineSourceTypeEnum.NODE.getCode(), nodeId,
                TimelineEventTypeEnum.BIRTH.getCode(), birthDate,
                nodeName + " 出生", nodeId, null, isNew);
    }

    /**
     * 记录节点去世事件
     *
     * @param familyId  家族ID
     * @param nodeId    节点ID
     * @param nodeName  节点姓名
     * @param deathDate 去世日期（yyyy-MM-dd 格式）
     * @param isNew     是否为新增节点
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordDeathEvent(Long familyId, Long nodeId, String nodeName,
                                 LocalDate deathDate, boolean isNew) {
        if (deathDate == null) {
            return;
        }
        upsertEvent(familyId, TimelineSourceTypeEnum.NODE.getCode(), nodeId,
                TimelineEventTypeEnum.DEATH.getCode(), deathDate,
                nodeName + " 去世", nodeId, null, isNew);
    }

    /**
     * 记录结婚事件
     *
     * @param familyId    家族ID
     * @param relationId  关系ID
     * @param fromName    关系发起方姓名
     * @param toName      关系接收方姓名
     * @param marriageDate 结婚日期
     * @param isNew       是否为新增关系
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordMarriageEvent(Long familyId, Long relationId,
                                    String fromName, String toName,
                                    LocalDate marriageDate, boolean isNew) {
        if (marriageDate == null) {
            return;
        }
        upsertEvent(familyId, TimelineSourceTypeEnum.RELATION.getCode(), relationId,
                TimelineEventTypeEnum.MARRIAGE.getCode(), marriageDate,
                fromName + " 与 " + toName + " 结婚", null, relationId, isNew);
    }

    /**
     * 记录家族活动事件
     *
     * @param familyId  家族ID
     * @param eventId   活动ID
     * @param title     活动标题
     * @param eventDate 活动日期
     * @param isNew     是否为新增活动
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordActivityEvent(Long familyId, Long eventId,
                                    String title, LocalDate eventDate, boolean isNew) {
        if (eventDate == null) {
            return;
        }
        upsertEvent(familyId, TimelineSourceTypeEnum.EVENT.getCode(), eventId,
                TimelineEventTypeEnum.ACTIVITY.getCode(), eventDate,
                "家族活动: " + title, null, null, isNew);
    }

    /**
     * 删除时间线事件（当源数据被删除时调用）
     *
     * @param sourceType 来源类型
     * @param sourceId   来源ID
     * @param eventType  事件类型
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeEvent(String sourceType, Long sourceId, String eventType) {
        TimelineEvent existing = timelineEventRepository.findBySourceAndType(sourceType, sourceId, eventType);
        if (existing != null) {
            timelineEventRepository.removeById(existing.getId());
            logger.info("Removed timeline event: sourceType={}, sourceId={}, eventType={}",
                    sourceType, sourceId, eventType);
        }
    }

    /**
     * 删除节点的所有时间线事件（出生+去世）
     *
     * @param nodeId 节点ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeNodeEvents(Long nodeId) {
        removeEvent(TimelineSourceTypeEnum.NODE.getCode(), nodeId,
                TimelineEventTypeEnum.BIRTH.getCode());
        removeEvent(TimelineSourceTypeEnum.NODE.getCode(), nodeId,
                TimelineEventTypeEnum.DEATH.getCode());
    }

    /**
     * 删除关系的时间线事件（结婚）
     *
     * @param relationId 关系ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeRelationEvents(Long relationId) {
        removeEvent(TimelineSourceTypeEnum.RELATION.getCode(), relationId,
                TimelineEventTypeEnum.MARRIAGE.getCode());
    }

    /**
     * 删除活动的时间线事件
     *
     * @param eventId 活动ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeActivityEvents(Long eventId) {
        removeEvent(TimelineSourceTypeEnum.EVENT.getCode(), eventId,
                TimelineEventTypeEnum.ACTIVITY.getCode());
    }

    /**
     * 查询家族时间线事件列表（按事件日期降序）
     *
     * @param familyId  家族ID
     * @param eventType 事件类型（可选，null 表示查全部）
     * @return 时间线事件列表
     */
    public List<TimelineEvent> listEvents(Long familyId, String eventType) {
        return timelineEventRepository.listByFamilyIdAndEventType(familyId, eventType);
    }

    /**
     * 查询家族时间线事件列表（按年份过滤）
     *
     * @param familyId 家族ID
     * @param year     年份（可选，null 表示查全部年份）
     * @return 时间线事件列表
     */
    public List<TimelineEvent> listEventsByYear(Long familyId, Integer year) {
        List<TimelineEvent> all = timelineEventRepository.listByFamilyId(familyId);
        if (year == null) {
            return all;
        }
        return all.stream()
                .filter(e -> e.getEventDate() != null && e.getEventDate().getYear() == year)
                .collect(Collectors.toList());
    }

    /**
     * Upsert 时间线事件：存在则更新，不存在则新增
     */
    private void upsertEvent(Long familyId, String sourceType, Long sourceId,
                             String eventType, LocalDate eventDate,
                             String description, Long nodeId, Long relationId,
                             boolean isNew) {
        TimelineEvent existing = timelineEventRepository.findBySourceAndType(
                sourceType, sourceId, eventType);

        if (existing != null) {
            existing.setEventDate(eventDate);
            existing.setDescription(description);
            existing.setIsNew(false);
            existing.setNodeId(nodeId);
            existing.setRelationId(relationId);
            timelineEventRepository.update(existing);
            logger.debug("Updated timeline event: sourceType={}, sourceId={}, eventType={}",
                    sourceType, sourceId, eventType);
        } else {
            TimelineEvent event = new TimelineEvent();
            event.setFamilyId(familyId);
            event.setSourceType(sourceType);
            event.setSourceId(sourceId);
            event.setEventType(eventType);
            event.setEventDate(eventDate);
            event.setDescription(description);
            event.setNodeId(nodeId);
            event.setRelationId(relationId);
            event.setIsNew(isNew);
            event.validateForCreate();
            timelineEventRepository.save(event);
            logger.debug("Created timeline event: sourceType={}, sourceId={}, eventType={}, isNew={}",
                    sourceType, sourceId, eventType, isNew);
        }
    }

    /**
     * 存量数据回填：从现有节点/关系/活动数据生成时间线事件
     * <p>
     * 幂等操作：已存在的时间线事件会被更新而非重复创建。
     *
     * @param familyId 家族ID
     * @return 回填的事件数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int backfill(Long familyId) {
        int count = 0;

        // 回填节点出生/去世事件
        List<FamilyNode> nodes = familyNodeRepository.findByFamilyId(familyId);
        for (FamilyNode node : nodes) {
            if (node.getBirthDate() != null) {
                upsertEvent(familyId, TimelineSourceTypeEnum.NODE.getCode(), node.getId(),
                        TimelineEventTypeEnum.BIRTH.getCode(), node.getBirthDate(),
                        node.getName() + " 出生", node.getId(), null, true);
                count++;
            }
            if (node.getDeathDate() != null) {
                upsertEvent(familyId, TimelineSourceTypeEnum.NODE.getCode(), node.getId(),
                        TimelineEventTypeEnum.DEATH.getCode(), node.getDeathDate(),
                        node.getName() + " 去世", node.getId(), null, true);
                count++;
            }
        }

        // 回填夫妻关系结婚事件
        List<FamilyRelation> relations = familyRelationRepository.findByFamilyId(familyId);
        for (FamilyRelation relation : relations) {
            if (relation.getRelationType() != null
                    && relation.getRelationType() == 2
                    && relation.getMarriageDate() != null) {
                String fromName = findNodeName(relation.getFromNodeId());
                String toName = findNodeName(relation.getToNodeId());
                upsertEvent(familyId, TimelineSourceTypeEnum.RELATION.getCode(), relation.getId(),
                        TimelineEventTypeEnum.MARRIAGE.getCode(), relation.getMarriageDate(),
                        fromName + " 与 " + toName + " 结婚", null, relation.getId(), true);
                count++;
            }
        }

        // 回填家族活动事件
        List<FamilyEvent> events = familyEventRepository.findByFamilyId(familyId);
        for (FamilyEvent event : events) {
            if (event.getEventTime() != null) {
                upsertEvent(familyId, TimelineSourceTypeEnum.EVENT.getCode(), event.getId(),
                        TimelineEventTypeEnum.ACTIVITY.getCode(), event.getEventTime().toLocalDate(),
                        "家族活动: " + event.getTitle(), null, null, true);
                count++;
            }
        }

        logger.info("Backfilled {} timeline events for family {}", count, familyId);
        return count;
    }

    private String findNodeName(Long nodeId) {
        if (nodeId == null) {
            return "未知";
        }
        FamilyNode node = familyNodeRepository.findById(nodeId);
        return node != null ? node.getName() : "未知";
    }
}
