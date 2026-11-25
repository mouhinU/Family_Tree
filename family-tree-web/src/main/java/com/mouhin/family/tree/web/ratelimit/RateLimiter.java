package com.mouhin.family.tree.web.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 固定窗口计数器：本地（Caffeine）与 Redis **双写**，判定取两者较大值。
 *
 * <p>为什么两条都写：这台机器上两种失效模式都真实发生过——Redis 侧，firewalld 重启曾把容器间
 * TCP 掐断，纯 Redis 计数会直接退化成「不限流」；进程侧，蓝绿切流与重启会让 JVM 内的计数归零，
 * 纯本地计数等于给攻击者重发一份额度。取大之后两条各自补对方的洞：</p>
 *
 * <ul>
 *   <li>Redis 可用：以 Redis 为准（跨槽、跨重启共享），本地值只会更小而被 max 抹掉，不额外放行；</li>
 *   <li>Redis 不可用：降级为纯本地计数，仍然挡得住遍历，且判定路径上没有网络等待；</li>
 *   <li>本地被容量淘汰或进程重启：Redis 那份仍在，不会重新放出额度。</li>
 * </ul>
 *
 * <p>窗口序号编在 key 里，所以本地条目的过期时间只是内存回收，多留一会儿不影响判定；
 * 淘汰导致的本地归零可接受，因为 Redis 是账本、本地只是兜底副本。</p>
 *
 * @author Family-Tree
 * @date 2026-11-25
 */
@Component
public class RateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimiter.class);

    /**
     * Redis 计数 key 前缀：{@code rate:limit:{name}:{维度值}:{窗口序号}}
     */
    private static final String REDIS_KEY_PREFIX = "rate:limit:";
    /**
     * 本地窗口条目的存活时长，取所有已用窗口上限的若干倍，只为回收内存
     */
    private static final Duration LOCAL_RETENTION = Duration.ofHours(1);
    /**
     * 本地窗口条目上限。按「维度值 × 活跃窗口」估：家庭用户规模下远不到，
     * 真被淘汰也只是退回 Redis 判定，不会放宽限制
     */
    private static final long LOCAL_MAX_SIZE = 50_000L;

    private final Cache<String, AtomicLong> localWindows;
    private final StringRedisTemplate stringRedisTemplate;

    public RateLimiter(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.localWindows = Caffeine.newBuilder()
                .maximumSize(LOCAL_MAX_SIZE)
                .expireAfterWrite(LOCAL_RETENTION)
                .build();
    }

    /**
     * 记一次请求并裁决。
     *
     * @param name          计数命名空间（{@link RateLimit#name()}）
     * @param dimensionValue 维度值（IP、用户ID 或固定串）
     * @param windowSeconds 窗口长度
     * @param maxRequests   窗口内允许的请求数
     * @return 裁决结果，含本请求在窗口内的序号与建议的 Retry-After
     */
    public Decision tryAcquire(String name, String dimensionValue, int windowSeconds, int maxRequests) {
        long nowMillis = System.currentTimeMillis();
        long slot = nowMillis / (windowSeconds * 1000L);
        String suffix = name + ":" + dimensionValue + ":" + slot;

        long localCount = localCount(suffix);
        long remoteCount = remoteCount(REDIS_KEY_PREFIX + suffix, windowSeconds);
        long count = Math.max(localCount, remoteCount);

        // 窗口末尾只剩 1 秒也报 1，别让 Retry-After 出现 0 导致前端立刻重试形成自旋
        int retryAfterSeconds = Math.max(1, windowSeconds - (int) (nowMillis / 1000L % windowSeconds));
        return new Decision(count <= maxRequests, count, retryAfterSeconds);
    }

    /**
     * 本地窗口自增。缓存抖动不该让认证入口 500，故不吞异常也不返回负值——这里的语义是「至少这么多」。
     */
    private long localCount(String suffix) {
        return localWindows.get(suffix, key -> new AtomicLong()).incrementAndGet();
    }

    /**
     * Redis 窗口自增，失败返回 -1（不可用）而不是 0，让调用方的 max 落到本地值上。
     */
    private long remoteCount(String key, int windowSeconds) {
        try {
            Long value = stringRedisTemplate.opsForValue().increment(key);
            if (value != null && value == 1L) {
                // 序号已隔离窗口，TTL 只负责回收历史 key，多给 60s 余量避免跨界丢计数
                stringRedisTemplate.expire(key, Duration.ofSeconds(windowSeconds + 60L));
            }
            return value == null ? 0L : value;
        } catch (RuntimeException e) {
            logger.warn("限流计数写 Redis 失败，本次仅按本地窗口判定: key={}, cause={}", key, e.getMessage());
            return -1L;
        }
    }

    /**
     * 单条规则的裁决结果。
     *
     * @param allowed           是否放行
     * @param count             本请求在窗口内的序号（Redis 不可用时等于本地序号）
     * @param retryAfterSeconds 距离窗口结束的秒数，用于 429 的 Retry-After
     */
    public record Decision(boolean allowed, long count, int retryAfterSeconds) {
    }
}
