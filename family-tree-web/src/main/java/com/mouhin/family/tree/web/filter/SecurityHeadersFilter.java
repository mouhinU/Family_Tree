package com.mouhin.family.tree.web.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 安全响应头过滤器。
 * <p>
 * 为所有响应添加安全相关的 HTTP 头，防止常见的 Web 攻击：
 * <ul>
 *   <li>X-Frame-Options: DENY — 防止点击劫持（禁止任何站点嵌入 iframe）</li>
 *   <li>X-Content-Type-Options: nosniff — 禁止浏览器 MIME 类型嗅探</li>
 *   <li>X-XSS-Protection: 0 — 禁用浏览器内置 XSS 过滤器（现代浏览器推荐设为 0，依赖 CSP）</li>
 *   <li>Content-Security-Policy — 限制资源加载来源，防止 XSS 与数据注入</li>
 *   <li>Referrer-Policy: strict-origin-when-cross-origin — 跨域请求仅发送 origin</li>
 *   <li>Cache-Control: no-store — 对 API 响应禁止缓存（防止敏感数据泄露）</li>
 * </ul>
 *
 * @author Family-Tree
 * @date 2026-08-04
 */
public class SecurityHeadersFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // 无需初始化
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse httpResp = (HttpServletResponse) response;

        // 防止点击劫持
        httpResp.setHeader("X-Frame-Options", "DENY");

        // 禁止 MIME 类型嗅探
        httpResp.setHeader("X-Content-Type-Options", "nosniff");

        // 禁用浏览器 XSS 过滤器（现代浏览器推荐依赖 CSP）
        httpResp.setHeader("X-XSS-Protection", "0");

        // 内容安全策略：允许同源资源 + CDN（D3.js / jsPDF），不使用 unsafe-inline
        // script-src 已移除 unsafe-inline：所有动态按钮通过 data-* 属性 + addEventListener 绑定
        httpResp.setHeader("Content-Security-Policy",
                "default-src 'self'; "
                        + "script-src 'self' https://cdnjs.cloudflare.com; "
                        + "style-src 'self' 'unsafe-inline'; "
                        + "img-src 'self' data:; "
                        + "font-src 'self' data:; "
                        + "connect-src 'self' ws: wss:");

        // Referrer 策略
        httpResp.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // API 响应不缓存（静态资源由 Spring 默认处理）
        String uri = ((jakarta.servlet.http.HttpServletRequest) request).getRequestURI();
        if (uri.startsWith("/api/")) {
            httpResp.setHeader("Cache-Control", "no-store");
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // 无需清理
    }
}
