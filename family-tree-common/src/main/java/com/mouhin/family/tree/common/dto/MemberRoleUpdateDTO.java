package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 成员角色变更请求
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
@Getter
@Setter
public class MemberRoleUpdateDTO {

    /**
     * 目标用户ID
     */
    private Long userId;

    /**
     * 目标角色编码
     */
    private String role;
}
