package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 平台展示型图谱中的族内关系视图对象。
 *
 * @author Family-Tree
 * @date 2026-11-26
 */
@Getter
@Setter
public class AdminGraphRelationVO {

    private Long id;
    private Long fromNodeId;
    private Long toNodeId;
    private Integer relationType;
    private String relationTypeLabel;
    private Boolean divorced;
    private Boolean widowed;
}
