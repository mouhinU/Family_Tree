package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 节点绑定账号归属的其他家族视图对象（Phase 0 受限展示，仅本人或族长可见）
 *
 * @author Family-Tree
 * @date 2026-11-20
 */
@Getter
@Setter
public class NodeMemberFamilyVO {

    /**
     * 家族ID
     */
    private Long familyId;

    /**
     * 家族名称
     */
    private String familyName;

    /**
     * 该账号在此家族的成员角色编码（{@link com.mouhin.family.tree.common.enums.FamilyMemberRoleEnum}）
     */
    private String role;
}
