package com.mouhin.family.tree.infrastructure.sms;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponseBody;
import com.aliyun.teaopenapi.models.Config;
import com.mouhin.family.tree.common.constant.SmsConsts;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.common.util.DesensitizeUtils;
import com.mouhin.family.tree.domain.gateway.SmsGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 阿里云短信认证服务（Dypnsapi）验证码网关实现
 * <p>
 * 仅在 aliyun.sms.enabled=true 时激活。凭据（AccessKey、签名、模板）全部由
 * 环境变量注入（见 application.yml aliyun.sms.*），Secret 不入库不落日志。
 * 走 SendSmsVerifyCode 自定义验证码模式：模板参数为 {"code":"验证码","min":"有效分钟数"}，
 * 验证码由本服务端生成并存储校验（阿里云侧不核验自定义码），签名与模板须为短信认证
 * 服务赠送的恒创联众 / 100001 组合。
 *
 * @author Family-Tree
 * @date 2026-11-23
 */
@Component
@ConditionalOnProperty(name = "aliyun.sms.enabled", havingValue = "true")
public class AliyunSmsGateway implements SmsGateway {

    private static final Logger logger = LoggerFactory.getLogger(AliyunSmsGateway.class);

    /**
     * 短信认证服务发送成功返回码
     */
    private static final String SMS_OK = "OK";

    /**
     * 模板参数 JSON 形态：服务端生成的 6 位数字验证码 + 有效分钟数，无外部原文拼接
     */
    private static final String TEMPLATE_PARAM_FORMAT = "{\"code\":\"%s\",\"min\":\"%d\"}";

    /**
     * 短信发送默认区域（Dypnsapi 中心化接入点所在区域）
     */
    private static final String DEFAULT_REGION_ID = "cn-hangzhou";

    /**
     * 阿里云 RPC 响应头中的请求 ID（SendSmsVerifyCodeResponseBody 不含该字段，仅头部可取）
     */
    private static final String HEADER_REQUEST_ID = "x-acs-request-id";

    private final String accessKeyId;
    private final String accessKeySecret;
    private final String signName;
    private final String templateCode;
    private final String endpoint;

    private volatile Client client;

    public AliyunSmsGateway(@Value("${aliyun.sms.access-key-id:}") String accessKeyId,
                            @Value("${aliyun.sms.access-key-secret:}") String accessKeySecret,
                            @Value("${aliyun.sms.sign-name:}") String signName,
                            @Value("${aliyun.sms.template-code:}") String templateCode,
                            @Value("${aliyun.sms.endpoint:dypnsapi.aliyuncs.com}") String endpoint) {
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.signName = signName;
        this.templateCode = templateCode;
        this.endpoint = endpoint;
    }

    @Override
    public void sendVerificationCode(String phone, String code) {
        SendSmsVerifyCodeRequest request = new SendSmsVerifyCodeRequest()
                .setPhoneNumber(phone)
                .setSignName(signName)
                .setTemplateCode(templateCode)
                .setTemplateParam(String.format(TEMPLATE_PARAM_FORMAT, code, SmsConsts.CODE_TTL_MINUTES));
        try {
            SendSmsVerifyCodeResponse response = getClient().sendSmsVerifyCode(request);
            SendSmsVerifyCodeResponseBody body = response.getBody();
            if (body == null || !SMS_OK.equals(body.getCode())) {
                String bizCode = body == null ? "null" : body.getCode();
                String bizMessage = body == null ? "空响应" : body.getMessage();
                logger.error("Aliyun SMS verify code send rejected: phone={}, bizCode={}, bizMessage={}, requestId={}",
                        DesensitizeUtils.maskPhone(phone), bizCode, bizMessage, requestId(response));
                throw new BusinessException("验证码发送失败，请稍后重试");
            }
            logger.info("Aliyun SMS verify code sent: phone={}, requestId={}", DesensitizeUtils.maskPhone(phone),
                    requestId(response));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Aliyun SMS verify code call failed: phone={}, err={}",
                    DesensitizeUtils.maskPhone(phone), e.getMessage(), e);
            throw new BusinessException("验证码发送失败，请稍后重试");
        }
    }

    /**
     * 从响应头提取请求 ID，用于日志追踪（缺失时返回 null 占位）
     */
    private String requestId(SendSmsVerifyCodeResponse response) {
        if (response == null || response.getHeaders() == null) {
            return "null";
        }
        Map<String, String> headers = response.getHeaders();
        String id = headers.get(HEADER_REQUEST_ID);
        return id == null ? "null" : id;
    }

    /**
     * 双重检查延迟初始化 Dypnsapi 客户端（线程安全，凭据变更需重启生效）
     */
    private Client getClient() {
        Client local = client;
        if (local == null) {
            synchronized (this) {
                local = client;
                if (local == null) {
                    Config config = new Config()
                            .setAccessKeyId(accessKeyId)
                            .setAccessKeySecret(accessKeySecret)
                            .setEndpoint(endpoint);
                    config.regionId = DEFAULT_REGION_ID;
                    try {
                        local = new Client(config);
                    } catch (Exception e) {
                        logger.error("Aliyun SMS client init failed: {}", e.getMessage(), e);
                        throw new BusinessException("短信服务未就绪，请联系管理员");
                    }
                    client = local;
                }
            }
        }
        return local;
    }
}
