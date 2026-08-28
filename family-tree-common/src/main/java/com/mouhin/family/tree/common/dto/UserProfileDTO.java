package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 用户个人信息 DTO（聚合昵称、辈分、出生日期、节点ID，减少多次查库）
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
@Getter
@Setter
public class UserProfileDTO {

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 辈分（第几世）
     */
    private Integer generation;

    /**
     * 出生日期
     */
    private String birthDate;

    /**
     * 关联的族谱节点ID
     */
    private Long nodeId;
}
