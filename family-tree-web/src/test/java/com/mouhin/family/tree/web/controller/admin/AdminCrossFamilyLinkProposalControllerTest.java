package com.mouhin.family.tree.web.controller.admin;

import com.mouhin.family.tree.application.service.CrossFamilyLinkProposalApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.CrossFamilyLinkProposalCommentDTO;
import com.mouhin.family.tree.common.dto.CrossFamilyLinkProposalVO;
import com.mouhin.family.tree.common.dto.PageResult;
import com.mouhin.family.tree.web.security.RequireSystemAdmin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 平台管理员跨族链接提案控制器单元测试。
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminCrossFamilyLinkProposalControllerTest {

    private static final Long ADMIN_ID = 88L;
    private static final Long PROPOSAL_ID = 1L;

    @Mock
    private CrossFamilyLinkProposalApplicationService proposalService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AdminCrossFamilyLinkProposalController(proposalService)).build();
        when(proposalService.listAdminProposals(eq("READY_FOR_REVIEW"), eq(1), eq(20)))
                .thenReturn(new PageResult<>(List.of(), 0L, 1, 20));
        when(proposalService.getAdminProposal(PROPOSAL_ID)).thenReturn(new CrossFamilyLinkProposalVO());
        when(proposalService.comment(eq(PROPOSAL_ID), any(), eq(ADMIN_ID), eq("admin"), any()))
                .thenReturn(new CrossFamilyLinkProposalVO());
        when(proposalService.approve(eq(PROPOSAL_ID), any(), eq(ADMIN_ID), eq("admin"), any()))
                .thenReturn(new CrossFamilyLinkProposalVO());
        when(proposalService.reject(eq(PROPOSAL_ID), any(), eq(ADMIN_ID), eq("admin"), any()))
                .thenReturn(new CrossFamilyLinkProposalVO());
    }

    @Test
    void listAndDetail_forwardPaginationAndStatus() throws Exception {
        mockMvc.perform(get("/api/admin/link-proposals")
                        .param("status", "READY_FOR_REVIEW").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray());
        mockMvc.perform(get("/api/admin/link-proposals/{proposalId}", PROPOSAL_ID))
                .andExpect(status().isOk());

        verify(proposalService).listAdminProposals("READY_FOR_REVIEW", 1, 20);
        verify(proposalService).getAdminProposal(PROPOSAL_ID);
    }

    @Test
    void approve_withoutBody_passesNullComment() throws Exception {
        mockMvc.perform(post("/api/admin/link-proposals/{proposalId}/approve", PROPOSAL_ID)
                        .session(session()).contentType("application/json"))
                .andExpect(status().isOk());

        verify(proposalService).approve(eq(PROPOSAL_ID), isNull(), eq(ADMIN_ID), eq("admin"), any());
    }

    @Test
    void reject_requiresBodyBeforeService() throws Exception {
        mockMvc.perform(post("/api/admin/link-proposals/{proposalId}/reject", PROPOSAL_ID)
                        .session(session()).contentType("application/json"))
                .andExpect(status().isBadRequest());

        verify(proposalService, never()).reject(any(), any(), any(), any(), any());
    }

    @Test
    void commentAndReject_forwardReviewComment() throws Exception {
        mockMvc.perform(post("/api/admin/link-proposals/{proposalId}/comment", PROPOSAL_ID)
                        .session(session()).contentType("application/json").content("{\"comment\":\"需复核\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/link-proposals/{proposalId}/reject", PROPOSAL_ID)
                        .session(session()).contentType("application/json").content("{\"comment\":\"证据不足\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<CrossFamilyLinkProposalCommentDTO> captor =
                ArgumentCaptor.forClass(CrossFamilyLinkProposalCommentDTO.class);
        verify(proposalService).comment(eq(PROPOSAL_ID), captor.capture(), eq(ADMIN_ID), eq("admin"), any());
        assertEquals("需复核", captor.getValue().getComment());
        verify(proposalService).reject(eq(PROPOSAL_ID), any(), eq(ADMIN_ID), eq("admin"), any());
    }

    @Test
    void deleteProposal_isNotExposed() throws Exception {
        mockMvc.perform(delete("/api/admin/link-proposals/{proposalId}", PROPOSAL_ID).session(session()))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void everyAdminEndpointDeclaresSystemAdminGuard() {
        for (Method method : AdminCrossFamilyLinkProposalController.class.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                assertNotNull(method.getAnnotation(RequireSystemAdmin.class), method.getName());
            }
        }
    }

    private MockHttpSession session() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(FamilyTreeConsts.SESSION_USER_ID, ADMIN_ID);
        session.setAttribute(FamilyTreeConsts.SESSION_USERNAME, "admin");
        return session;
    }
}
