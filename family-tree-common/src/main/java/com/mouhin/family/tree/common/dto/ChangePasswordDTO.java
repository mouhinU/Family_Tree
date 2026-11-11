package com.mouhin.family.tree.common.dto;

import com.mouhin.family.tree.common.constant.AccountConsts;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 修改密码请求对象
 *
 * @author Family-Tree
 * @date 2026-11-10
 */
@Getter
@Setter
public class ChangePasswordDTO {

    /**
     * 当前密码（一次性初始口令首登改密时即初始密码）
     */
    @NotBlank(message = "当前密码不能为空")
    private String oldPassword;

    /**
     * 新密码
     */
    @NotBlank(message = "新密码不能为空")
    @Size(min = AccountConsts.MIN_PASSWORD_LENGTH, message = "密码长度不能少于6位")
    private String newPassword;
}
