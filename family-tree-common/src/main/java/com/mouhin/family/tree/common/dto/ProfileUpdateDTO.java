package com.mouhin.family.tree.common.dto;

import com.mouhin.family.tree.common.constant.AccountConsts;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 个人信息更新请求对象
 *
 * @author Family-Tree
 * @date 2026-08-03
 */
@Getter
@Setter
public class ProfileUpdateDTO {

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 出生日期（yyyy-MM-dd）
     */
    private String birthDate;

    /**
     * 所属辈分（第几世）
     */
    private Integer generation;

    /**
     * 邮箱（选填，非空时全局唯一，可用于登录；留空表示不修改）
     */
    @Email(message = "邮箱格式不正确")
    @Size(max = AccountConsts.MAX_EMAIL_LENGTH, message = "邮箱长度超出限制")
    private String email;

    /**
     * 手机号（选填，非空时全局唯一，可用于登录；留空表示不修改）
     */
    @Pattern(regexp = AccountConsts.PHONE_REGEXP, message = "手机号须为11位大陆号码")
    private String phone;
}
