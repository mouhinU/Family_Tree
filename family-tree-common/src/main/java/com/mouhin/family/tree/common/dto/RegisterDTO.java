package com.mouhin.family.tree.common.dto;

import com.mouhin.family.tree.common.constant.AccountConsts;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 注册请求对象
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@Getter
@Setter
public class RegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 20, message = "用户名长度须为2-20个字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, message = "密码长度不能少于6位")
    private String password;

    /**
     * 邮箱（选填，非空时全局唯一，可用于登录）
     */
    @Email(message = "邮箱格式不正确")
    @Size(max = AccountConsts.MAX_EMAIL_LENGTH, message = "邮箱长度超出限制")
    private String email;

    /**
     * 手机号（选填，非空时全局唯一，可用于登录）
     */
    @Pattern(regexp = AccountConsts.PHONE_REGEXP, message = "手机号须为11位大陆号码")
    private String phone;

    /**
     * 家族邀请码（可选，注册时直接加入已有家族）
     */
    private String inviteCode;
}
