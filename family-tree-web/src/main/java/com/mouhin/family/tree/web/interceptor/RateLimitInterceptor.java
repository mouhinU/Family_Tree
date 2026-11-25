package com.mouhin.family.tree.web.interceptor;

import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.web.ratelimit.RateLimit;
import com.mouhin.family.tree.web.ratelimit.RateLimiter;
import com.mouhin.family.tree.web.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Set;

/**
 * 限流拦截器：读取目标方法（或其所在类）上的 {@link RateLimit} 声明并逐条裁决，
 * 计数由 {@link RateLimiter} 同时落本地窗口与 Redis。
 *
 * <p>没有标注的接口保留既有的默认规则：POST/PUT/DELETE 按会话用户 60 秒 30 次，匿名请求放行
 * （交给登录入口自己的 IP 规则）。所以本拦截器对 {@code /api/**} 全量注册，是否需要额外额度
 * 由注解决定，公开路径名单不再需要在 {@code WebMvcConfig} 里为限流单独维护一份。</p>
 *
 * <p>方法上的声明覆盖类上的声明，两者不叠加——给单个方法加严时不必再把类级规则重抄一遍。</p>
 *
 * @author Family-Tree
 * @date 2026-08-09
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitInterceptor.class);

    /**
     * 默认写限流的计数命名空间
     */
    private static final String DEFAULT_WRITE_RULE_NAME = "write";
    /**
     * GLOBAL 维度的固定桶名
     */
    private static final String GLOBAL_BUCKET = "global";
    /**
     * 默认规则的提示语（与既有写限流保持一致）
     */
    private static final String DEFAULT_WRITE_MESSAGE = "操作过于频繁，请稍后再试";

    private final RateLimiter rateLimiter;

    public RateLimitInterceptor(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler)
            throws Exception {
        for (Rule rule : resolveRules(request, handler)) {
            String bucket = bucketOf(rule, request);
            if (bucket == null) {
                // 匿名请求没有 USER 维度可数，这条规则对它不适用
                continue;
            }
            RateLimiter.Decision decision =
                    rateLimiter.tryAcquire(rule.name(), bucket, rule.windowSeconds(), rule.maxRequests());
            if (!decision.allowed()) {
                return reject(response, request, rule, decision);
            }
        }
        return true;
    }

    /**
     * 取本次请求要执行的规则：方法级声明优先，其次类级，都没有则用默认写限流规则。
     */
    private List<Rule> resolveRules(HttpServletRequest request, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return List.of();
        }
        Set<RateLimit> declared =
                AnnotatedElementUtils.findMergedRepeatableAnnotations(handlerMethod.getMethod(), RateLimit.class);
        if (declared.isEmpty()) {
            declared = AnnotatedElementUtils
                    .findMergedRepeatableAnnotations(handlerMethod.getBeanType(), RateLimit.class);
        }
        if (declared.isEmpty()) {
            Rule defaultRule = defaultWriteRule(request);
            return defaultRule == null ? List.of() : List.of(defaultRule);
        }
        return declared.stream().map(Rule::of).toList();
    }

    /**
     * 既有的「写接口按用户限流」，保留为未标注接口的兜底，避免逐个控制器铺注解。
     */
    private Rule defaultWriteRule(HttpServletRequest request) {
        if (!isWrite(request.getMethod())) {
            return null;
        }
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(FamilyTreeConsts.SESSION_USER_ID) == null) {
            return null;
        }
        return new Rule(DEFAULT_WRITE_RULE_NAME, RateLimit.Dimension.USER,
                FamilyTreeConsts.RATE_LIMIT_WINDOW_SECONDS, FamilyTreeConsts.RATE_LIMIT_MAX_REQUESTS,
                DEFAULT_WRITE_MESSAGE);
    }

    private boolean isWrite(String method) {
        String upper = method.toUpperCase();
        return "POST".equals(upper) || "PUT".equals(upper) || "DELETE".equals(upper);
    }

    /**
     * 按维度取计数桶；返回 null 表示该规则对本次请求不适用。
     */
    private String bucketOf(Rule rule, HttpServletRequest request) {
        return switch (rule.dimension()) {
            case IP -> ClientIpResolver.resolve(request);
            case USER -> {
                HttpSession session = request.getSession(false);
                Long userId = session == null ? null : (Long) session.getAttribute(FamilyTreeConsts.SESSION_USER_ID);
                yield userId == null ? null : String.valueOf(userId);
            }
            case GLOBAL -> GLOBAL_BUCKET;
        };
    }

    private boolean reject(HttpServletResponse response, HttpServletRequest request, Rule rule,
                           RateLimiter.Decision decision) throws Exception {
        logger.warn("限流命中: rule={}, dimension={}, path={}, count={}, windowSeconds={}",
                rule.name(), rule.dimension(), request.getRequestURI(), decision.count(), rule.windowSeconds());
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        response.getWriter().write("{\"code\":429,\"message\":\"" + rule.message() + "\",\"data\":null}");
        return false;
    }

    /**
     * 一条已解析的限流规则：注解的扁平副本，便于把「默认规则」和「声明式规则」走同一条执行路径。
     *
     * @param name          计数命名空间
     * @param dimension     计数维度
     * @param windowSeconds 窗口长度（秒）
     * @param maxRequests   窗口内允许的请求数
     * @param message       被拒提示语
     */
    private record Rule(String name, RateLimit.Dimension dimension, int windowSeconds, int maxRequests,
                        String message) {

        static Rule of(RateLimit annotation) {
            return new Rule(annotation.name(), annotation.dimension(), annotation.windowSeconds(),
                    annotation.maxRequests(), annotation.message());
        }
    }
}
