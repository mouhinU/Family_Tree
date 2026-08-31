package com.mouhin.family.tree.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 家族活动数据对象
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
@TableName("family_event")
public class FamilyEventDO {

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
     * 发起人ID
     */
    private Long userId;

    /**
     * 发起人姓名
     */
    private String username;

    /**
     * 活动标题
     */
    private String title;

    /**
     * 活动说明
     */
    private String description;

    /**
     * 活动时间
     */
    private LocalDateTime eventTime;

    /**
     * 活动地点
     */
    private String location;

    /**
     * 活动总费用（AA 计算基数）
     */
    private BigDecimal totalCost;

    /**
     * 状态：OPEN 报名中 / CLOSED 已截止
     */
    private String status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
