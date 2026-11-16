package com.mouhin.family.tree.web.interceptor;

import com.mouhin.family.tree.application.service.FamilyStatusGuardApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.enums.FamilyStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 家族状态拦截器单元测试：裁决表覆盖正常 / 冻结 / 归档三态下读写请求的放行与拒绝，
 * 登录、平台接口、逃生入口的绕过，以及无会话与未选家族时的失败开放。
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@ExtendWith(MockitoExtension.class)
class FamilyStatusInterceptorTest {

    private static final Long FAMILY_ID = 100L;
    private static final String FROZEN_MESSAGE = "该家族已被平台冻结，暂不可修改数据";
    private static final String ARCHIVED_MESSAGE = "该家族已被平台归档，仅平台管理员可查看";

    @Mock
    private FamilyStatusGuardApplicationService familyStatusGuardService;

    private FamilyStatusInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new FamilyStatusInterceptor(familyStatusGuardService);
    }

    private MockHttpServletRequest request(String method, String uri, FamilyStatusEnum status) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(FamilyTreeConsts.SESSION_FAMILY_ID, FAMILY_ID);
        request.setSession(session);
        if (status != null) {
            when(familyStatusGuardService.statusOf(FAMILY_ID)).thenReturn(status);
        }
        return request;
    }

    // ========== 正常家族 ==========

    @Test
    void normalFamilyPassesReadsAndWrites() throws Exception {
        for (String method : new String[] {"GET", "POST", "PUT", "DELETE", "PATCH"}) {
            MockHttpServletRequest request = request(method, "/api/node", FamilyStatusEnum.NORMAL);

            assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()), method);
        }
    }

    // ========== 冻结家族 ==========

    @Test
    void frozenFamilyStillAllowsReads() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/tree", FamilyStatusEnum.FROZEN);

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void frozenFamilyRejectsEveryWriteMethod() throws Exception {
        for (String method : new String[] {"POST", "PUT", "DELETE", "PATCH"}) {
            MockHttpServletRequest request = request(method, "/api/node/1", FamilyStatusEnum.FROZEN);
            MockHttpServletResponse response = new MockHttpServletResponse();

            assertFalse(interceptor.preHandle(request, response, new Object()), method);
            assertEquals(403, response.getStatus());
            assertTrue(response.getContentAsString().contains(FROZEN_MESSAGE));
        }
    }

    // ========== 归档家族 ==========

    @Test
    void archivedFamilyRejectsReadsAndWrites() throws Exception {
        for (String method : new String[] {"GET", "POST", "PUT", "DELETE"}) {
            MockHttpServletRequest request = request(method, "/api/node/1", FamilyStatusEnum.ARCHIVED);
            MockHttpServletResponse response = new MockHttpServletResponse();

            assertFalse(interceptor.preHandle(request, response, new Object()), method);
            assertEquals(403, response.getStatus());
            assertTrue(response.getContentAsString().contains(ARCHIVED_MESSAGE));
        }
    }

    // ========== 逃生入口 ==========

    @Test
    void escapeEntriesNeverConsultStatus() throws Exception {
        String[][] escapeRequests = {
                {"GET", "/api/family/my-list"},
                {"POST", "/api/family/join"},
                {"PUT", "/api/family/switch/200"},
                {"POST", "/api/family"}};
        for (String[] entry : escapeRequests) {
            MockHttpServletRequest request = request(entry[0], entry[1], null);
            MockHttpServletResponse response = new MockHttpServletResponse();

            assertTrue(interceptor.preHandle(request, response, new Object()), entry[0] + " " + entry[1]);
            assertEquals(200, response.getStatus());
        }
        // 逃生入口在读取状态之前放行，成员因此始终能离开已归档家族
        verify(familyStatusGuardService, never()).statusOf(any());
    }

    // ========== 绕过治理的入口 ==========

    @Test
    void platformAndAuthEndpointsNeverConsultStatus() throws Exception {
        String[][] bypassed = {
                {"PUT", "/api/admin/family/100/status"},
                {"POST", "/api/admin/user/7/password-reset"},
                {"GET", "/api/auth/me"},
                {"POST", "/api/auth/logout"}};
        for (String[] entry : bypassed) {
            MockHttpServletRequest request = new MockHttpServletRequest(entry[0], entry[1]);
            MockHttpSession session = new MockHttpSession();
            session.setAttribute(FamilyTreeConsts.SESSION_FAMILY_ID, FAMILY_ID);
            request.setSession(session);

            assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()),
                    entry[0] + " " + entry[1]);
        }
        verify(familyStatusGuardService, never()).statusOf(any());
    }

    @Test
    void anonymousRequestIsLeftToLoginInterceptor() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/node");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        verify(familyStatusGuardService, never()).statusOf(any());
    }

    @Test
    void sessionWithoutCurrentFamilyFollowsGuardVerdict() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/node");
        request.setSession(new MockHttpSession());
        when(familyStatusGuardService.statusOf(null)).thenReturn(FamilyStatusEnum.NORMAL);

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }
}
