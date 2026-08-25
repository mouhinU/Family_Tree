package com.mouhin.family.tree.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mouhin.family.tree.common.result.Result;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.UUID;

/**
 * CSRF 防护过滤器。
 * <p>
 * 采用 Synchronizer Token Pattern：登录时生成 CSRF Token 存入 Session，
 * 前端通过 X-CSRF-TOKEN 请求头回传，服务端校验一致性。
 * <p>
 * 仅对状态变更方法（POST/PUT/DELETE）生效；GET/HEAD/OPTIONS 放行。
 * 登录/注册接口本身无 Session 依赖，豁免校验。
 *
 * @author Family-Tree
 * @date 2026-08-04
 */
public class CsrfFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(CsrfFilter.class);

    /**
     * Session 中存储 CSRF Token 的 key
     */
    public static final String CSRF_TOKEN_SESSION_KEY = "CSRF_TOKEN";

    /**
     * 前端回传 CSRF Token 的请求头名称
     */
    public static final String CSRF_TOKEN_HEADER = "X-CSRF-TOKEN";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // 无需初始化
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        String method = httpReq.getMethod().toUpperCase();

        // GET / HEAD / OPTIONS 不校验
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            chain.doFilter(request, response);
            return;
        }

        String path = httpReq.getRequestURI();

        // 豁免路径：登录、注册（此时尚无 Session）
        if (path.equals("/api/auth/login") || path.equals("/api/auth/register")) {
            chain.doFilter(request, response);
            return;
        }

        // 校验 CSRF Token
        HttpSession session = httpReq.getSession(false);
        String sessionToken = session != null ? (String) session.getAttribute(CSRF_TOKEN_SESSION_KEY) : null;
        String headerToken = httpReq.getHeader(CSRF_TOKEN_HEADER);

        if (sessionToken == null || !sessionToken.equals(headerToken)) {
            logger.warn("CSRF token validation failed: path={}, method={}, ip={}", path, method, httpReq.getRemoteAddr());
            httpResp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResp.setContentType("application/json;charset=UTF-8");
            httpResp.getWriter().write(OBJECT_MAPPER.writeValueAsString(Result.fail(403, "CSRF 校验失败，请刷新页面后重试")));
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // 无需清理
    }

    /**
     * 生成新的 CSRF Token 并存入 Session。
     * 在登录成功后调用。
     *
     * @param session 当前 HTTP Session
     * @return 生成的 Token
     */
    public static String generateToken(HttpSession session) {
        String token = UUID.randomUUID().toString();
        session.setAttribute(CSRF_TOKEN_SESSION_KEY, token);
        return token;
    }
}
