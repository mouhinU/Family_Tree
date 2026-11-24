package com.mouhin.family.tree.common.dto;

import com.mouhin.family.tree.common.constant.SmsConsts;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * 发送短信验证码请求对象
 *
 * @author Family-Tree
 * @date 2026-11-22
 */
@Getter
@Setter
public class SendSmsCodeDTO {

    /**
     * 手机号：短信场景必填，须为 11 位大陆号码
     */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = SmsConsts.PHONE_STRICT_REGEXP, message = "手机号须为11位大陆号码")
    private String phone;

    /**
     * 验证码场景：REGISTER（注册）/ LOGIN（短信登录）
     */
    @NotBlank(message = "验证码场景不能为空")
    private String scene;
}
