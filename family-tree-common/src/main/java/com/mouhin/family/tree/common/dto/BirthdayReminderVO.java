package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 生日提醒展示对象
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
public class BirthdayReminderVO {

    /**
     * 族谱节点ID
     */
    private Long nodeId;

    /**
     * 族人姓名
     */
    private String name;

    /**
     * 出生日期
     */
    private String birthDate;

    /**
     * 即将到来的岁数（今年将满）
     */
    private Integer age;

    /**
     * 距生日天数（0 表示今天）
     */
    private Integer daysUntil;
}
