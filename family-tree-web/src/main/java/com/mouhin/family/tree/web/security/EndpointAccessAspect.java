package com.mouhin.family.tree.web.security;

import com.mouhin.family.tree.application.service.FamilyNodeVisibilityApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.exception.BusinessException;
import jakarta.servlet.http.HttpSession;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Collection;

/**
 * 端点权限切面：统一执行 {@link RequireFamilyManager} 与 {@link RequireNodeAccess} 声明的权限裁决。
 *
 * <p>切面在拦截器（登录认证、限流）之后、控制器方法体与 {@code @Transactional} 之前运行，
 * 从当前 HTTP 会话读取 familyId / userId，将裁决委托给
 * {@link FamilyNodeVisibilityApplicationService}，使各端点无需再内联编写鉴权代码。
 * 越权时抛出 {@link BusinessException}，由全局异常处理器统一转化为业务错误码返回。</p>
 *
 * @author Family-Tree
 * @date 2026-11-12
 */
@Aspect
@Component
public class EndpointAccessAspect {

    private static final Logger logger = LoggerFactory.getLogger(EndpointAccessAspect.class);

    /**
     * 未加入 / 未创建家族时的统一提示，与控制器基类保持一致
     */
    private static final String NO_FAMILY_MESSAGE = "请先加入或创建家族";

    private final FamilyNodeVisibilityApplicationService visibilityService;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public EndpointAccessAspect(FamilyNodeVisibilityApplicationService visibilityService) {
        this.visibilityService = visibilityService;
    }

    /**
     * 家族管理者端点裁决
     *
     * @param joinPoint          连接点
     * @param requireFamilyManager 注解实例
     * @return 方法执行结果
     * @throws Throwable 目标方法抛出的异常
     */
    @Around("@annotation(requireFamilyManager)")
    public Object checkManager(ProceedingJoinPoint joinPoint,
                               RequireFamilyManager requireFamilyManager) throws Throwable {
        visibilityService.assertManager(currentFamilyId(), currentUserId());
        return joinPoint.proceed();
    }

    /**
     * 按节点查看 / 操作端点裁决
     *
     * @param joinPoint        连接点
     * @param requireNodeAccess 注解实例
     * @return 方法执行结果
     * @throws Throwable 目标方法抛出的异常
     */
    @Around("@annotation(requireNodeAccess)")
    public Object checkNodeAccess(ProceedingJoinPoint joinPoint,
                                  RequireNodeAccess requireNodeAccess) throws Throwable {
        Long familyId = currentFamilyId();
        Long userId = currentUserId();
        Object resolved = evaluate(joinPoint, requireNodeAccess.node());
        if (requireNodeAccess.action() == NodeAccessActionEnum.VIEW) {
            visibilityService.assertDetailVisible(familyId, userId, toLong(resolved));
        } else if (resolved instanceof Collection<?> collection) {
            visibilityService.assertOperableNodes(familyId, userId, toLongArray(collection));
        } else if (resolved instanceof Object[] elements) {
            visibilityService.assertOperableNodes(familyId, userId, toLongArray(elements));
        } else {
            visibilityService.assertOperableNode(familyId, userId, toLong(resolved));
        }
        return joinPoint.proceed();
    }

    /**
     * 以 SpEL 解析目标节点ID，根上下文暴露方法入参（依赖编译期 -parameters 生成的参数名）。
     */
    private Object evaluate(ProceedingJoinPoint joinPoint, String expression) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        MethodBasedEvaluationContext context =
                new MethodBasedEvaluationContext(new Object(), method, joinPoint.getArgs(), parameterNameDiscoverer);
        Expression expr = parser.parseExpression(expression);
        return expr.getValue(context);
    }

    /**
     * 读取当前会话中的家族ID，未加入家族时抛出业务异常
     */
    private Long currentFamilyId() {
        HttpSession session = currentSession();
        Long familyId = session == null ? null : (Long) session.getAttribute(FamilyTreeConsts.SESSION_FAMILY_ID);
        if (familyId == null) {
            throw new BusinessException(NO_FAMILY_MESSAGE);
        }
        return familyId;
    }

    /**
     * 读取当前会话中的用户ID（登录拦截器已保证存在，缺失时返回 null 交由可见性服务失败关闭）
     */
    private Long currentUserId() {
        HttpSession session = currentSession();
        return session == null ? null : (Long) session.getAttribute(FamilyTreeConsts.SESSION_USER_ID);
    }

    private HttpSession currentSession() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest().getSession(false);
        }
        logger.debug("No servlet request bound to current thread while enforcing endpoint access");
        return null;
    }

    private Long[] toLongArray(Collection<?> values) {
        return values.stream().map(this::toLong).toArray(Long[]::new);
    }

    private Long[] toLongArray(Object[] values) {
        return java.util.Arrays.stream(values).map(this::toLong).toArray(Long[]::new);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof CharSequence text && !text.isEmpty()) {
            return Long.valueOf(text.toString());
        }
        throw new BusinessException("节点ID解析失败: " + value);
    }
}
