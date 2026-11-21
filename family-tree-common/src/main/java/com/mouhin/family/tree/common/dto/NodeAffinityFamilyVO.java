package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 节点亲族家族视图对象（详情弹窗「亲族关系」区块）
 *
 * @author Family-Tree
 * @date 2026-11-20
 */
@Getter
@Setter
public class NodeAffinityFamilyVO {

    /**
     * 亲族类型编码（{@link com.mouhin.family.tree.common.enums.AffinityTypeEnum}）
     */
    private Integer type;

    /**
     * 亲族家族ID
     */
    private Long familyId;

    /**
     * 亲族家族名称
     */
    private String familyName;

    /**
     * 对应亲族成员节点ID（母族为母亲节点、妻/夫族为配偶节点；回落本族父行为节点自身）
     */
    private Long relatedPersonNodeId;

    /**
     * 对应亲族成员姓名
     */
    private String relatedPersonName;

    /**
     * 依据的跨族链接ID；父族回落本族时为 null
     */
    private Long linkId;

    /**
     * 成链来源编码（{@link com.mouhin.family.tree.common.enums.NodeLinkSourceEnum}）；可为 null
     */
    private Integer linkSource;

    /**
     * 链接确认人用户ID；账号自动来源为 null
     */
    private Long confirmedBy;
}
