package com.mouhin.family.tree.infrastructure.sms;

import com.mouhin.family.tree.common.util.DesensitizeUtils;
import com.mouhin.family.tree.domain.gateway.SmsGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 日志模式短信网关（未接入阿里云时的回退实现）
 * <p>
 * aliyun.sms.enabled=false（或未配置）时激活：验证码仅落 INFO 日志（手机号脱敏），
 * 供本地开发与端到端测试完成校验链路，不产生任何外部调用与费用。
 *
 * @author Family-Tree
 * @date 2026-11-22
 */
@Component
@ConditionalOnProperty(name = "aliyun.sms.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingSmsGateway implements SmsGateway {

    private static final Logger logger = LoggerFactory.getLogger(LoggingSmsGateway.class);

    @Override
    public void sendVerificationCode(String phone, String code) {
        logger.info("SMS verification code [log mode, aliyun.sms.enabled=false]: phone={}, code={}",
                DesensitizeUtils.maskPhone(phone), code);
    }
}
