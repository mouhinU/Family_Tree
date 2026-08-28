package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 创建家族请求 DTO
 *
 * @author Family-Tree
 * @date 2026-08-03
 */
@Getter
@Setter
public class FamilyCreateDTO {

    /**
     * 家族名称
     */
    private String name;
}
