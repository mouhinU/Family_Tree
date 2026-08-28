package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 加入家族请求 DTO
 *
 * @author Family-Tree
 * @date 2026-08-03
 */
@Getter
@Setter
public class FamilyJoinDTO {

    /**
     * 邀请码
     */
    private String inviteCode;
}
