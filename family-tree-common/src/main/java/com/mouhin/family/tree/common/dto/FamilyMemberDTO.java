package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 家族成员信息 DTO
 *
 * @author Family-Tree
 * @date 2026-08-03
 */
@Getter
@Setter
public class FamilyMemberDTO {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 角色：OWNER / MEMBER
     */
    private String role;

    /**
     * 加入时间
     */
    private LocalDateTime joinedTime;
}
