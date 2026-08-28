package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 批量更新节点颜色请求对象
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@Getter
@Setter
public class ColorUpdateDTO {

    private List<Long> nodeIds;
    private String colorLabel;
}
