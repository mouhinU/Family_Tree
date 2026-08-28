package com.mouhin.family.tree.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * 辈分管理行列布局 DTO
 *
 * @author Family-Tree
 * @date 2026-08-04
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GenerationLayoutDTO {

    /**
     * 列数
     */
    private Integer cols;

    /**
     * 行数
     */
    private Integer rows;
}
