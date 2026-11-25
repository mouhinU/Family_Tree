package com.mouhin.family.tree.web.interceptor;

import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.web.ratelimit.RateLimit;
import com.mouhin.family.tree.web.ratelimit.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.method.HandlerMethod;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 注解式限流拦截器单测：方法级/类级声明的解析与遮蔽、重复注解逐条裁决、
 * 各维度的计数桶、匿名请求下的默认写限流，以及 429 响应体。
 *
 * @author Family-Tree
 * @date 2026-11-25
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimitInterceptorTest {

    private static final String REAL_CLIENT_IP = "203.0.113.66";

    @Mock
    private RateLimiter rateLimiter;

    private RateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new RateLimitInterceptor(rateLimiter);
        when(rateLimiter.tryAcquire(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(new RateLimiter.Decision(true, 1, 60));
    }

    // ========== 被测声明 ==========

    private static class FixtureController {

        void plain() {
        }

        @RateLimit(name = "auth-ip", dimension = RateLimit.Dimension.IP, windowSeconds = 60, maxRequests = 30,
                message = "登录尝试过于频繁，请稍后再试")
        @RateLimit(name = "auth-ip-slow", dimension = RateLimit.Dimension.IP, windowSeconds = 600, maxRequests = 120,
                message = "登录尝试过于频繁，请稍后再试")
        void login() {
        }

        @RateLimit(name = "user-write", dimension = RateLimit.Dimension.USER, windowSeconds = 60, maxRequests = 30)
        void userScoped() {
        }

        @RateLimit(name = "global-heavy", dimension = RateLimit.Dimension.GLOBAL, windowSeconds = 60, maxRequests = 2)
        void globalScoped() {
        }
    }

    /** 类级声明：未标注的方法继承，标注了的方法整体遮蔽（不是叠加） */
    @RateLimit(name = "class-ip", dimension = RateLimit.Dimension.IP, windowSeconds = 60, maxRequests = 5)
    private static class ClassLevelController {

        void inherited() {
        }

        @RateLimit(name = "method-ip", dimension = RateLimit.Dimension.IP, windowSeconds = 60, maxRequests = 30)
        void overriding() {
        }
    }

    // ========== 请求构造 ==========

    private MockHttpServletRequest request(String method, String uri, Long userId) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        // 生产形态：nginx 从环回转发，真实来源只来自代理头
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "9.9.9.9, " + REAL_CLIENT_IP);
        if (userId != null) {
            MockHttpSession session = new MockHttpSession();
            session.setAttribute(FamilyTreeConsts.SESSION_USER_ID, userId);
            request.setSession(session);
        }
        return request;
    }

    private HandlerMethod handler(String methodName) throws NoSuchMethodException {
        return new HandlerMethod(new FixtureController(), FixtureController.class.getDeclaredMethod(methodName));
    }

    private HandlerMethod classLevelHandler(String methodName) throws NoSuchMethodException {
        return new HandlerMethod(new ClassLevelController(), ClassLevelController.class.getDeclaredMethod(methodName));
    }

    // ========== 声明解析 ==========

    @Test
    void unannotatedAnonymousWriteIsNotLimited() throws Exception {
        assertTrue(interceptor.preHandle(request("POST", "/api/auth/login", null),
                new MockHttpServletResponse(), handler("plain")));
        verify(rateLimiter, never()).tryAcquire(anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void unannotatedWriteFallsBackToPerUserDefault() throws Exception {
        assertTrue(interceptor.preHandle(request("POST", "/api/node", 7L),
                new MockHttpServletResponse(), handler("plain")));
        verify(rateLimiter).tryAcquire("write", "7", FamilyTreeConsts.RATE_LIMIT_WINDOW_SECONDS,
                FamilyTreeConsts.RATE_LIMIT_MAX_REQUESTS);
    }

    @Test
    void classLevelRuleAppliesToUnannotatedMethod() throws Exception {
        assertTrue(interceptor.preHandle(request("GET", "/api/tree", null),
                new MockHttpServletResponse(), classLevelHandler("inherited")));
        verify(rateLimiter).tryAcquire("class-ip", REAL_CLIENT_IP, 60, 5);
    }

    @Test
    void methodLevelRulesShadowClassLevelAndBothAreEvaluated() throws Exception {
        assertTrue(interceptor.preHandle(request("POST", "/api/auth/login", null),
                new MockHttpServletResponse(), handler("login")));

        ArgumentCaptor<String> names = ArgumentCaptor.forClass(String.class);
        verify(rateLimiter, times(2)).tryAcquire(names.capture(), anyString(), anyInt(), anyInt());
        assertEquals(List.of("auth-ip", "auth-ip-slow"), names.getAllValues(), "两条声明都要跑，且顺序稳定");
    }

    @Test
    void methodLevelDeclarationReplacesInsteadOfStackingOntoClassLevel() throws Exception {
        assertTrue(interceptor.preHandle(request("POST", "/api/auth/login", null),
                new MockHttpServletResponse(), classLevelHandler("overriding")));
        verify(rateLimiter, times(1)).tryAcquire("method-ip", REAL_CLIENT_IP, 60, 30);
        verify(rateLimiter, never()).tryAcquire(eq("class-ip"), anyString(), anyInt(), anyInt());
    }

    @Test
    void globalDimensionSharesOneBucketAcrossCallers() throws Exception {
        interceptor.preHandle(request("POST", "/api/admin/repair", 7L), new MockHttpServletResponse(),
                handler("globalScoped"));
        interceptor.preHandle(request("POST", "/api/admin/repair", 8L), new MockHttpServletResponse(),
                handler("globalScoped"));

        verify(rateLimiter, times(2)).tryAcquire("global-heavy", "global", 60, 2);
    }

    // ========== 维度与桶 ==========

    @Test
    void ipBucketUsesResolvedClientIp() throws Exception {
        interceptor.preHandle(request("POST", "/api/auth/login", null), new MockHttpServletResponse(), handler("login"));

        ArgumentCaptor<String> buckets = ArgumentCaptor.forClass(String.class);
        verify(rateLimiter, times(2)).tryAcquire(anyString(), buckets.capture(), anyInt(), anyInt());
        for (String bucket : buckets.getAllValues()) {
            assertEquals(REAL_CLIENT_IP, bucket, "客户端可自填的 XFF 最左一段不能当限流桶");
        }
    }

    @Test
    void userDimensionRuleIsSkippedForAnonymous() throws Exception {
        assertTrue(interceptor.preHandle(request("POST", "/api/node", null),
                new MockHttpServletResponse(), handler("userScoped")));
        verify(rateLimiter, never()).tryAcquire(anyString(), anyString(), anyInt(), anyInt());
    }

    // ========== 拒绝响应 ==========

    @Test
    void secondRuleRejectionCarriesItsOwnMessage() throws Exception {
        when(rateLimiter.tryAcquire(eq("auth-ip"), anyString(), anyInt(), anyInt()))
                .thenReturn(new RateLimiter.Decision(true, 1, 60));
        when(rateLimiter.tryAcquire(eq("auth-ip-slow"), anyString(), anyInt(), anyInt()))
                .thenReturn(new RateLimiter.Decision(false, 121, 37));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request("POST", "/api/auth/login", null), response, handler("login")));
        assertEquals(429, response.getStatus());
        assertEquals("37", response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("登录尝试过于频繁"));
    }

    @Test
    void nonHandlerMethodRequestsAreLeftAlone() throws Exception {
        assertTrue(interceptor.preHandle(request("GET", "/static/login.html", null),
                new MockHttpServletResponse(), new Object()));
        verify(rateLimiter, never()).tryAcquire(anyString(), anyString(), anyInt(), anyInt());
    }
}
