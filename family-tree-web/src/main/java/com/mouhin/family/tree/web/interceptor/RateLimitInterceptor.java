package com.mouhin.family.tree.web.interceptor;

import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * 写接口限流拦截器。
 * <p>
 * 基于 Redis 原子计数器（INCR），按用户 ID 限流。仅对 POST/PUT/DELETE 请求生效，
 * 防止已登录用户（或自动化脚本）短时间内大量调用写接口。
 * 超限返回 HTTP 429 Too Many Requests。
 *
 * @author Family-Tree
 * @date 2026-08-09
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitInterceptor.class);

    /**
     * Redis Key 前缀
     */
    private static final String RATE_LIMIT_KEY_PREFIX = "rate:limit:";

    private final StringRedisTemplate stringRedisTemplate;

    public RateLimitInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler)
            throws Exception {
        String method = request.getMethod().toUpperCase();
        if (!"POST".equals(method) && !"PUT".equals(method) && !"DELETE".equals(method)) {
            return true;
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            return true;
        }
        Long userId = (Long) session.getAttribute(FamilyTreeConsts.SESSION_USER_ID);
        if (userId == null) {
            return true;
        }

        String key = RATE_LIMIT_KEY_PREFIX + userId;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, Duration.ofSeconds(FamilyTreeConsts.RATE_LIMIT_WINDOW_SECONDS));
        }

        if (count != null && count > FamilyTreeConsts.RATE_LIMIT_MAX_REQUESTS) {
            logger.warn("Rate limit exceeded: userId={}, path={}, count={}",
                    userId, request.getRequestURI(), count);
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Retry-After", String.valueOf(FamilyTreeConsts.RATE_LIMIT_WINDOW_SECONDS));
            response.getWriter().write(
                    "{\"code\":429,\"message\":\"操作过于频繁，请稍后再试\",\"data\":null}");
            return false;
        }

        return true;
    }
}
