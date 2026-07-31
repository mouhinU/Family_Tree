package com.mouhin.family.tree.common.dto;

import lombok.Data;

/**
 * 新增节点请求对象（可附带父节点或配偶关系）
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@Data
public class NodeCreateDTO {

    private String name;
    private Integer gender;
    private String birthDate;
    private String deathDate;
    private String colorLabel;
    private String avatar;
    private String remark;

    /** 同胞排次（选填，未填时新增子节点自动追加为末位） */
    private Integer birthOrder;

    /** 父节点ID（建立亲子关系） */
    private Long parentNodeId;

    /** 配偶节点ID（建立夫妻关系） */
    private Long spouseNodeId;

    /** 子节点ID（新增父节点时使用，建立亲子关系） */
    private Long childNodeId;
}
