package com.mouhin.family.tree.web.security;

import com.mouhin.family.tree.application.service.PlatformAdminApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.exception.BusinessException;
import jakarta.servlet.http.HttpSession;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 平台管理员切面单元测试：覆盖白名单放行、非白名单拒绝、会话缺失的失败关闭，
 * 以及经代理驱动真实注解时的拦截生效。
 *
 * @author Family-Tree
 * @date 2026-11-15
 */
@ExtendWith(MockitoExtension.class)
class SystemAccessAspectTest {

    private static final Long USER_ID = 7L;
    private static final String PROCEEDED = "proceeded";

    @Mock
    private PlatformAdminApplicationService platformAdminService;

    private SystemAccessAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new SystemAccessAspect(platformAdminService);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void bindUser(Long userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        HttpSession session = new MockHttpSession();
        if (userId != null) {
            session.setAttribute(FamilyTreeConsts.SESSION_USER_ID, userId);
        }
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private ProceedingJoinPoint joinPoint() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        lenient().when(joinPoint.proceed()).thenReturn(PROCEEDED);
        return joinPoint;
    }

    @Test
    void whitelistedUserProceeds() throws Throwable {
        bindUser(USER_ID);
        when(platformAdminService.isPlatformAdmin(USER_ID)).thenReturn(true);
        ProceedingJoinPoint joinPoint = joinPoint();

        Object result = aspect.checkSystemAdmin(joinPoint, annotation());

        assertEquals(PROCEEDED, result);
        verify(joinPoint).proceed();
    }

    @Test
    void nonWhitelistedUserIsBlockedWithoutProceed() throws Throwable {
        bindUser(USER_ID);
        when(platformAdminService.isPlatformAdmin(USER_ID)).thenReturn(false);
        ProceedingJoinPoint joinPoint = joinPoint();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> aspect.checkSystemAdmin(joinPoint, annotation()));

        assertEquals("无系统管理权限", ex.getMessage());
        verify(joinPoint, never()).proceed();
    }

    @Test
    void missingSessionFailsClosedWithoutConsultingWhitelist() throws Throwable {
        bindUser(null);
        ProceedingJoinPoint joinPoint = joinPoint();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> aspect.checkSystemAdmin(joinPoint, annotation()));

        assertEquals("未登录", ex.getMessage());
        verify(platformAdminService, never()).isPlatformAdmin(USER_ID);
        verify(joinPoint, never()).proceed();
    }

    @Test
    void absentRequestContextFailsClosed() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> aspect.checkSystemAdmin(joinPoint, annotation()));

        assertEquals("未登录", ex.getMessage());
        verify(joinPoint, never()).proceed();
    }

    @Test
    void proxiedAdminCallIsIntercepted() {
        bindUser(USER_ID);
        when(platformAdminService.isPlatformAdmin(USER_ID)).thenReturn(true);

        assertEquals("guarded", newProxy().guarded());

        verify(platformAdminService).isPlatformAdmin(USER_ID);
    }

    @Test
    void proxiedAdminCallIsDeniedForOrdinaryUser() {
        bindUser(USER_ID);
        when(platformAdminService.isPlatformAdmin(USER_ID)).thenReturn(false);

        assertThrows(BusinessException.class, () -> newProxy().guarded());
    }

    private RequireSystemAdmin annotation() throws NoSuchMethodException {
        return SampleApi.class.getMethod("guarded").getAnnotation(RequireSystemAdmin.class);
    }

    private SampleApi newProxy() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleApi());
        factory.addAspect(aspect);
        return factory.getProxy();
    }

    /**
     * 采样端点：仅提供注解供代理驱动，非 Spring Bean。
     */
    public static class SampleApi {

        @RequireSystemAdmin
        public String guarded() {
            return "guarded";
        }
    }
}
