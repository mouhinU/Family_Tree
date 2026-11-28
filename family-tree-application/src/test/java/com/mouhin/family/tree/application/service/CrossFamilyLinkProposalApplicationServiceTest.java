package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.AffinityLinkDTO;
import com.mouhin.family.tree.common.dto.CrossFamilyLinkProposalCommentDTO;
import com.mouhin.family.tree.common.dto.CrossFamilyLinkProposalVO;
import com.mouhin.family.tree.common.enums.CrossFamilyLinkProposalStatusEnum;
import com.mouhin.family.tree.common.enums.CrossFamilyLinkProposalVoteStatusEnum;
import com.mouhin.family.tree.common.enums.FamilyMemberRoleEnum;
import com.mouhin.family.tree.common.enums.FamilySettingKeyEnum;
import com.mouhin.family.tree.common.enums.NodeLinkSourceEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.CrossFamilyLink;
import com.mouhin.family.tree.domain.entity.CrossFamilyLinkProposal;
import com.mouhin.family.tree.domain.entity.CrossFamilyLinkProposalVote;
import com.mouhin.family.tree.domain.entity.Family;
import com.mouhin.family.tree.domain.entity.FamilyMember;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilySetting;
import com.mouhin.family.tree.domain.entity.FamilyUserNode;
import com.mouhin.family.tree.domain.entity.User;
import com.mouhin.family.tree.domain.repository.CrossFamilyLinkProposalEventRepository;
import com.mouhin.family.tree.domain.repository.CrossFamilyLinkProposalRepository;
import com.mouhin.family.tree.domain.repository.CrossFamilyLinkProposalVoteRepository;
import com.mouhin.family.tree.domain.repository.CrossFamilyLinkRepository;
import com.mouhin.family.tree.domain.repository.FamilyMemberRepository;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.FamilyRepository;
import com.mouhin.family.tree.domain.repository.FamilySettingRepository;
import com.mouhin.family.tree.domain.repository.FamilyUserNodeRepository;
import com.mouhin.family.tree.domain.repository.UserRepository;
import com.mouhin.family.tree.domain.service.FamilySettingDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 跨族链接提案应用服务单元测试。
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrossFamilyLinkProposalApplicationServiceTest {

    private static final Long FAMILY_ID = 100L;
    private static final Long OTHER_FAMILY_ID = 200L;
    private static final Long NODE_ID = 10L;
    private static final Long OTHER_NODE_ID = 30L;
    private static final Long PROPOSAL_ID = 1L;
    private static final Long MANAGER_ID = 99L;
    private static final Long FIRST_VOTER_ID = 11L;
    private static final Long SECOND_VOTER_ID = 22L;
    private static final Long ADMIN_ID = 88L;

    @Mock private CrossFamilyLinkProposalRepository proposalRepository;
    @Mock private CrossFamilyLinkProposalVoteRepository voteRepository;
    @Mock private CrossFamilyLinkProposalEventRepository eventRepository;
    @Mock private CrossFamilyLinkRepository linkRepository;
    @Mock private FamilyNodeRepository familyNodeRepository;
    @Mock private FamilyRepository familyRepository;
    @Mock private FamilyMemberRepository familyMemberRepository;
    @Mock private FamilyUserNodeRepository familyUserNodeRepository;
    @Mock private FamilySettingRepository familySettingRepository;
    @Mock private UserRepository userRepository;
    @Mock private PlatformSettingApplicationService platformSettingService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private CrossFamilyLinkProposalApplicationService service;
    private CrossFamilyLinkProposal proposal;
    private final List<CrossFamilyLinkProposalVote> votes = new ArrayList<>();
    private final Map<Long, FamilyUserNode> bindings = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        service = new CrossFamilyLinkProposalApplicationService(proposalRepository, voteRepository,
                eventRepository, linkRepository, familyNodeRepository, familyRepository,
                familyMemberRepository, familyUserNodeRepository, familySettingRepository,
                userRepository, platformSettingService, new FamilySettingDomainService(), eventPublisher);
        proposal = proposal();
        bindVoter(FAMILY_ID, FIRST_VOTER_ID, NODE_ID);
        bindVoter(OTHER_FAMILY_ID, SECOND_VOTER_ID, OTHER_NODE_ID);
        when(platformSettingService.familyScopedDefaults()).thenReturn(Map.of(
                FamilySettingKeyEnum.CROSS_FAMILY_LINK_MIN_AGREEMENTS.getCode(), "1"));
        when(platformSettingService.getInt(any())).thenReturn(30);
        when(familySettingRepository.findByFamilyId(anyLong())).thenReturn(List.of());
        when(proposalRepository.findByIdForUpdate(PROPOSAL_ID)).thenReturn(proposal);
        when(proposalRepository.findById(PROPOSAL_ID)).thenReturn(proposal);
        when(voteRepository.listByProposalId(PROPOSAL_ID)).thenAnswer(invocation -> List.copyOf(votes));
        when(voteRepository.save(any())).thenAnswer(invocation -> {
            CrossFamilyLinkProposalVote vote = invocation.getArgument(0);
            vote.setId((long) votes.size() + 1L);
            votes.add(vote);
            return vote;
        });
        when(eventRepository.listByProposalId(PROPOSAL_ID)).thenReturn(List.of());
        when(familyNodeRepository.findById(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            if (id.equals(NODE_ID)) {
                return node(NODE_ID, FAMILY_ID);
            }
            return node(id, OTHER_FAMILY_ID);
        });
        when(familyRepository.findById(anyLong())).thenAnswer(invocation -> family(invocation.getArgument(0)));
        when(familyMemberRepository.findByFamilyIdAndUserId(FAMILY_ID, MANAGER_ID))
                .thenReturn(member(FAMILY_ID, MANAGER_ID, FamilyMemberRoleEnum.OWNER.getCode()));
        when(userRepository.findById(MANAGER_ID)).thenReturn(user(MANAGER_ID));
        when(userRepository.findById(ADMIN_ID)).thenReturn(user(ADMIN_ID));
    }

    @Test
    void createProposal_normalizesEndpointsAndKeepsThresholdsWithFamilies() {
        FamilySetting sourceOverride = setting(FAMILY_ID, "3");
        when(familySettingRepository.findByFamilyId(FAMILY_ID)).thenReturn(List.of(sourceOverride));
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node(NODE_ID, OTHER_FAMILY_ID));
        when(familyNodeRepository.findById(OTHER_NODE_ID)).thenReturn(node(OTHER_NODE_ID, FAMILY_ID));
        when(proposalRepository.save(any())).thenAnswer(invocation -> {
            CrossFamilyLinkProposal saved = invocation.getArgument(0);
            saved.setId(PROPOSAL_ID);
            return saved;
        });

        service.createProposal(request(), FAMILY_ID, OTHER_NODE_ID, MANAGER_ID, "manager", "127.0.0.1");

        ArgumentCaptor<CrossFamilyLinkProposal> captor = ArgumentCaptor.forClass(CrossFamilyLinkProposal.class);
        verify(proposalRepository).save(captor.capture());
        CrossFamilyLinkProposal saved = captor.getValue();
        assertEquals(OTHER_FAMILY_ID, saved.getSrcFamilyId());
        assertEquals(NODE_ID, saved.getSrcNodeId());
        assertEquals(Integer.valueOf(1), saved.getSrcRequirement());
        assertEquals(FAMILY_ID, saved.getDstFamilyId());
        assertEquals(Integer.valueOf(3), saved.getDstRequirement());
        assertEquals(NODE_ID, saved.getActiveSrcNodeId());
        assertEquals(OTHER_NODE_ID, saved.getActiveDstNodeId());
    }

    @Test
    void agree_countsDistinctBoundNodesFromBothFamilies() {
        service.agree(PROPOSAL_ID, FAMILY_ID, FIRST_VOTER_ID, "first", "127.0.0.1");
        assertEquals(CrossFamilyLinkProposalStatusEnum.PENDING_VOTES.getCode(), proposal.getStatus());

        CrossFamilyLinkProposalVO result = service.agree(
                PROPOSAL_ID, OTHER_FAMILY_ID, SECOND_VOTER_ID, "second", "127.0.0.1");

        assertEquals(CrossFamilyLinkProposalStatusEnum.READY_FOR_REVIEW.getCode(), proposal.getStatus());
        assertEquals(Long.valueOf(1L), result.getSrcAgreementCount());
        assertEquals(Long.valueOf(1L), result.getDstAgreementCount());
        assertEquals(CrossFamilyLinkProposalVoteStatusEnum.ACTIVE.name(), result.getVotes().get(0).getStatus());
    }

    @Test
    void agree_sameAccountAtBothEndpoints_rejected() {
        service.agree(PROPOSAL_ID, FAMILY_ID, FIRST_VOTER_ID, "first", "127.0.0.1");
        bindVoter(OTHER_FAMILY_ID, FIRST_VOTER_ID, OTHER_NODE_ID);

        assertThrows(BusinessException.class, () -> service.agree(
                PROPOSAL_ID, OTHER_FAMILY_ID, FIRST_VOTER_ID, "first", "127.0.0.1"));
        assertEquals(1, votes.size());
    }

    @Test
    void agree_staleVoteFromOldBinding_replacedByCurrentBinding() {
        CrossFamilyLinkProposalVote oldVote = vote(FAMILY_ID, FIRST_VOTER_ID, NODE_ID);
        oldVote.setUserId(MANAGER_ID);
        votes.add(oldVote);
        bindVoter(FAMILY_ID, FIRST_VOTER_ID, NODE_ID);

        service.agree(PROPOSAL_ID, FAMILY_ID, FIRST_VOTER_ID, "first", "127.0.0.1");

        assertEquals(FIRST_VOTER_ID, oldVote.getUserId());
        assertEquals(CrossFamilyLinkProposalVoteStatusEnum.ACTIVE.getCode(), oldVote.getStatus());
        // 先撤销失效投票，再复用唯一节点记录写入当前账号的新投票
        verify(voteRepository, times(2)).update(oldVote);
    }

    @Test
    void withdraw_returnsReviewedProposalToVotingWhenThresholdLost() {
        service.agree(PROPOSAL_ID, FAMILY_ID, FIRST_VOTER_ID, "first", "127.0.0.1");
        service.agree(PROPOSAL_ID, OTHER_FAMILY_ID, SECOND_VOTER_ID, "second", "127.0.0.1");

        CrossFamilyLinkProposalVO result = service.withdraw(
                PROPOSAL_ID, FAMILY_ID, FIRST_VOTER_ID, "first", "127.0.0.1");

        assertEquals(CrossFamilyLinkProposalStatusEnum.PENDING_VOTES.getCode(), proposal.getStatus());
        assertEquals(Long.valueOf(0L), result.getSrcAgreementCount());
        assertEquals(CrossFamilyLinkProposalVoteStatusEnum.REVOKED.name(), result.getVotes().get(0).getStatus());
        assertTrue(result.getViewerCanVote());
    }

    @Test
    void approve_createsTraceableManualLinkAndClearsActivePair() {
        proposal.changeStatus(CrossFamilyLinkProposalStatusEnum.READY_FOR_REVIEW, LocalDateTime.now());
        votes.add(vote(FAMILY_ID, FIRST_VOTER_ID, NODE_ID));
        votes.add(vote(OTHER_FAMILY_ID, SECOND_VOTER_ID, OTHER_NODE_ID));
        when(linkRepository.save(any())).thenAnswer(invocation -> {
            CrossFamilyLink saved = invocation.getArgument(0);
            saved.setId(77L);
            return saved;
        });

        CrossFamilyLinkProposalVO result = service.approve(PROPOSAL_ID, comment("审核通过"),
                ADMIN_ID, "admin", "127.0.0.1");

        ArgumentCaptor<CrossFamilyLink> captor = ArgumentCaptor.forClass(CrossFamilyLink.class);
        verify(linkRepository).save(captor.capture());
        CrossFamilyLink saved = captor.getValue();
        assertEquals(PROPOSAL_ID, saved.getProposalId());
        assertEquals(NodeLinkSourceEnum.MANUAL.getCode(), saved.getSource());
        assertEquals(ADMIN_ID, saved.getConfirmedBy());
        assertEquals(CrossFamilyLinkProposalStatusEnum.APPROVED.getCode(), result.getStatus());
        assertNull(proposal.getActiveSrcNodeId());
        assertNull(proposal.getActiveDstNodeId());
    }

    @Test
    void reject_withoutComment_rejectedWithoutStateChange() {
        proposal.changeStatus(CrossFamilyLinkProposalStatusEnum.READY_FOR_REVIEW, LocalDateTime.now());

        assertThrows(BusinessException.class,
                () -> service.reject(PROPOSAL_ID, comment(null), ADMIN_ID, "admin", "127.0.0.1"));

        assertEquals(CrossFamilyLinkProposalStatusEnum.READY_FOR_REVIEW.getCode(), proposal.getStatus());
        verify(proposalRepository, never()).update(any());
    }

    @Test
    void createProposal_ordinaryMemberRejected() {
        when(familyMemberRepository.findByFamilyIdAndUserId(FAMILY_ID, FIRST_VOTER_ID))
                .thenReturn(member(FAMILY_ID, FIRST_VOTER_ID, FamilyMemberRoleEnum.MEMBER.getCode()));

        assertThrows(BusinessException.class, () -> service.createProposal(
                request(), FAMILY_ID, NODE_ID, FIRST_VOTER_ID, "member", "127.0.0.1"));
        verify(proposalRepository, never()).save(any());
    }

    @Test
    void createProposal_platformSwitchOffRejected() {
        org.mockito.Mockito.doThrow(new BusinessException("功能未开启"))
                .when(platformSettingService).assertEnabled(any());

        assertThrows(BusinessException.class, () -> service.createProposal(
                request(), FAMILY_ID, NODE_ID, MANAGER_ID, "manager", "127.0.0.1"));
        verify(proposalRepository, never()).save(any());
    }

    @Test
    void agree_nonEndpointFamilyRejected() {
        bindVoter(300L, FIRST_VOTER_ID, 40L);

        assertThrows(BusinessException.class, () -> service.agree(
                PROPOSAL_ID, 300L, FIRST_VOTER_ID, "first", "127.0.0.1"));
        verify(voteRepository, never()).save(any());
    }

    @Test
    void agree_unboundMemberRejected() {
        when(familyMemberRepository.findByFamilyIdAndUserId(FAMILY_ID, 33L))
                .thenReturn(member(FAMILY_ID, 33L, FamilyMemberRoleEnum.MEMBER.getCode()));

        assertThrows(BusinessException.class, () -> service.agree(
                PROPOSAL_ID, FAMILY_ID, 33L, "unbound", "127.0.0.1"));
        verify(voteRepository, never()).save(any());
    }

    @Test
    void agree_sameNodeCannotBeUsedByAnotherAccount() {
        service.agree(PROPOSAL_ID, FAMILY_ID, FIRST_VOTER_ID, "first", "127.0.0.1");
        bindVoter(FAMILY_ID, 33L, NODE_ID);

        assertThrows(BusinessException.class, () -> service.agree(
                PROPOSAL_ID, FAMILY_ID, 33L, "second", "127.0.0.1"));
        assertEquals(1, votes.size());
    }

    @Test
    void agree_managerHasNoAdditionalVoteWeight() {
        proposal.setSrcRequirement(2);
        bindVoter(FAMILY_ID, MANAGER_ID, 40L);
        when(familyNodeRepository.findById(40L)).thenReturn(node(40L, FAMILY_ID));

        CrossFamilyLinkProposalVO result = service.agree(
                PROPOSAL_ID, FAMILY_ID, MANAGER_ID, "manager", "127.0.0.1");

        assertEquals(Long.valueOf(1L), result.getSrcAgreementCount());
        assertEquals(CrossFamilyLinkProposalStatusEnum.PENDING_VOTES.getCode(), result.getStatus());
    }

    @Test
    void getProposal_sameAccountWithOppositeVoteCannotVoteAgain() {
        service.agree(PROPOSAL_ID, FAMILY_ID, FIRST_VOTER_ID, "first", "127.0.0.1");
        bindVoter(OTHER_FAMILY_ID, FIRST_VOTER_ID, OTHER_NODE_ID);

        CrossFamilyLinkProposalVO result = service.getFamilyProposal(
                PROPOSAL_ID, OTHER_FAMILY_ID, FIRST_VOTER_ID);

        assertFalse(result.getViewerCanVote());
        assertTrue(result.getViewerHasVoted());
    }

    @Test
    void approve_withoutCommentAccepted() {
        proposal.changeStatus(CrossFamilyLinkProposalStatusEnum.READY_FOR_REVIEW, LocalDateTime.now());
        votes.add(vote(FAMILY_ID, FIRST_VOTER_ID, NODE_ID));
        votes.add(vote(OTHER_FAMILY_ID, SECOND_VOTER_ID, OTHER_NODE_ID));
        when(linkRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CrossFamilyLinkProposalVO result = service.approve(
                PROPOSAL_ID, null, ADMIN_ID, "admin", "127.0.0.1");

        assertEquals(CrossFamilyLinkProposalStatusEnum.APPROVED.getCode(), result.getStatus());
        assertNull(result.getReviewComment());
    }

    @Test
    void approve_repeatedApprovalDoesNotCreateAnotherLink() {
        proposal.changeStatus(CrossFamilyLinkProposalStatusEnum.READY_FOR_REVIEW, LocalDateTime.now());
        votes.add(vote(FAMILY_ID, FIRST_VOTER_ID, NODE_ID));
        votes.add(vote(OTHER_FAMILY_ID, SECOND_VOTER_ID, OTHER_NODE_ID));
        when(linkRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.approve(PROPOSAL_ID, null, ADMIN_ID, "admin", "127.0.0.1");

        assertThrows(BusinessException.class,
                () -> service.approve(PROPOSAL_ID, null, ADMIN_ID, "admin", "127.0.0.1"));
        verify(linkRepository).save(any());
    }

    @Test
    void approve_thresholdLostRejectedWithoutCreatingLink() {
        proposal.changeStatus(CrossFamilyLinkProposalStatusEnum.READY_FOR_REVIEW, LocalDateTime.now());
        votes.add(vote(FAMILY_ID, FIRST_VOTER_ID, NODE_ID));

        assertThrows(BusinessException.class,
                () -> service.approve(PROPOSAL_ID, null, ADMIN_ID, "admin", "127.0.0.1"));
        verify(linkRepository, never()).save(any());
    }

    @Test
    void cancel_terminalStateCannotBeChanged() {
        service.cancel(PROPOSAL_ID, FAMILY_ID, MANAGER_ID, "manager", "127.0.0.1");

        assertNull(proposal.getActiveSrcNodeId());
        assertNull(proposal.getActiveDstNodeId());
        assertThrows(BusinessException.class, () -> service.agree(
                PROPOSAL_ID, FAMILY_ID, FIRST_VOTER_ID, "first", "127.0.0.1"));
        assertThrows(BusinessException.class,
                () -> service.cancel(PROPOSAL_ID, FAMILY_ID, MANAGER_ID, "manager", "127.0.0.1"));
        verify(voteRepository, never()).save(any());
    }

    @Test
    void maintainProposals_invalidVoteReturnsReadyProposalToVoting() {
        proposal.changeStatus(CrossFamilyLinkProposalStatusEnum.READY_FOR_REVIEW, LocalDateTime.now());
        votes.add(vote(FAMILY_ID, FIRST_VOTER_ID, NODE_ID));
        votes.add(vote(OTHER_FAMILY_ID, SECOND_VOTER_ID, OTHER_NODE_ID));
        when(userRepository.findById(SECOND_VOTER_ID)).thenReturn(null);
        when(proposalRepository.listActive(200)).thenReturn(List.of(proposal));

        service.maintainProposals();

        assertEquals(CrossFamilyLinkProposalStatusEnum.PENDING_VOTES.getCode(), proposal.getStatus());
        assertEquals(CrossFamilyLinkProposalVoteStatusEnum.REVOKED.getCode(), votes.get(1).getStatus());
    }

    @Test
    void listFamilyProposals_invalidPaginationRejected() {
        assertThrows(BusinessException.class, () -> service.listFamilyProposals(FAMILY_ID, FIRST_VOTER_ID, 0, 20));
        assertThrows(BusinessException.class, () -> service.listFamilyProposals(FAMILY_ID, FIRST_VOTER_ID, 1, 101));
    }

    @Test
    void maintainProposals_expiresOverdueProposal() {
        proposal.setExpireAt(LocalDateTime.now().minusSeconds(1L));
        when(proposalRepository.listActive(200)).thenReturn(List.of(proposal));

        service.maintainProposals();

        assertEquals(CrossFamilyLinkProposalStatusEnum.EXPIRED.getCode(), proposal.getStatus());
        verify(proposalRepository).update(proposal);
        assertNull(proposal.getActiveSrcNodeId());
        assertNull(proposal.getActiveDstNodeId());
    }

    private CrossFamilyLinkProposal proposal() {
        CrossFamilyLinkProposal value = new CrossFamilyLinkProposal();
        value.setId(PROPOSAL_ID);
        value.setSrcFamilyId(FAMILY_ID);
        value.setSrcNodeId(NODE_ID);
        value.setSrcRequirement(1);
        value.setDstFamilyId(OTHER_FAMILY_ID);
        value.setDstNodeId(OTHER_NODE_ID);
        value.setDstRequirement(1);
        value.setLinkType(1);
        value.setInitiatorId(MANAGER_ID);
        value.setInitiatorFamilyId(FAMILY_ID);
        value.setExpireAt(LocalDateTime.now().plusDays(1L));
        value.changeStatus(CrossFamilyLinkProposalStatusEnum.PENDING_VOTES, LocalDateTime.now());
        return value;
    }

    private AffinityLinkDTO request() {
        AffinityLinkDTO dto = new AffinityLinkDTO();
        dto.setDstNodeId(NODE_ID);
        dto.setLinkType(1);
        dto.setReason("same person");
        return dto;
    }

    private CrossFamilyLinkProposalCommentDTO comment(String text) {
        CrossFamilyLinkProposalCommentDTO dto = new CrossFamilyLinkProposalCommentDTO();
        dto.setComment(text);
        return dto;
    }

    private CrossFamilyLinkProposalVote vote(Long familyId, Long userId, Long nodeId) {
        CrossFamilyLinkProposalVote vote = new CrossFamilyLinkProposalVote();
        vote.setId((long) votes.size() + 1L);
        vote.setProposalId(PROPOSAL_ID);
        vote.setFamilyId(familyId);
        vote.setUserId(userId);
        vote.setNodeId(nodeId);
        vote.setStatus(CrossFamilyLinkProposalVoteStatusEnum.ACTIVE.getCode());
        return vote;
    }

    private FamilySetting setting(Long familyId, String value) {
        FamilySetting setting = new FamilySetting();
        setting.setFamilyId(familyId);
        setting.setSettingKey(FamilySettingKeyEnum.CROSS_FAMILY_LINK_MIN_AGREEMENTS.getCode());
        setting.setSettingValue(value);
        return setting;
    }

    private void bindVoter(Long familyId, Long userId, Long nodeId) {
        FamilyUserNode binding = new FamilyUserNode();
        binding.setFamilyId(familyId);
        binding.setUserId(userId);
        binding.setNodeId(nodeId);
        bindings.put(userId, binding);
        when(familyMemberRepository.findByFamilyIdAndUserId(familyId, userId))
                .thenReturn(member(familyId, userId, FamilyMemberRoleEnum.MEMBER.getCode()));
        when(familyUserNodeRepository.findByFamilyIdAndUserId(familyId, userId)).thenReturn(binding);
        when(userRepository.findById(userId)).thenReturn(user(userId));
    }

    private FamilyMember member(Long familyId, Long userId, String role) {
        FamilyMember member = new FamilyMember();
        member.setFamilyId(familyId);
        member.setUserId(userId);
        member.setRole(role);
        return member;
    }

    private FamilyNode node(Long id, Long familyId) {
        FamilyNode node = new FamilyNode();
        node.setId(id);
        node.setFamilyId(familyId);
        node.setName("node-" + id);
        return node;
    }

    private Family family(Long id) {
        Family family = new Family();
        family.setId(id);
        family.setName("family-" + id);
        return family;
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        return user;
    }
}
