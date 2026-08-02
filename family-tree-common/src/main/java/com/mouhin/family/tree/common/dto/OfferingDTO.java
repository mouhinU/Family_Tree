package com.mouhin.family.tree.common.dto;

import lombok.Data;

/**
 * 祭奠操作数据传输对象（上香烛 / 烧纸）
 *
 * @author Family-Tree
 * @date 2026-08-01
 */
@Data
public class OfferingDTO {

    /** 受祭的已故节点ID */
    private Long nodeId;

    /** 祭奠类型：1-香烛 2-烧纸 */
    private Integer offeringType;
}
