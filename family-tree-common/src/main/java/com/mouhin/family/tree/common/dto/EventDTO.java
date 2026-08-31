package com.mouhin.family.tree.common.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 家族活动创建请求对象
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
public class EventDTO {

    /**
     * 活动标题
     */
    @NotBlank(message = "活动标题不能为空")
    @Size(max = 100, message = "活动标题不能超过100个字符")
    private String title;

    /**
     * 活动说明
     */
    @Size(max = 1000, message = "活动说明不能超过1000个字符")
    private String description;

    /**
     * 活动时间（格式：yyyy-MM-dd HH:mm 或 ISO 格式）
     */
    @NotBlank(message = "活动时间不能为空")
    private String eventTime;

    /**
     * 活动地点
     */
    @Size(max = 200, message = "活动地点不能超过200个字符")
    private String location;

    /**
     * 活动总费用（用于 AA 计算，0 表示暂不收费）
     */
    @DecimalMin(value = "0", message = "活动总费用不能为负数")
    private BigDecimal totalCost;
}
