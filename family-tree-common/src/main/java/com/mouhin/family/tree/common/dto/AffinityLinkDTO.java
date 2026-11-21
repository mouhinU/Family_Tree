package com.mouhin.family.tree.common.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 人工建立跨族亲族链接请求体
 *
 * @author Family-Tree
 * @date 2026-11-20
 */
@Getter
@Setter
public class AffinityLinkDTO {

    /**
     * 他族目标节点ID
     */
    @NotNull(message = "目标节点不能为空")
    private Long dstNodeId;

    /**
     * 链接类型编码（可为 null，默认血缘同人），取值见
     * {@link com.mouhin.family.tree.common.enums.NodeLinkTypeEnum}
     */
    private Integer linkType;
}
