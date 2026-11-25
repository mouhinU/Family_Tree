package com.mouhin.family.tree.web.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 双写限流计数器单测：Redis 与本地谁大用谁、Redis 故障时降级为本地计数（不放开限制）、
 * 以及两处 key 都带上窗口序号与命名空间。
 *
 * @author Family-Tree
 * @date 2026-11-25
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimiterTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new RateLimiter(stringRedisTemplate);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void redisLedgerWinsWhenHigherThanLocal() {
        // 模拟另一槽（或重启前的自己）已经消耗掉大半额度
        when(valueOperations.increment(anyString())).thenReturn(99L);

        RateLimiter.Decision decision = limiter.tryAcquire("auth-ip", "203.0.113.66", 60, 30);

        assertFalse(decision.allowed(), "Redis 已记到 99 次，本地才 1 次也要拒");
        assertEquals(99, decision.count());
    }

    @Test
    void localCountEnforcesWhenRedisUnavailable() {
        when(valueOperations.increment(anyString())).thenThrow(new IllegalStateException("redis down"));

        boolean rejectedBeforeQuota = false;
        for (int i = 1; i <= 30; i++) {
            rejectedBeforeQuota |= !limiter.tryAcquire("auth-ip", "203.0.113.66", 60, 30).allowed();
        }
        assertFalse(rejectedBeforeQuota, "额度内不能误伤");
        assertFalse(limiter.tryAcquire("auth-ip", "203.0.113.66", 60, 30).allowed(),
                "Redis 挂了要退化成本地计数，而不是放开限制");
    }

    @Test
    void differentNamesAndBucketsAreIndependent() {
        when(valueOperations.increment(anyString())).thenReturn(1L);

        for (int i = 0; i < 30; i++) {
            assertTrue(limiter.tryAcquire("auth-ip", "203.0.113.66", 60, 30).allowed());
        }
        assertTrue(limiter.tryAcquire("auth-ip", "203.0.113.67", 60, 30).allowed(), "换个 IP 不该被别人的额度挡住");
        assertTrue(limiter.tryAcquire("write", "203.0.113.66", 60, 30).allowed(), "换个命名空间同理");
    }

    @Test
    void writesBothLocalAndRedisKeyWithWindowSlot() {
        when(valueOperations.increment(anyString())).thenReturn(1L);
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);

        limiter.tryAcquire("auth-ip", "203.0.113.66", 60, 30);

        verify(valueOperations, times(1)).increment(keys.capture());
        String redisKey = keys.getValue();
        assertTrue(redisKey.startsWith("rate:limit:auth-ip:203.0.113.66:"), redisKey);
        // 首次自增即设 TTL，否则跨窗口留下的 key 永远回收不掉
        verify(stringRedisTemplate).expire(any(String.class), any(Duration.class));
    }

    @Test
    void retryAfterStaysInsideWindow() {
        when(valueOperations.increment(anyString())).thenReturn(1L);

        RateLimiter.Decision decision = limiter.tryAcquire("auth-ip", "203.0.113.66", 60, 30);

        assertTrue(decision.retryAfterSeconds() >= 1 && decision.retryAfterSeconds() <= 60,
                "Retry-After 必须在窗口内且不为 0，否则前端会立即重试形成自旋");
    }
}
