package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 族谱关系数据传输对象
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@Getter
@Setter
public class FamilyRelationDTO {

    private Long id;
    private Long fromNodeId;
    private Long toNodeId;
    private Integer relationType;
    private LocalDate marriageDate;
    private LocalDate divorceDate;

    /**
     * 是否离异（独立于离异日期）
     */
    private Boolean divorced;

    /**
     * 是否丧偶（配偶一方去世）
     */
    private Boolean widowed;

    /**
     * 婚姻次序（第几任配偶）
     */
    private Integer marriageOrder;

    /**
     * 婚姻终止方式：DIVORCED / WIDOWED / ALIVE
     */
    private String endType;
}
