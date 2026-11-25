package com.mouhin.family.tree.web.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式限流：标在 Controller 方法（或整类）上，由 {@code RateLimitInterceptor} 读取并执行。
 *
 * <p>可重复标注多条，逐条独立计数，任一条超限即 429 —— 登录入口就同时挂「短窗挡脚本」和
 * 「长窗挡刻意放慢的遍历」两条。计数落点与阈值边界见 {@link RateLimiter}。</p>
 *
 * <p>未标注的写接口（POST/PUT/DELETE）仍走既有的「按登录用户 60 秒 30 次」默认规则，
 * 所以本注解是用来**加严或改维度**，不是限流的唯一入口。</p>
 *
 * @author Family-Tree
 * @date 2026-11-25
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RateLimit.List.class)
public @interface RateLimit {

    /**
     * 计数命名空间，直接进本地与 Redis 的 key。同名同维度同窗口即共享一份额度，
     * 所以按用途取名（如 {@code login-ip}），不要按接口取名后再复用。
     */
    String name();

    /**
     * 计数维度，决定「谁」共享这份额度
     */
    Dimension dimension() default Dimension.IP;

    /**
     * 固定窗口长度（秒）
     */
    int windowSeconds();

    /**
     * 窗口内允许的请求数，超限的第 N+1 次请求被拒
     */
    int maxRequests();

    /**
     * 被拒时返回给前端的提示语
     */
    String message() default "操作过于频繁，请稍后再试";

    /**
     * 计数维度
     */
    enum Dimension {
        /**
         * 按解析后的客户端 IP（{@code ClientIpResolver} 口径，客户端不能自己填）
         */
        IP,
        /**
         * 按会话用户；匿名请求该条不生效
         */
        USER,
        /**
         * 全局共享一份额度，用于代价极高的接口
         */
        GLOBAL
    }

    /**
     * {@code @Repeatable} 容器，业务代码不直接使用
     */
    @Documented
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @interface List {

        RateLimit[] value();
    }
}
