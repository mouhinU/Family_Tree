package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 家族活动展示对象
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
public class EventVO {

    /**
     * 活动ID
     */
    private Long id;

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
     * 活动总费用
     */
    private BigDecimal totalCost;

    /**
     * 状态编码（OPEN 报名中 / CLOSED 已截止）
     */
    private String status;

    /**
     * 状态描述
     */
    private String statusDesc;

    /**
     * 报名总人数（含携伴）
     */
    private Integer totalAttendees;

    /**
     * AA 人均费用（总费用 / 报名总人数，无费用时为 0）
     */
    private BigDecimal perPersonCost;

    /**
     * 当前用户是否已报名
     */
    private Boolean signedUp;

    /**
     * 是否为当前用户发起（用于前端判断管理权限）
     */
    private Boolean own;

    /**
     * 报名列表
     */
    private List<EventSignupVO> signups;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
