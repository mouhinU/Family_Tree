package com.mouhin.family.tree.common.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家族信息 DTO
 *
 * @author Family-Tree
 * @date 2026-08-03
 */
@Data
public class FamilyDTO {

    private Long id;

    /** 家族名称 */
    private String name;

    /** 邀请码 */
    private String inviteCode;

    /** 创建者（族长）用户ID */
    private Long creatorId;

    /** 当前用户在家族中的角色（OWNER / MEMBER） */
    private String currentRole;

    /** 堂号 */
    private String hallName;

    /** 籍贯 */
    private String ancestralHome;

    private LocalDateTime createTime;
}
