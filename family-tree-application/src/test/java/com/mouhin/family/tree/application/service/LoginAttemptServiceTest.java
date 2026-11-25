package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.constant.LoginSecurityConsts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录失败计数服务单测：正常时以 Redis 计数裁决锁定，Redis 不可用时降级为"不锁定"
 * 且不向上抛异常（避免登录接口把可诊断的业务失败变成 500）。
 *
 * @author Family-Tree
 * @date 2026-11-25
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginAttemptServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService(stringRedisTemplate);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void locksWhenRedisCountReachesThreshold() {
        when(valueOperations.get("login:attempt:zhangsan"))
                .thenReturn(String.valueOf(LoginSecurityConsts.MAX_FAILED_ATTEMPTS));

        assertTrue(service.isLocked("zhangsan"));
    }

    @Test
    void staysUnlockedBelowThreshold() {
        when(valueOperations.get("login:attempt:zhangsan"))
                .thenReturn(String.valueOf(LoginSecurityConsts.MAX_FAILED_ATTEMPTS - 1));

        assertFalse(service.isLocked("zhangsan"));
    }

    @Test
    void firstFailureStartsLockWindowTtl() {
        when(valueOperations.increment("login:attempt:zhangsan")).thenReturn(1L);

        service.recordFailure("zhangsan");

        verify(stringRedisTemplate).expire(eq("login:attempt:zhangsan"),
                eq(Duration.ofMinutes(LoginSecurityConsts.LOCK_MINUTES)));
    }

    @Test
    void laterFailureKeepsOriginalTtl() {
        when(valueOperations.increment("login:attempt:zhangsan")).thenReturn(3L);

        service.recordFailure("zhangsan");

        verify(stringRedisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void successClearsRedisCounter() {
        service.recordSuccess("zhangsan");

        verify(stringRedisTemplate).delete("login:attempt:zhangsan");
    }

    @Test
    void readOutageDegradesToUnlockedInsteadOfThrowing() {
        when(valueOperations.get(anyString()))
                .thenThrow(new RedisConnectionFailureException("Unable to connect to Redis"));

        assertEquals(0, service.getFailedAttempts("zhangsan"));
        assertFalse(service.isLocked("zhangsan"), "Redis 不可用时不锁人，误锁正常用户的代价高于暂时放宽");
    }

    @Test
    void writeOutageDoesNotBreakLoginFlow() {
        when(valueOperations.increment(anyString()))
                .thenThrow(new RedisConnectionFailureException("Unable to connect to Redis"));
        when(stringRedisTemplate.delete(anyString()))
                .thenThrow(new RedisConnectionFailureException("Unable to connect to Redis"));

        assertDoesNotThrow(() -> service.recordFailure("zhangsan"));
        assertDoesNotThrow(() -> service.recordSuccess("zhangsan"));
    }

    @Test
    void corruptValueCountsAsZero() {
        when(valueOperations.get("login:attempt:zhangsan")).thenReturn("not-a-number");

        assertEquals(0, service.getFailedAttempts("zhangsan"));
    }

    @Test
    void blankUsernameNeverTouchesRedis() {
        service.recordFailure(" ");
        service.clearAttempts(null);

        assertEquals(0, service.getFailedAttempts(""));
        verify(stringRedisTemplate, never()).delete(anyString());
    }
}
