package com.mouhin.family.tree.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 活动报名对象（请求与展示复用）
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
public class EventSignupVO {

    /**
     * 报名ID
     */
    private Long id;

    /**
     * 报名用户ID
     */
    private Long userId;

    /**
     * 报名用户名
     */
    private String username;

    /**
     * 参加人数（含本人及携伴）
     */
    @Min(value = 1, message = "参加人数至少为1人")
    @Max(value = 20, message = "单次报名人数不能超过20人")
    private Integer attendeeCount;

    /**
     * 报名备注
     */
    @Size(max = 200, message = "报名备注不能超过200个字符")
    private String remark;

    /**
     * 报名时间
     */
    private LocalDateTime createTime;
}
