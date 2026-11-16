package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 平台管理页用户所属家族关系视图对象
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Getter
@Setter
public class UserAdminFamilyVO {

    /**
     * 家族ID
     */
    private Long familyId;

    /**
     * 家族名称
     */
    private String familyName;

    /**
     * 成员角色编码：OWNER / ADMIN / MEMBER
     */
    private String role;

    /**
     * 成员角色中文名
     */
    private String roleLabel;

    /**
     * 是否为当前激活家族
     */
    private Boolean current;

    /**
     * 加入家族时间
     */
    private LocalDateTime joinedTime;

    /**
     * 在该家族中标记为本人的节点ID，未标记时为 null
     */
    private Long boundNodeId;

    /**
     * 标记为本人的节点姓名，未标记时为 null
     */
    private String boundNodeName;

    @Override
    public String toString() {
        return "UserAdminFamilyVO{"
                + "familyId=" + familyId
                + ", familyName='" + familyName + '\''
                + ", role='" + role + '\''
                + ", current=" + current
                + ", boundNodeId=" + boundNodeId
                + '}';
    }
}
