package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 用户个人信息 DTO（聚合昵称、出生日期，减少多次查库）
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
     * 性别（0未知 1男 2女，与 family_node.gender 同口径）
     */
    private Integer gender;

    /**
     * 出生日期
     */
    private String birthDate;

    /**
     * 农历出生日期（由阳历推导，与 birthDate 一并记录）
     */
    private String lunarBirthDate;

    /**
     * 出生日期录入历法（1阳历 2阴历，null 未记录）
     */
    private Integer birthCalendar;

    /**
     * 邮箱（仅本人资料可见，不在成员列表等他人视图暴露）
     */
    private String email;

    /**
     * 手机号（仅本人资料可见，不在成员列表等他人视图暴露）
     */
    private String phone;
}
