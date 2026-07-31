package com.mouhin.family.tree.common.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 族谱关系数据传输对象
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@Data
public class FamilyRelationDTO {

    private Long id;
    private Long fromNodeId;
    private Long toNodeId;
    private Integer relationType;
    private LocalDate marriageDate;
    private LocalDate divorceDate;

    /** 是否离异（独立于离异日期） */
    private Boolean divorced;
}
