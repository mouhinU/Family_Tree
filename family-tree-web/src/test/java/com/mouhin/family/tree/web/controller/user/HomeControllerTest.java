package com.mouhin.family.tree.web.controller.user;

import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 首页与工作台入口路由单元测试。
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
class HomeControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new HomeController()).build();
    }

    @Test
    void home_withoutSession_forwardsToPublicHome() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/home.html"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void home_withSessionButWithoutLogin_forwardsToPublicHome() throws Exception {
        mockMvc.perform(get("/").sessionAttr("unrelated", "value"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/home.html"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void home_withAuthenticatedSession_forwardsToWorkspace() throws Exception {
        mockMvc.perform(get("/").sessionAttr(FamilyTreeConsts.SESSION_USER_ID, 1L))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void publicHome_withoutSession_forwardsToPublicHome() throws Exception {
        mockMvc.perform(get("/home"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/home.html"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void publicHome_withAuthenticatedSession_stillForwardsToPublicHome() throws Exception {
        mockMvc.perform(get("/home").sessionAttr(FamilyTreeConsts.SESSION_USER_ID, 1L))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/home.html"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }
}
