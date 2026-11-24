package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.constant.SmsConsts;
import com.mouhin.family.tree.common.enums.PlatformSettingKeyEnum;
import com.mouhin.family.tree.common.enums.SmsSceneEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.User;
import com.mouhin.family.tree.domain.gateway.SmsGateway;
import com.mouhin.family.tree.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 短信验证码应用服务单元测试：假 Redis 存储验证下发限流、回滚与一次性校验口径
 *
 * @author Family-Tree
 * @date 2026-11-22
 */
@ExtendWith(MockitoExtension.class)
class SmsCodeApplicationServiceTest {

    private static final String PHONE = "13800138000";
    private static final String CLIENT_IP = "203.0.113.9";
    private static final String CODE_KEY_PREFIX = "sms:code:";
    private static final String FAIL_KEY_PREFIX = "sms:fail:";
    private static final String INTERVAL_KEY_PREFIX = "sms:interval:";
    private static final String PHONE_DAY_KEY_PREFIX = "sms:day:phone:";
    private static final String IP_DAY_KEY_PREFIX = "sms:day:ip:";

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SmsGateway smsGateway;

    @Mock
    private PlatformSettingApplicationService platformSettingService;

    @Mock
    private UserRepository userRepository;

    /**
     * 假 Redis 存储：让 mock 具备 GET/SET/INCR/DECR/SETNX/DEL 语义
     */
    private final Map<String, String> redisStore = new HashMap<>();

    private SmsCodeApplicationService service;

    @BeforeEach
    void setUp() {
        wireRedisMock();
        service = new SmsCodeApplicationService(stringRedisTemplate, smsGateway,
                platformSettingService, userRepository);
    }

    private void wireRedisMock() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString()))
                .thenAnswer(invocation -> redisStore.get(invocation.<String>getArgument(0)));
        lenient().when(valueOperations.increment(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            long next = Long.parseLong(redisStore.getOrDefault(key, "0")) + 1;
            redisStore.put(key, String.valueOf(next));
            return next;
        });
        lenient().when(valueOperations.decrement(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            long next = Long.parseLong(redisStore.getOrDefault(key, "0")) - 1;
            redisStore.put(key, String.valueOf(next));
            return next;
        });
        lenient().when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    if (redisStore.containsKey(key)) {
                        return false;
                    }
                    redisStore.put(key, invocation.getArgument(1));
                    return true;
                });
        lenient().doAnswer(invocation -> {
            redisStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        lenient().when(stringRedisTemplate.delete(anyString())).thenAnswer(invocation ->
                redisStore.remove(invocation.<String>getArgument(0)) != null);
    }

    private static String codeKey(SmsSceneEnum scene) {
        return CODE_KEY_PREFIX + scene.name() + ":" + PHONE;
    }

    private static String failKey(SmsSceneEnum scene) {
        return FAIL_KEY_PREFIX + scene.name() + ":" + PHONE;
    }

    private static String intervalKey(SmsSceneEnum scene) {
        return INTERVAL_KEY_PREFIX + scene.name() + ":" + PHONE;
    }

    private static String phoneDayKey() {
        return PHONE_DAY_KEY_PREFIX + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ":" + PHONE;
    }

    private static String ipDayKey() {
        return IP_DAY_KEY_PREFIX + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ":" + CLIENT_IP;
    }

    @Test
    void sendRegisterCodeStoresScopedCodeAndDispatchesOnce() {
        when(userRepository.findByPhone(PHONE)).thenReturn(null);

        service.sendRegisterCode(PHONE, CLIENT_IP);

        String code = redisStore.get(codeKey(SmsSceneEnum.REGISTER));
        assertTrue(code != null && code.matches(SmsConsts.CODE_REGEXP), "应生成6位数字验证码并按场景存储");
        verify(smsGateway).sendVerificationCode(eq(PHONE), eq(code));
        assertEquals("1", redisStore.get(intervalKey(SmsSceneEnum.REGISTER)));
        assertEquals("1", redisStore.get(phoneDayKey()));
        assertEquals("1", redisStore.get(ipDayKey()));
    }

    @Test
    void sendRegisterCodeRejectsPhoneAlreadyRegistered() {
        when(userRepository.findByPhone(PHONE)).thenReturn(new User());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.sendRegisterCode(PHONE, CLIENT_IP));

        assertTrue(ex.getMessage().contains("该手机号已注册账号"));
        verify(smsGateway, never()).sendVerificationCode(anyString(), anyString());
    }

    @Test
    void sendLoginCodeRejectsPhoneWithoutBoundAccount() {
        when(userRepository.findByPhone(PHONE)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.sendLoginCode(PHONE, CLIENT_IP));

        assertTrue(ex.getMessage().contains("该手机号未绑定账号"));
        verify(smsGateway, never()).sendVerificationCode(anyString(), anyString());
    }

    @Test
    void sendCodeRejectsResendWithinInterval() {
        when(userRepository.findByPhone(PHONE)).thenReturn(null);
        redisStore.put(intervalKey(SmsSceneEnum.REGISTER), "1");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.sendRegisterCode(PHONE, CLIENT_IP));

        assertTrue(ex.getMessage().contains("验证码发送过于频繁"));
        verify(smsGateway, never()).sendVerificationCode(anyString(), anyString());
        assertFalse(redisStore.containsKey(phoneDayKey()), "间隔拦截应先于限量计数，不消耗当日名额");
    }

    @Test
    void sendCodeRejectsWhenPhoneDailyLimitReachedAndRestoresCount() {
        when(userRepository.findByPhone(PHONE)).thenReturn(null);
        redisStore.put(phoneDayKey(), String.valueOf(SmsConsts.PHONE_DAILY_LIMIT));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.sendRegisterCode(PHONE, CLIENT_IP));

        assertTrue(ex.getMessage().contains("今日验证码发送次数已达上限"));
        assertEquals(String.valueOf(SmsConsts.PHONE_DAILY_LIMIT), redisStore.get(phoneDayKey()),
                "拒绝应回退本次 INCR 计数");
        verify(smsGateway, never()).sendVerificationCode(anyString(), anyString());
    }

    @Test
    void sendCodeRollsBackQuotaAndIntervalWhenGatewayFails() {
        when(userRepository.findByPhone(PHONE)).thenReturn(null);
        doThrow(new RuntimeException("sms channel down"))
                .when(smsGateway).sendVerificationCode(anyString(), anyString());

        assertThrows(RuntimeException.class, () -> service.sendRegisterCode(PHONE, CLIENT_IP));

        assertFalse(redisStore.containsKey(intervalKey(SmsSceneEnum.REGISTER)), "发送失败应释放重发间隔");
        assertFalse(redisStore.containsKey(codeKey(SmsSceneEnum.REGISTER)), "发送失败应作废未送达验证码");
        assertEquals("0", redisStore.get(phoneDayKey()), "发送失败应回滚手机号当日名额");
        assertEquals("0", redisStore.get(ipDayKey()), "发送失败应回滚 IP 当日名额");
    }

    @Test
    void verifyCodeSuccessConsumesCodeAndFailCounter() {
        redisStore.put(codeKey(SmsSceneEnum.LOGIN), "123456");

        service.verifyCode(SmsSceneEnum.LOGIN, PHONE, "123456");

        assertFalse(redisStore.containsKey(codeKey(SmsSceneEnum.LOGIN)), "验证码应一次性消费");
        assertFalse(redisStore.containsKey(failKey(SmsSceneEnum.LOGIN)));
    }

    @Test
    void verifyCodeRejectsExpired() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.verifyCode(SmsSceneEnum.LOGIN, PHONE, "123456"));
        assertTrue(ex.getMessage().contains("验证码已过期"));
    }

    @Test
    void verifyCodeWrongAnswerIncrementsFailCounter() {
        redisStore.put(codeKey(SmsSceneEnum.LOGIN), "123456");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.verifyCode(SmsSceneEnum.LOGIN, PHONE, "000000"));

        assertTrue(ex.getMessage().contains("验证码不正确"));
        assertEquals("1", redisStore.get(failKey(SmsSceneEnum.LOGIN)));
        assertEquals("123456", redisStore.get(codeKey(SmsSceneEnum.LOGIN)), "错误尝试不应作废验证码");
    }

    @Test
    void verifyCodeAttemptsExhaustedInvalidatesCode() {
        redisStore.put(codeKey(SmsSceneEnum.LOGIN), "123456");
        redisStore.put(failKey(SmsSceneEnum.LOGIN), String.valueOf(SmsConsts.MAX_VERIFY_ATTEMPTS));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.verifyCode(SmsSceneEnum.LOGIN, PHONE, "123456"));

        assertTrue(ex.getMessage().contains("验证码输错次数过多"));
        assertNull(redisStore.get(codeKey(SmsSceneEnum.LOGIN)), "超限后旧验证码应作废");
        assertNull(redisStore.get(failKey(SmsSceneEnum.LOGIN)));
    }

    @Test
    void sendCodeWithoutIpSkipsIpQuotaAccounting() {
        when(userRepository.findByPhone(PHONE)).thenReturn(null);

        service.sendRegisterCode(PHONE, null);

        assertFalse(redisStore.containsKey(ipDayKey()), "IP 为空时不应产生当日 IP 计数");
        verify(smsGateway).sendVerificationCode(eq(PHONE), anyString());
    }

    @Test
    void isRegisterVerifyEnabledDelegatesToPlatformSetting() {
        when(platformSettingService.isEnabled(PlatformSettingKeyEnum.SMS_VERIFY_ENABLED))
                .thenReturn(true).thenReturn(false);

        assertTrue(service.isRegisterVerifyEnabled());
        assertFalse(service.isRegisterVerifyEnabled());
    }

    @Test
    void isSmsLoginEnabledDelegatesToPlatformSetting() {
        when(platformSettingService.isEnabled(PlatformSettingKeyEnum.SMS_LOGIN_ENABLED))
                .thenReturn(true).thenReturn(false);

        assertTrue(service.isSmsLoginEnabled());
        assertFalse(service.isSmsLoginEnabled());
    }
}
