package com.mouhin.family.tree.common.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 照片人物标记请求对象
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
public class PhotoTagDTO {

    /**
     * 族谱节点ID
     */
    @NotNull(message = "请选择要标记的族人")
    private Long nodeId;
}
