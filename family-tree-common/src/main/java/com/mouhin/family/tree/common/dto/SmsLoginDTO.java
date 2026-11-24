package com.mouhin.family.tree.common.dto;

import com.mouhin.family.tree.common.constant.SmsConsts;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * 短信验证码登录请求对象
 *
 * @author Family-Tree
 * @date 2026-11-22
 */
@Getter
@Setter
public class SmsLoginDTO {

    /**
     * 已注册账号的手机号
     */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = SmsConsts.PHONE_STRICT_REGEXP, message = "手机号须为11位大陆号码")
    private String phone;

    /**
     * 短信验证码（LOGIN 场景下发）
     */
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = SmsConsts.CODE_REGEXP, message = "验证码为6位数字")
    private String code;
}
