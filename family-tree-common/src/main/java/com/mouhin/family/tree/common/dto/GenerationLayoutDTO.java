package com.mouhin.family.tree.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 辈分管理行列布局 DTO
 *
 * @author Family-Tree
 * @date 2026-08-04
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerationLayoutDTO {

    /** 列数 */
    private Integer cols;

    /** 行数 */
    private Integer rows;
}
