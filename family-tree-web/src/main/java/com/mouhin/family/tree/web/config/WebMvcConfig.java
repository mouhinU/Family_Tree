package com.mouhin.family.tree.web.config;

import com.mouhin.family.tree.web.filter.CsrfFilter;
import com.mouhin.family.tree.web.filter.SecurityHeadersFilter;
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

    private final LoginInterceptor loginInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    public WebMvcConfig(LoginInterceptor loginInterceptor,
                        RateLimitInterceptor rateLimitInterceptor) {
        this.loginInterceptor = loginInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/auth/register");

        // 写接口限流（在登录认证之后执行）
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/auth/register");
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
