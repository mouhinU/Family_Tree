package com.mouhin.family.tree.common.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 家族故事生成请求对象
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
@Getter
@Setter
public class AiStoryDTO {

    @NotNull(message = "节点ID不能为空")
    private Long nodeId;
}
