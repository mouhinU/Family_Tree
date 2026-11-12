package com.mouhin.family.tree.web.security;

import com.mouhin.family.tree.application.service.FamilyNodeVisibilityApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.exception.BusinessException;
import jakarta.servlet.http.HttpSession;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 端点权限切面单元测试：覆盖会话主体提取、SpEL 节点解析（标量 / DTO 属性 / 集合 / 创建锚点派生）、
 * 管理者与节点查看 / 操作的分派，以及未加入家族的失败关闭。
 *
 * @author Family-Tree
 * @date 2026-11-12
 */
@ExtendWith(MockitoExtension.class)
class EndpointAccessAspectTest {

    private static final Long USER_ID = 1L;
    private static final Long FAMILY_ID = 100L;

    @Mock
    private FamilyNodeVisibilityApplicationService visibilityService;

    private EndpointAccessAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new EndpointAccessAspect(visibilityService);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void bindSession(Long familyId, Long userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        HttpSession session = new MockHttpSession();
        if (familyId != null) {
            session.setAttribute(FamilyTreeConsts.SESSION_FAMILY_ID, familyId);
        }
        if (userId != null) {
            session.setAttribute(FamilyTreeConsts.SESSION_USER_ID, userId);
        }
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    /**
     * 用采样方法的注解、参数名与入参构造一个仅用于驱动的 ProceedingJoinPoint。
     */
    private ProceedingJoinPoint joinPointFor(String methodName, Class<?> paramType, Object arg) throws Throwable {
        Method method = paramType == null
                ? SampleApi.class.getMethod(methodName)
                : SampleApi.class.getMethod(methodName, paramType);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        org.mockito.Mockito.lenient().when(joinPoint.getSignature()).thenReturn(signature);
        org.mockito.Mockito.lenient().when(signature.getMethod()).thenReturn(method);
        org.mockito.Mockito.lenient().when(joinPoint.getArgs()).thenReturn(new Object[]{arg});
        org.mockito.Mockito.lenient().when(joinPoint.proceed()).thenReturn("proceeded");
        return joinPoint;
    }

    private RequireNodeAccess nodeAnnotationOf(String methodName, Class<?> paramType) throws NoSuchMethodException {
        return SampleApi.class.getMethod(methodName, paramType).getAnnotation(RequireNodeAccess.class);
    }

    private RequireFamilyManager managerAnnotationOf() throws NoSuchMethodException {
        return SampleApi.class.getMethod("managerOnly").getAnnotation(RequireFamilyManager.class);
    }

    @Test
    void managerDelegatesToAssertManager() throws Throwable {
        bindSession(FAMILY_ID, USER_ID);
        ProceedingJoinPoint joinPoint = joinPointFor("managerOnly", null, null);
        RequireFamilyManager annotation = managerAnnotationOf();

        Object result = aspect.checkManager(joinPoint, annotation);

        assertEquals("proceeded", result);
        verify(visibilityService).assertManager(FAMILY_ID, USER_ID);
    }

    @Test
    void nodeViewResolvesPathVariable() throws Throwable {
        bindSession(FAMILY_ID, USER_ID);
        ProceedingJoinPoint joinPoint = joinPointFor("view", Long.class, 42L);
        RequireNodeAccess annotation = nodeAnnotationOf("view", Long.class);

        aspect.checkNodeAccess(joinPoint, annotation);

        verify(visibilityService).assertDetailVisible(FAMILY_ID, USER_ID, 42L);
    }

    @Test
    void nodeOperateScalarResolvesPathVariable() throws Throwable {
        bindSession(FAMILY_ID, USER_ID);
        ProceedingJoinPoint joinPoint = joinPointFor("operate", Long.class, 7L);
        RequireNodeAccess annotation = nodeAnnotationOf("operate", Long.class);

        aspect.checkNodeAccess(joinPoint, annotation);

        verify(visibilityService).assertOperableNode(FAMILY_ID, USER_ID, 7L);
    }

    @Test
    void nodeOperateResolvesDtoProperty() throws Throwable {
        bindSession(FAMILY_ID, USER_ID);
        SampleReq req = new SampleReq();
        req.setNodeId(55L);
        ProceedingJoinPoint joinPoint = joinPointFor("dtoOperate", SampleReq.class, req);
        RequireNodeAccess annotation = nodeAnnotationOf("dtoOperate", SampleReq.class);

        aspect.checkNodeAccess(joinPoint, annotation);

        verify(visibilityService).assertOperableNode(FAMILY_ID, USER_ID, 55L);
    }

    @Test
    void nodeOperateResolvesDtoCollection() throws Throwable {
        bindSession(FAMILY_ID, USER_ID);
        SampleReq req = new SampleReq();
        req.setNodeIds(List.of(1L, 2L, 3L));
        ProceedingJoinPoint joinPoint = joinPointFor("color", SampleReq.class, req);
        RequireNodeAccess annotation = nodeAnnotationOf("color", SampleReq.class);

        aspect.checkNodeAccess(joinPoint, annotation);

        verify(visibilityService).assertOperableNodes(FAMILY_ID, USER_ID, 1L, 2L, 3L);
    }

    @Test
    void createAnchorDerivesParentOverChildOverSpouse() throws Throwable {
        bindSession(FAMILY_ID, USER_ID);
        SampleReq req = new SampleReq();
        req.setParentId(10L);
        req.setChildId(20L);
        req.setSpouseId(30L);
        ProceedingJoinPoint joinPoint = joinPointFor("create", SampleReq.class, req);
        RequireNodeAccess annotation = nodeAnnotationOf("create", SampleReq.class);

        aspect.checkNodeAccess(joinPoint, annotation);

        verify(visibilityService).assertOperableNode(FAMILY_ID, USER_ID, 10L);
    }

    @Test
    void createAnchorFallsBackToSpouseWhenNoParentOrChild() throws Throwable {
        bindSession(FAMILY_ID, USER_ID);
        SampleReq req = new SampleReq();
        req.setSpouseId(30L);
        ProceedingJoinPoint joinPoint = joinPointFor("create", SampleReq.class, req);
        RequireNodeAccess annotation = nodeAnnotationOf("create", SampleReq.class);

        aspect.checkNodeAccess(joinPoint, annotation);

        verify(visibilityService).assertOperableNode(FAMILY_ID, USER_ID, 30L);
    }

    @Test
    void createNewRootWithoutAnchorPassesNull() throws Throwable {
        bindSession(FAMILY_ID, USER_ID);
        ProceedingJoinPoint joinPoint = joinPointFor("create", SampleReq.class, new SampleReq());
        RequireNodeAccess annotation = nodeAnnotationOf("create", SampleReq.class);

        aspect.checkNodeAccess(joinPoint, annotation);

        verify(visibilityService).assertOperableNode(FAMILY_ID, USER_ID, null);
    }

    @Test
    void missingFamilyIdFailsClosedWithoutDelegating() throws Throwable {
        bindSession(null, USER_ID);
        ProceedingJoinPoint joinPoint = joinPointFor("view", Long.class, 42L);
        RequireNodeAccess annotation = nodeAnnotationOf("view", Long.class);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> aspect.checkNodeAccess(joinPoint, annotation));

        assertEquals("请先加入或创建家族", ex.getMessage());
        verify(visibilityService, never()).assertDetailVisible(FAMILY_ID, USER_ID, 42L);
        verify(joinPoint, never()).proceed();
    }

    @Test
    void denialPropagatesAndSkipsProceed() throws Throwable {
        bindSession(FAMILY_ID, USER_ID);
        doThrow(new BusinessException("无权查看该族人详情"))
                .when(visibilityService).assertDetailVisible(FAMILY_ID, USER_ID, 42L);
        ProceedingJoinPoint joinPoint = joinPointFor("view", Long.class, 42L);
        RequireNodeAccess annotation = nodeAnnotationOf("view", Long.class);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> aspect.checkNodeAccess(joinPoint, annotation));

        assertEquals("无权查看该族人详情", ex.getMessage());
        verify(joinPoint, never()).proceed();
    }

    @Test
    void proxiedNodeOperateCallIsIntercepted() {
        bindSession(FAMILY_ID, USER_ID);
        SampleApi proxy = newProxy();

        proxy.operate(42L);

        verify(visibilityService).assertOperableNode(FAMILY_ID, USER_ID, 42L);
    }

    @Test
    void proxiedManagerCallIsIntercepted() {
        bindSession(FAMILY_ID, USER_ID);
        SampleApi proxy = newProxy();

        proxy.managerOnly();

        verify(visibilityService).assertManager(FAMILY_ID, USER_ID);
    }

    @Test
    void proxiedCreateAnchorCallIsIntercepted() {
        bindSession(FAMILY_ID, USER_ID);
        SampleApi proxy = newProxy();
        SampleReq req = new SampleReq();
        req.setParentId(10L);

        proxy.create(req);

        verify(visibilityService).assertOperableNode(FAMILY_ID, USER_ID, 10L);
    }

    private SampleApi newProxy() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleApi());
        factory.addAspect(aspect);
        return factory.getProxy();
    }

    /**
     * 采样端点：仅提供注解、参数名与形参类型供反射与代理驱动，非 Spring Bean。
     */
    public static class SampleApi {

        @RequireFamilyManager
        public void managerOnly() {
        }

        @RequireNodeAccess(node = "#nodeId", action = NodeAccessActionEnum.VIEW)
        public String view(Long nodeId) {
            return "view";
        }

        @RequireNodeAccess(node = "#nodeId", action = NodeAccessActionEnum.OPERATE)
        public String operate(Long nodeId) {
            return "operate";
        }

        @RequireNodeAccess(node = "#dto.nodeId", action = NodeAccessActionEnum.OPERATE)
        public String dtoOperate(SampleReq dto) {
            return "dto";
        }

        @RequireNodeAccess(node = "#dto.nodeIds", action = NodeAccessActionEnum.OPERATE)
        public String color(SampleReq dto) {
            return "color";
        }

        @RequireNodeAccess(node = "#dto.parentId != null ? #dto.parentId"
                + " : (#dto.childId != null ? #dto.childId : #dto.spouseId)",
                action = NodeAccessActionEnum.OPERATE)
        public String create(SampleReq dto) {
            return "create";
        }
    }

    /**
     * 采样请求体：承载节点ID、批量节点ID与创建锚点候选。
     */
    static class SampleReq {

        private Long nodeId;
        private List<Long> nodeIds;
        private Long parentId;
        private Long childId;
        private Long spouseId;

        public Long getNodeId() {
            return nodeId;
        }

        public void setNodeId(Long nodeId) {
            this.nodeId = nodeId;
        }

        public List<Long> getNodeIds() {
            return nodeIds;
        }

        public void setNodeIds(List<Long> nodeIds) {
            this.nodeIds = nodeIds;
        }

        public Long getParentId() {
            return parentId;
        }

        public void setParentId(Long parentId) {
            this.parentId = parentId;
        }

        public Long getChildId() {
            return childId;
        }

        public void setChildId(Long childId) {
            this.childId = childId;
        }

        public Long getSpouseId() {
            return spouseId;
        }

        public void setSpouseId(Long spouseId) {
            this.spouseId = spouseId;
        }
    }
}
