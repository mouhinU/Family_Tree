package com.mouhin.family.tree.web.security;

import com.mouhin.family.tree.application.service.PlatformAdminApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.exception.BusinessException;
import jakarta.servlet.http.HttpSession;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 平台管理员端点切面：统一执行 {@link RequireSystemAdmin} 声明的身份裁决。
 *
 * <p>裁决完全在服务端进行，前端隐藏入口只用于体验优化，不构成安全边界。
 * 未登录或缺席白名单均抛出 {@link BusinessException}，由全局异常处理器统一转化返回。</p>
 *
 * @author Family-Tree
 * @date 2026-11-15
 */
@Aspect
@Component
public class SystemAccessAspect {

    private static final Logger logger = LoggerFactory.getLogger(SystemAccessAspect.class);

    /**
     * 会话缺失（未登录）时的统一提示
     */
    private static final String NO_SESSION_MESSAGE = "未登录";

    private final PlatformAdminApplicationService platformAdminService;

    public SystemAccessAspect(PlatformAdminApplicationService platformAdminService) {
        this.platformAdminService = platformAdminService;
    }

    /**
     * 平台管理员端点裁决
     *
     * @param joinPoint         连接点
     * @param requireSystemAdmin 注解实例
     * @return 方法执行结果
     * @throws Throwable 目标方法抛出的异常
     */
    @Around("@annotation(requireSystemAdmin)")
    public Object checkSystemAdmin(ProceedingJoinPoint joinPoint,
                                   RequireSystemAdmin requireSystemAdmin) throws Throwable {
        Long userId = currentUserId();
        if (userId == null) {
            throw new BusinessException(NO_SESSION_MESSAGE);
        }
        if (!platformAdminService.isPlatformAdmin(userId)) {
            logger.warn("Blocked platform admin endpoint for userId: {}", userId);
            throw new BusinessException("无系统管理权限");
        }
        return joinPoint.proceed();
    }

    /**
     * 读取当前会话中的用户ID（登录拦截器已保证存在，缺失时返回 null 由裁决失败关闭）
     */
    private Long currentUserId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            logger.debug("No servlet request bound to current thread while enforcing platform admin access");
            return null;
        }
        HttpSession session = servletAttributes.getRequest().getSession(false);
        return session == null ? null : (Long) session.getAttribute(FamilyTreeConsts.SESSION_USER_ID);
    }
}
