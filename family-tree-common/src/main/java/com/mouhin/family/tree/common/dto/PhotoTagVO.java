package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 照片人物标记展示对象
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
public class PhotoTagVO {

    /**
     * 标记ID
     */
    private Long id;

    /**
     * 族谱节点ID
     */
    private Long nodeId;

    /**
     * 节点姓名
     */
    private String nodeName;
}
