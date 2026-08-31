package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 纪念日展示对象
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
public class AnniversaryVO {

    /**
     * 纪念日ID
     */
    private Long id;

    /**
     * 关联族谱节点ID
     */
    private Long nodeId;

    /**
     * 关联节点姓名
     */
    private String nodeName;

    /**
     * 纪念日标题
     */
    private String title;

    /**
     * 分类编码
     */
    private String category;

    /**
     * 分类描述
     */
    private String categoryDesc;

    /**
     * 纪念日日期
     */
    private LocalDate anniversaryDate;

    /**
     * 备注
     */
    private String remark;

    /**
     * 距今年数（周年数）
     */
    private Integer years;

    /**
     * 距下一次纪念日天数
     */
    private Integer daysUntil;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 是否为当前用户创建（用于前端判断管理权限）
     */
    private Boolean own;
}
