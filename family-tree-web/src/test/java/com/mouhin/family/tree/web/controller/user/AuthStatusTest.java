package com.mouhin.family.tree.web.controller.user;

import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 公开登录状态接口单元测试。
 *
 * <p>该接口仅供首页判断是否展示登录态入口，须保证不创建会话、不读取账号资料、
 * 也不轮换 CSRF Token，否则会让其他标签页已保存的令牌失效。</p>
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
class AuthStatusTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(null, null, null, null, null, null, null))
                .build();
    }

    @Test
    void authStatus_withoutSession_returnsFalseAndCreatesNoSession() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.loggedIn").value(false))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        assertNull(result.getRequest().getSession(false));
    }

    @Test
    void authStatus_withSessionWithoutLogin_returnsFalse() throws Exception {
        mockMvc.perform(get("/api/auth/status").sessionAttr("unrelated", "value"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loggedIn").value(false));
    }

    @Test
    void authStatus_withAuthenticatedSession_returnsTrue() throws Exception {
        mockMvc.perform(get("/api/auth/status").sessionAttr(FamilyTreeConsts.SESSION_USER_ID, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loggedIn").value(true));
    }
}
