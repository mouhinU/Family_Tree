package com.mouhin.family.tree.common.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 登录请求对象
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@Getter
@Setter
public class LoginDTO {

    /**
     * 登录标识：支持用户名、手机号或邮箱（字段名沿用 username 以兼容既有前端）
     */
    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
