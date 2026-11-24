package com.mouhin.family.tree.domain.gateway;

/**
 * 短信网关接口（端口）
 * <p>
 * 抽象验证码短信发送能力，由基础设施层提供实现（如阿里云短信认证服务 Dypnsapi）。
 * 未接入短信服务时，激活的实现应将验证码落入日志而非静默丢弃。
 *
 * @author Family-Tree
 * @date 2026-11-22
 */
public interface SmsGateway {

    /**
     * 向指定手机号发送验证码短信
     *
     * @param phone 11 位大陆手机号
     * @param code  短信验证码（模板参数 code）
     */
    void sendVerificationCode(String phone, String code);
}
