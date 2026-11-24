package com.mouhin.family.tree.web.config;

import com.mouhin.family.tree.web.filter.CsrfFilter;
import com.mouhin.family.tree.web.filter.SecurityHeadersFilter;
import com.mouhin.family.tree.web.interceptor.FamilyStatusInterceptor;
import com.mouhin.family.tree.web.interceptor.LoginInterceptor;
import com.mouhin.family.tree.web.interceptor.RateLimitInterceptor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 免登录公开接口路径：口令登录/注册与短信验证码/短信登录，及登录页读取公开开关的配置接口，
     * 三个拦截器共用同一份排除名单，避免逐处维护漂移
     */
    private static final String[] PUBLIC_API_PATHS = {
            "/api/auth/login", "/api/auth/register",
            "/api/auth/login-sms", "/api/auth/sms/send-code",
            "/api/auth/config"
    };

    private final LoginInterceptor loginInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;
    private final FamilyStatusInterceptor familyStatusInterceptor;

    public WebMvcConfig(LoginInterceptor loginInterceptor,
                        RateLimitInterceptor rateLimitInterceptor,
                        FamilyStatusInterceptor familyStatusInterceptor) {
        this.loginInterceptor = loginInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.familyStatusInterceptor = familyStatusInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(PUBLIC_API_PATHS);

        // 家族冻结 / 归档治理（在登录认证之后、限流之前执行）
        registry.addInterceptor(familyStatusInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(PUBLIC_API_PATHS);

        // 写接口限流（在登录认证之后执行）
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(PUBLIC_API_PATHS);
    }

    /**
     * 安全响应头过滤器：为所有响应添加安全 HTTP 头
     */
    @Bean
    public FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilter() {
        FilterRegistrationBean<SecurityHeadersFilter> bean = new FilterRegistrationBean<>(new SecurityHeadersFilter());
        bean.setOrder(1);
        return bean;
    }

    /**
     * CSRF 防护过滤器：校验状态变更请求的 CSRF Token
     */
    @Bean
    public FilterRegistrationBean<CsrfFilter> csrfFilter() {
        FilterRegistrationBean<CsrfFilter> bean = new FilterRegistrationBean<>(new CsrfFilter());
        bean.setOrder(2);
        return bean;
    }
}
