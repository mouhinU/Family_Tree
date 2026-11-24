package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.constant.SmsConsts;
import com.mouhin.family.tree.common.enums.PlatformSettingKeyEnum;
import com.mouhin.family.tree.common.enums.SmsSceneEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.common.util.DesensitizeUtils;
import com.mouhin.family.tree.domain.entity.User;
import com.mouhin.family.tree.domain.gateway.SmsGateway;
import com.mouhin.family.tree.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 短信验证码应用服务
 * <p>
 * 负责验证码的生成、下发与一次性校验，防重放口径见《Java开发手册》安全规约：
 * 有效时长 {@value SmsConsts#CODE_TTL_MINUTES} 分钟、重发间隔与手机号/IP 当日发送限量、
 * 校验失败上限；全部计数落 Redis（键带场景或自然日后缀，条目到期自动清零），INCR/SETNX 原子操作天然并发安全。
 * 发送通道由领域端口 {@link SmsGateway} 提供，未接入阿里云时验证码落入日志。
 *
 * @author Family-Tree
 * @date 2026-11-22
 */
@Service
public class SmsCodeApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(SmsCodeApplicationService.class);

    /**
     * Redis Key 前缀：验证码本体（sms:code:{场景}:{手机号}）
     */
    private static final String CODE_KEY_PREFIX = "sms:code:";

    /**
     * Redis Key 前缀：校验失败计数（sms:fail:{场景}:{手机号}）
     */
    private static final String FAIL_KEY_PREFIX = "sms:fail:";

    /**
     * Redis Key 前缀：重发间隔哨兵（sms:interval:{场景}:{手机号}）
     */
    private static final String INTERVAL_KEY_PREFIX = "sms:interval:";

    /**
     * Redis Key 前缀：手机号当日发送计数（场景合并，sms:day:phone:{yyyyMMdd}:{手机号}）
     */
    private static final String PHONE_DAY_KEY_PREFIX = "sms:day:phone:";

    /**
     * Redis Key 前缀：来源 IP 当日发送计数（场景合并，sms:day:ip:{yyyyMMdd}:{IP}）
     */
    private static final String IP_DAY_KEY_PREFIX = "sms:day:ip:";

    /**
     * 自然日计数键的过期时长：跨日冗余 1 小时，避免边界时钟差导致的键漂移
     */
    private static final Duration DAILY_KEY_TTL = Duration.ofHours(25);

    /**
     * 自然日键后缀格式（yyyyMMdd）
     */
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    /**
     * 验证码生成使用密码学安全随机源（静态复用，禁止在方法体内新建）
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 6 位数字验证码取值区间下限
     */
    private static final int CODE_NUMERIC_FLOOR = 100000;

    /**
     * 6 位数字验证码取值区间跨度
     */
    private static final int CODE_NUMERIC_RANGE = 900000;

    private final StringRedisTemplate stringRedisTemplate;
    private final SmsGateway smsGateway;
    private final PlatformSettingApplicationService platformSettingService;
    private final UserRepository userRepository;

    public SmsCodeApplicationService(StringRedisTemplate stringRedisTemplate,
                                     SmsGateway smsGateway,
                                     PlatformSettingApplicationService platformSettingService,
                                     UserRepository userRepository) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.smsGateway = smsGateway;
        this.platformSettingService = platformSettingService;
        this.userRepository = userRepository;
    }

    /**
     * 下发注册场景验证码：开关开启且该手机号尚未被账号占用
     *
     * @param phone    11 位大陆手机号
     * @param clientIp 请求来源 IP，可为 null
     */
    public void sendRegisterCode(String phone, String clientIp) {
        assertSceneEnabled(SmsSceneEnum.REGISTER);
        if (userRepository.findByPhone(phone) != null) {
            throw new BusinessException("该手机号已注册账号，请直接登录");
        }
        sendCode(SmsSceneEnum.REGISTER, phone, clientIp);
    }

    /**
     * 下发短信登录场景验证码：开关开启且该手机号已绑定账号
     *
     * @param phone    11 位大陆手机号
     * @param clientIp 请求来源 IP，可为 null
     */
    public void sendLoginCode(String phone, String clientIp) {
        assertSceneEnabled(SmsSceneEnum.LOGIN);
        User user = userRepository.findByPhone(phone);
        if (user == null) {
            throw new BusinessException("该手机号未绑定账号");
        }
        sendCode(SmsSceneEnum.LOGIN, phone, clientIp);
    }

    /**
     * 生成并下发验证码：依次过重发间隔、手机号与 IP 当日限量；
     * 发送失败时回滚本次占用的间隔与限量名额，不消耗用户重试机会
     *
     * @param scene    验证码场景（开关已由场景入口断言）
     * @param phone    11 位大陆手机号
     * @param clientIp 请求来源 IP，可为 null（如测试场景）
     */
    private void sendCode(SmsSceneEnum scene, String phone, String clientIp) {
        String intervalKey = scopedKey(INTERVAL_KEY_PREFIX, scene, phone);
        Boolean firstAllowed = stringRedisTemplate.opsForValue().setIfAbsent(
                intervalKey, "1", Duration.ofSeconds(SmsConsts.RESEND_INTERVAL_SECONDS));
        if (!Boolean.TRUE.equals(firstAllowed)) {
            throw new BusinessException("验证码发送过于频繁，请 " + SmsConsts.RESEND_INTERVAL_SECONDS + " 秒后再试");
        }

        String phoneDayKey = dailyKey(PHONE_DAY_KEY_PREFIX, phone);
        assertWithinDailyLimit(phoneDayKey, SmsConsts.PHONE_DAILY_LIMIT, "该手机号今日验证码发送次数已达上限");
        String ipDayKey = clientIp == null || clientIp.isBlank() ? null : dailyKey(IP_DAY_KEY_PREFIX, clientIp);
        if (ipDayKey != null) {
            assertWithinDailyLimit(ipDayKey, SmsConsts.IP_DAILY_LIMIT, "当前网络今日验证码发送次数已达上限，请稍后再试");
        }

        String code = generateCode();
        stringRedisTemplate.opsForValue().set(scopedKey(CODE_KEY_PREFIX, scene, phone), code,
                Duration.ofMinutes(SmsConsts.CODE_TTL_MINUTES));
        stringRedisTemplate.delete(scopedKey(FAIL_KEY_PREFIX, scene, phone));
        try {
            smsGateway.sendVerificationCode(phone, code);
        } catch (RuntimeException e) {
            rollbackQuota(phoneDayKey, ipDayKey, intervalKey, scene, phone);
            logger.error("SMS send failed, quota rolled back: scene={}, phone={}, err={}",
                    scene, DesensitizeUtils.maskPhone(phone), e.getMessage());
            throw e;
        }
        logger.info("SMS code dispatched: scene={}, phone={}", scene, DesensitizeUtils.maskPhone(phone));
    }

    /**
     * 校验验证码：一次性使用，成功后立即销毁；连续失败达到上限即作废旧码
     *
     * @param scene 验证码场景
     * @param phone 手机号
     * @param code  待校验验证码
     * @throws BusinessException 验证码过期、错误次数超限或内容不正确
     */
    public void verifyCode(SmsSceneEnum scene, String phone, String code) {
        String codeKey = scopedKey(CODE_KEY_PREFIX, scene, phone);
        String failKey = scopedKey(FAIL_KEY_PREFIX, scene, phone);
        String stored = stringRedisTemplate.opsForValue().get(codeKey);
        if (stored == null) {
            throw new BusinessException("验证码已过期，请重新获取");
        }
        if (getFailCount(failKey) >= SmsConsts.MAX_VERIFY_ATTEMPTS) {
            stringRedisTemplate.delete(codeKey);
            stringRedisTemplate.delete(failKey);
            throw new BusinessException("验证码输错次数过多，请重新获取");
        }
        if (code == null || !stored.equals(code.trim())) {
            Long fails = stringRedisTemplate.opsForValue().increment(failKey);
            if (fails != null && fails == 1L) {
                stringRedisTemplate.expire(failKey, Duration.ofMinutes(SmsConsts.CODE_TTL_MINUTES));
            }
            throw new BusinessException("验证码不正确");
        }
        stringRedisTemplate.delete(codeKey);
        stringRedisTemplate.delete(failKey);
    }

    /**
     * 断言场景对应的平台开关已开启
     *
     * @param scene 验证码场景
     */
    public void assertSceneEnabled(SmsSceneEnum scene) {
        if (scene == SmsSceneEnum.REGISTER) {
            platformSettingService.assertEnabled(PlatformSettingKeyEnum.SMS_VERIFY_ENABLED);
        } else {
            platformSettingService.assertEnabled(PlatformSettingKeyEnum.SMS_LOGIN_ENABLED);
        }
    }

    /**
     * 注册短信验证开关是否开启（注册链路据此决定手机号与验证码是否必填）
     *
     * @return 开启返回 true
     */
    public boolean isRegisterVerifyEnabled() {
        return platformSettingService.isEnabled(PlatformSettingKeyEnum.SMS_VERIFY_ENABLED);
    }

    /**
     * 短信登录开关是否开启（前端据此决定"短信验证码登录"入口显隐）
     *
     * @return 开启返回 true
     */
    public boolean isSmsLoginEnabled() {
        return platformSettingService.isEnabled(PlatformSettingKeyEnum.SMS_LOGIN_ENABLED);
    }

    private String generateCode() {
        return String.valueOf(CODE_NUMERIC_FLOOR + SECURE_RANDOM.nextInt(CODE_NUMERIC_RANGE));
    }

    private String scopedKey(String prefix, SmsSceneEnum scene, String phone) {
        return prefix + scene.name() + ":" + phone;
    }

    private String dailyKey(String prefix, String identity) {
        return prefix + LocalDate.now().format(DAY_FORMATTER) + ":" + identity;
    }

    private void assertWithinDailyLimit(String dayKey, int limit, String rejectedMessage) {
        Long count = stringRedisTemplate.opsForValue().increment(dayKey);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(dayKey, DAILY_KEY_TTL);
        }
        if (count != null && count > limit) {
            stringRedisTemplate.opsForValue().decrement(dayKey);
            throw new BusinessException(rejectedMessage);
        }
    }

    private void rollbackQuota(String phoneDayKey, String ipDayKey, String intervalKey,
                               SmsSceneEnum scene, String phone) {
        stringRedisTemplate.delete(intervalKey);
        stringRedisTemplate.delete(scopedKey(CODE_KEY_PREFIX, scene, phone));
        stringRedisTemplate.opsForValue().decrement(phoneDayKey);
        if (ipDayKey != null) {
            stringRedisTemplate.opsForValue().decrement(ipDayKey);
        }
    }

    private int getFailCount(String failKey) {
        String value = stringRedisTemplate.opsForValue().get(failKey);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warn("Invalid sms fail count for key={}: {}", failKey, value);
            return 0;
        }
    }
}
