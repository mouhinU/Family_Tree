package com.mouhin.family.tree.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 时间线事件数据对象
 *
 * @author Family-Tree
 * @date 2026-09-12
 */
@Getter
@Setter
@TableName("family_timeline")
public class TimelineEventDO {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 家族ID
     */
    private Long familyId;

    /**
     * 来源类型：NODE/RELATION/EVENT
     */
    private String sourceType;

    /**
     * 来源ID（节点ID/关系ID/活动ID）
     */
    private Long sourceId;

    /**
     * 事件类型：BIRTH/DEATH/MARRIAGE/ACTIVITY
     */
    private String eventType;

    /**
     * 事件日期
     */
    private LocalDate eventDate;

    /**
     * 事件描述
     */
    private String description;

    /**
     * 关联节点ID（出生/去世时填写）
     */
    private Long nodeId;

    /**
     * 关联关系ID（结婚时填写）
     */
    private Long relationId;

    /**
     * 是否新增数据：1新增 0修改
     */
    private Boolean isNew;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
