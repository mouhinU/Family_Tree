package com.mouhin.family.tree.common.dto;

import lombok.Data;

/**
 * 族谱辈分（世代名称）数据传输对象
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@Data
public class FamilyGenerationDTO {

    private Long id;

    /**
     * 世代（从1开始）
     */
    private Integer generation;

    /**
     * 辈分名称（字辈），空字符串表示清除该世代名称
     */
    private String name;
}
