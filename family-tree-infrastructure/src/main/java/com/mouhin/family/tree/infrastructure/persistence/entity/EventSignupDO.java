package com.mouhin.family.tree.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 活动报名数据对象
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
@TableName("family_event_signup")
public class EventSignupDO {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 活动ID
     */
    private Long eventId;

    /**
     * 家族ID
     */
    private Long familyId;

    /**
     * 报名用户ID
     */
    private Long userId;

    /**
     * 报名用户名
     */
    private String username;

    /**
     * 参加人数
     */
    private Integer attendeeCount;

    /**
     * 报名备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
