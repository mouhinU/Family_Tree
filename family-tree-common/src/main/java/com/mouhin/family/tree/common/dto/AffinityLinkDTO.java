package com.mouhin.family.tree.common.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 人工跨族亲族链接提案请求体
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

    /**
     * 提案说明
     */
    @Size(max = 500, message = "提案说明不能超过500个字符")
    private String reason;
}
