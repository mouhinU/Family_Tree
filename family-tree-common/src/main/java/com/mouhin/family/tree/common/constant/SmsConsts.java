package com.mouhin.family.tree.common.constant;

/**
 * 短信验证码常量（防重放：有效时长、重发间隔、发送限量、校验失败上限）
 *
 * @author Family-Tree
 * @date 2026-11-22
 */
public final class SmsConsts {

    /**
     * 验证码位数（纯数字）
     */
    public static final int CODE_LENGTH = 6;

    /**
     * 验证码有效时长（分钟），到期自动失效
     */
    public static final int CODE_TTL_MINUTES = 5;

    /**
     * 同一手机号同一场景的重发间隔（秒）
     */
    public static final int RESEND_INTERVAL_SECONDS = 60;

    /**
     * 同一手机号当日发送上限（注册与登录场景合并计数）
     */
    public static final int PHONE_DAILY_LIMIT = 10;

    /**
     * 同一来源 IP 当日发送上限（场景合并计数）
     */
    public static final int IP_DAILY_LIMIT = 20;

    /**
     * 同一验证码校验失败上限，达到即作废需重新获取
     */
    public static final int MAX_VERIFY_ATTEMPTS = 5;

    /**
     * 验证码格式：固定位数纯数字
     */
    public static final String CODE_REGEXP = "^\\d{6}$";

    /**
     * 短信场景手机号格式：必填且为 11 位大陆号码（不同于注册资料中的选填口径）
     */
    public static final String PHONE_STRICT_REGEXP = "^1[3-9]\\d{9}$";

    private SmsConsts() {
    }
}
