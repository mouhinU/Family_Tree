package com.mouhin.family.tree.web.interceptor;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * 写接口限流拦截器。
 * <p>
 * 基于 Caffeine 滑动窗口计数器，按用户 ID 限流。仅对 POST/PUT/DELETE 请求生效，
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
     * 限流缓存：key=userId，value=窗口内请求计数
     */
    private final Cache<Long, int[]> rateLimitCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(FamilyTreeConsts.RATE_LIMIT_WINDOW_SECONDS))
            .build();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String method = request.getMethod().toUpperCase();
        // 仅对写操作限流
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

        int[] counter = rateLimitCache.get(userId, k -> new int[]{0});
        counter[0]++;

        if (counter[0] > FamilyTreeConsts.RATE_LIMIT_MAX_REQUESTS) {
            logger.warn("Rate limit exceeded: userId={}, path={}, count={}",
                    userId, request.getRequestURI(), counter[0]);
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
