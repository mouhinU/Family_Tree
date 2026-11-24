package com.mouhin.family.tree.common.enums;

/**
 * 短信验证码场景枚举
 * <p>
 * 区分注册验证与短信登录两类用途，决定验证码的 Redis 键空间与发送前置校验口径；
 * 同一手机号不同场景的验证码相互独立、不可互用。
 *
 * @author Family-Tree
 * @date 2026-11-22
 */
public enum SmsSceneEnum {

    /**
     * 注册验证：真人注册时校验手机号真实性
     */
    REGISTER("注册验证码"),

    /**
     * 短信登录：已注册手机号凭验证码登录
     */
    LOGIN("登录验证码");

    /**
     * 场景用途说明
     */
    private final String description;

    SmsSceneEnum(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 按名称解析场景，未识别时返回 null（由调用方给出统一错误提示）
     *
     * @param name 场景名称（REGISTER / LOGIN），大小写敏感
     * @return 场景枚举，未识别时为 null
     */
    public static SmsSceneEnum parse(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (SmsSceneEnum scene : values()) {
            if (scene.name().equals(name.trim())) {
                return scene;
            }
        }
        return null;
    }
}
