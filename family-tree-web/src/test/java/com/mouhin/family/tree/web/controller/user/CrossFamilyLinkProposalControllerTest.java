package com.mouhin.family.tree.web.controller.user;

import com.mouhin.family.tree.application.service.CrossFamilyLinkProposalApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.AffinityLinkDTO;
import com.mouhin.family.tree.common.dto.CrossFamilyLinkProposalVO;
import com.mouhin.family.tree.common.dto.PageResult;
import com.mouhin.family.tree.web.controller.BaseController;
import com.mouhin.family.tree.web.security.RequireFamilyManager;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 普通用户跨族链接提案控制器单元测试。
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrossFamilyLinkProposalControllerTest {

    private static final Long FAMILY_ID = 100L;
    private static final Long USER_ID = 11L;
    private static final Long NODE_ID = 10L;
    private static final Long PROPOSAL_ID = 1L;

    @Mock
    private CrossFamilyLinkProposalApplicationService proposalService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CrossFamilyLinkProposalController(proposalService)).build();
        when(proposalService.createProposal(any(), eq(FAMILY_ID), eq(NODE_ID), eq(USER_ID), eq("member"), any()))
                .thenReturn(new CrossFamilyLinkProposalVO());
        when(proposalService.agree(eq(PROPOSAL_ID), eq(FAMILY_ID), eq(USER_ID), eq("member"), any()))
                .thenReturn(new CrossFamilyLinkProposalVO());
        when(proposalService.withdraw(eq(PROPOSAL_ID), eq(FAMILY_ID), eq(USER_ID), eq("member"), any()))
                .thenReturn(new CrossFamilyLinkProposalVO());
        when(proposalService.cancel(eq(PROPOSAL_ID), eq(FAMILY_ID), eq(USER_ID), eq("member"), any()))
                .thenReturn(new CrossFamilyLinkProposalVO());
        when(proposalService.listFamilyProposals(FAMILY_ID, USER_ID, 1, 20))
                .thenReturn(new PageResult<>(List.of(), 0L, 1, 20));
        when(proposalService.getFamilyProposal(PROPOSAL_ID, FAMILY_ID, USER_ID))
                .thenReturn(new CrossFamilyLinkProposalVO());
    }

    @Test
    void createProposal_passesSessionIdentityAndValidatedRequest() throws Exception {
        mockMvc.perform(post("/api/node/{nodeId}/link-proposals", NODE_ID)
                        .session(session())
                        .contentType("application/json")
                        .content("{\"dstNodeId\":30,\"linkType\":1,\"reason\":\"同一人\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<AffinityLinkDTO> captor = ArgumentCaptor.forClass(AffinityLinkDTO.class);
        verify(proposalService).createProposal(captor.capture(), eq(FAMILY_ID), eq(NODE_ID), eq(USER_ID),
                eq("member"), any());
        assertEquals(Long.valueOf(30L), captor.getValue().getDstNodeId());
        assertEquals("同一人", captor.getValue().getReason());
    }

    @Test
    void createProposal_invalidRequest_isRejectedBeforeService() throws Exception {
        mockMvc.perform(post("/api/node/{nodeId}/link-proposals", NODE_ID)
                        .session(session())
                        .contentType("application/json")
                        .content("{\"linkType\":1,\"reason\":\"同一人\"}"))
                .andExpect(status().isBadRequest());

        verify(proposalService, org.mockito.Mockito.never())
                .createProposal(any(), any(), any(), any(), any(), any());
    }

    @Test
    void listAndDetail_useCurrentFamilyAndUser() throws Exception {
        mockMvc.perform(get("/api/link-proposals").session(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray());
        mockMvc.perform(get("/api/link-proposals/{proposalId}", PROPOSAL_ID).session(session()))
                .andExpect(status().isOk());

        verify(proposalService).listFamilyProposals(FAMILY_ID, USER_ID, 1, 20);
        verify(proposalService).getFamilyProposal(PROPOSAL_ID, FAMILY_ID, USER_ID);
    }

    @Test
    void stateChanges_usePostEndpoints() throws Exception {
        for (String action : List.of("agree", "withdraw", "cancel")) {
            mockMvc.perform(post("/api/link-proposals/{proposalId}/" + action, PROPOSAL_ID).session(session()))
                    .andExpect(status().isOk());
        }

        verify(proposalService).agree(any(), eq(FAMILY_ID), eq(USER_ID), eq("member"), any());
        verify(proposalService).withdraw(any(), eq(FAMILY_ID), eq(USER_ID), eq("member"), any());
        verify(proposalService).cancel(any(), eq(FAMILY_ID), eq(USER_ID), eq("member"), any());
    }

    @Test
    void deleteProposal_isNotExposed() throws Exception {
        mockMvc.perform(delete("/api/link-proposals/{proposalId}", PROPOSAL_ID).session(session()))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void createEndpointDeclaresManagerGuard() throws Exception {
        assertNotNull(CrossFamilyLinkProposalController.class
                .getMethod("createProposal", Long.class, AffinityLinkDTO.class,
                        jakarta.servlet.http.HttpSession.class, jakarta.servlet.http.HttpServletRequest.class)
                .getAnnotation(RequireFamilyManager.class));
        assertNull(BaseController.class.getAnnotation(RequireFamilyManager.class));
    }

    private MockHttpSession session() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(FamilyTreeConsts.SESSION_USER_ID, USER_ID);
        session.setAttribute(FamilyTreeConsts.SESSION_FAMILY_ID, FAMILY_ID);
        session.setAttribute(FamilyTreeConsts.SESSION_USERNAME, "member");
        return session;
    }
}
