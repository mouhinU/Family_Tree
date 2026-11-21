package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 家族只读摘要视图对象（跨族详情跳转前的最小信息，不含敏感明细）
 *
 * @author Family-Tree
 * @date 2026-11-20
 */
@Getter
@Setter
public class FamilyBriefVO {

    /**
     * 家族ID
     */
    private Long familyId;

    /**
     * 家族名称
     */
    private String familyName;

    /**
     * 始祖姓名（口径：建族初始节点，即家族内最早创建的节点；无节点时为 null）
     */
    private String ancestorName;

    /**
     * 辈分代数（家族配置的辈分行数，未配置时为 null）
     */
    private Integer generationRows;

    /**
     * 家族成员数
     */
    private Long memberCount;
}
