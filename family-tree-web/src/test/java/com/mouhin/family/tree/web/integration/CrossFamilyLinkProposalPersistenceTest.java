package com.mouhin.family.tree.web.integration;

import com.mouhin.family.tree.application.service.CrossFamilyLinkProposalApplicationService;
import com.mouhin.family.tree.application.service.PlatformSettingApplicationService;
import com.mouhin.family.tree.common.dto.AffinityLinkDTO;
import com.mouhin.family.tree.common.dto.CrossFamilyLinkProposalVO;
import com.mouhin.family.tree.common.enums.CrossFamilyLinkProposalStatusEnum;
import com.mouhin.family.tree.common.enums.FamilyMemberRoleEnum;
import com.mouhin.family.tree.common.enums.NodeLinkSourceEnum;
import com.mouhin.family.tree.common.enums.PlatformSettingKeyEnum;
import com.mouhin.family.tree.domain.entity.CrossFamilyLink;
import com.mouhin.family.tree.domain.entity.CrossFamilyLinkProposal;
import com.mouhin.family.tree.domain.entity.PlatformSetting;
import com.mouhin.family.tree.domain.repository.CrossFamilyLinkProposalRepository;
import com.mouhin.family.tree.domain.repository.CrossFamilyLinkRepository;
import com.mouhin.family.tree.domain.repository.PlatformSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 跨族链接提案的 H2、Flyway 与 MyBatis 持久化集成测试。
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
@SpringBootTest(properties = {
        "spring.session.store-type=none",
        "spring.task.scheduling.enabled=false"
})
@ActiveProfiles("h2")
@Transactional
class CrossFamilyLinkProposalPersistenceTest {

    private static final Long SRC_FAMILY_ID = 100L;
    private static final Long DST_FAMILY_ID = 200L;
    private static final Long SRC_NODE_ID = 10L;
    private static final Long DST_NODE_ID = 30L;
    private static final Long MANAGER_ID = 99L;
    private static final Long DST_MANAGER_ID = 100L;
    private static final Long ADMIN_ID = 88L;
    private static final String IP_ADDRESS = "127.0.0.1";

    @Autowired private CrossFamilyLinkProposalApplicationService proposalService;
    @Autowired private PlatformSettingRepository platformSettingRepository;
    @Autowired private CrossFamilyLinkProposalRepository proposalRepository;
    @Autowired private CrossFamilyLinkRepository linkRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        savePlatformSetting(PlatformSettingKeyEnum.CROSS_FAMILY_LINK_PROPOSAL_ENABLED, "true");
        savePlatformSetting(PlatformSettingKeyEnum.CROSS_FAMILY_LINK_MIN_AGREEMENTS, "1");
        savePlatformSetting(PlatformSettingKeyEnum.CROSS_FAMILY_LINK_PROPOSAL_EXPIRE_DAYS, "30");
        insertAccount(MANAGER_ID, "proposal-manager");
        insertAccount(DST_MANAGER_ID, "proposal-dst-manager");
        insertAccount(ADMIN_ID, "proposal-admin");
        insertFamily(SRC_FAMILY_ID, "提案源家族", MANAGER_ID);
        insertFamily(DST_FAMILY_ID, "提案目标家族", DST_MANAGER_ID);
        insertMember(SRC_FAMILY_ID, MANAGER_ID, FamilyMemberRoleEnum.OWNER);
        insertMember(DST_FAMILY_ID, DST_MANAGER_ID, FamilyMemberRoleEnum.OWNER);
        insertNode(SRC_NODE_ID, SRC_FAMILY_ID, MANAGER_ID, "提案源节点");
        insertNode(DST_NODE_ID, DST_FAMILY_ID, DST_MANAGER_ID, "提案目标节点");
        insertBinding(SRC_FAMILY_ID, MANAGER_ID, SRC_NODE_ID);
        insertBinding(DST_FAMILY_ID, DST_MANAGER_ID, DST_NODE_ID);
    }

    @Test
    void createVoteAndApprove_persistsTraceableManualLink() {
        CrossFamilyLinkProposalVO created = createProposal();
        assertEquals(CrossFamilyLinkProposalStatusEnum.PENDING_VOTES.getCode(), created.getStatus());
        assertEquals(Long.valueOf(SRC_NODE_ID), created.getSrcNodeId());
        assertEquals(Long.valueOf(DST_NODE_ID), created.getDstNodeId());

        proposalService.agree(created.getId(), SRC_FAMILY_ID, MANAGER_ID, "proposal-manager", IP_ADDRESS);
        CrossFamilyLinkProposalVO ready = proposalService.agree(
                created.getId(), DST_FAMILY_ID, DST_MANAGER_ID, "proposal-dst-manager", IP_ADDRESS);
        assertEquals(CrossFamilyLinkProposalStatusEnum.READY_FOR_REVIEW.getCode(), ready.getStatus());
        assertEquals(Long.valueOf(1L), ready.getSrcAgreementCount());
        assertEquals(Long.valueOf(1L), ready.getDstAgreementCount());

        CrossFamilyLinkProposalVO approved = proposalService.approve(
                created.getId(), null, ADMIN_ID, "proposal-admin", IP_ADDRESS);
        assertEquals(CrossFamilyLinkProposalStatusEnum.APPROVED.getCode(), approved.getStatus());

        CrossFamilyLink link = linkRepository.findByNodePair(SRC_NODE_ID, DST_NODE_ID);
        assertNotNull(link);
        assertEquals(NodeLinkSourceEnum.MANUAL.getCode(), link.getSource());
        assertEquals(created.getId(), link.getProposalId());
        assertEquals(ADMIN_ID, link.getConfirmedBy());
        CrossFamilyLinkProposal persisted = proposalRepository.findById(created.getId());
        assertNull(persisted.getActiveSrcNodeId());
        assertNull(persisted.getActiveDstNodeId());
        assertEquals(ADMIN_ID, persisted.getReviewedBy());
        assertTrue(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM family_node_link_proposal_event WHERE proposal_id = ? AND event_type = 'APPROVED'",
                Integer.class, created.getId()) > 0);
    }

    @Test
    void withdraw_persistsRevokedVoteAndReturnsProposalToVoting() {
        CrossFamilyLinkProposalVO created = createReadyProposal();

        CrossFamilyLinkProposalVO result = proposalService.withdraw(
                created.getId(), SRC_FAMILY_ID, MANAGER_ID, "proposal-manager", IP_ADDRESS);

        assertEquals(CrossFamilyLinkProposalStatusEnum.PENDING_VOTES.getCode(), result.getStatus());
        assertEquals(CrossFamilyLinkProposalStatusEnum.PENDING_VOTES.getCode(),
                jdbcTemplate.queryForObject("SELECT status FROM family_node_link_proposal WHERE id = ?",
                        String.class, created.getId()));
        assertEquals(Integer.valueOf(2), jdbcTemplate.queryForObject(
                "SELECT status FROM family_node_link_proposal_vote WHERE proposal_id = ? AND family_id = ?",
                Integer.class, created.getId(), SRC_FAMILY_ID));
        assertNull(linkRepository.findByNodePair(SRC_NODE_ID, DST_NODE_ID));
    }

    @Test
    void cancel_clearsActivePairAndAllowsSamePairToBeProposedAgain() {
        CrossFamilyLinkProposalVO created = createProposal();
        proposalService.cancel(created.getId(), SRC_FAMILY_ID, MANAGER_ID, "proposal-manager", IP_ADDRESS);

        CrossFamilyLinkProposal cancelled = proposalRepository.findById(created.getId());
        assertEquals(CrossFamilyLinkProposalStatusEnum.CANCELLED.getCode(), cancelled.getStatus());
        assertNull(cancelled.getActiveSrcNodeId());
        assertNull(cancelled.getActiveDstNodeId());
        assertNull(proposalRepository.findActiveByNodePair(SRC_NODE_ID, DST_NODE_ID));

        CrossFamilyLinkProposalVO replacement = createProposal();
        assertNotNull(replacement.getId());
        assertTrue(replacement.getId() > created.getId());
    }

    @Test
    void activeNodePairUniqueConstraint_rejectsConcurrentDuplicate() {
        CrossFamilyLinkProposalVO created = createProposal();

        assertThrows(DuplicateKeyException.class, () -> jdbcTemplate.update(
                "INSERT INTO family_node_link_proposal "
                        + "(src_family_id, src_node_id, dst_family_id, dst_node_id, link_type, status, "
                        + "src_requirement, dst_requirement, active_src_node_id, active_dst_node_id, "
                        + "initiator_id, initiator_family_id, expire_at) "
                        + "VALUES (?, ?, ?, ?, 1, 'PENDING_VOTES', 1, 1, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                SRC_FAMILY_ID, SRC_NODE_ID, DST_FAMILY_ID, DST_NODE_ID,
                SRC_NODE_ID, DST_NODE_ID, MANAGER_ID, SRC_FAMILY_ID));
        assertNotNull(proposalRepository.findById(created.getId()));
    }

    @Test
    void voteNodeUniqueConstraint_rejectsSecondAccountForSameNode() {
        CrossFamilyLinkProposalVO created = createProposal();
        proposalService.agree(created.getId(), SRC_FAMILY_ID, MANAGER_ID, "proposal-manager", IP_ADDRESS);

        assertThrows(DuplicateKeyException.class, () -> jdbcTemplate.update(
                "INSERT INTO family_node_link_proposal_vote "
                        + "(proposal_id, family_id, node_id, user_id, status) VALUES (?, ?, ?, ?, 1)",
                created.getId(), SRC_FAMILY_ID, SRC_NODE_ID, ADMIN_ID));
        assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM family_node_link_proposal_vote WHERE proposal_id = ?",
                Integer.class, created.getId()));
    }

    private CrossFamilyLinkProposalVO createProposal() {
        AffinityLinkDTO dto = new AffinityLinkDTO();
        dto.setDstNodeId(DST_NODE_ID);
        dto.setLinkType(1);
        dto.setReason("集成测试提案");
        return proposalService.createProposal(
                dto, SRC_FAMILY_ID, SRC_NODE_ID, MANAGER_ID, "proposal-manager", IP_ADDRESS);
    }

    private CrossFamilyLinkProposalVO createReadyProposal() {
        CrossFamilyLinkProposalVO created = createProposal();
        proposalService.agree(created.getId(), SRC_FAMILY_ID, MANAGER_ID, "proposal-manager", IP_ADDRESS);
        return proposalService.agree(
                created.getId(), DST_FAMILY_ID, DST_MANAGER_ID, "proposal-dst-manager", IP_ADDRESS);
    }

    private void savePlatformSetting(PlatformSettingKeyEnum key, String value) {
        PlatformSetting setting = new PlatformSetting();
        setting.setSettingKey(key.getCode());
        setting.setSettingValue(value);
        platformSettingRepository.save(setting);
    }

    private void insertAccount(Long id, String username) {
        jdbcTemplate.update("INSERT INTO account (id, username, password_hash) VALUES (?, ?, ?)",
                id, username, "test-password-hash");
    }

    private void insertFamily(Long id, String name, Long creatorId) {
        jdbcTemplate.update("INSERT INTO family (id, name, invite_code, creator_id) VALUES (?, ?, ?, ?)",
                id, name, "INVITE" + id, creatorId);
    }

    private void insertMember(Long familyId, Long userId, FamilyMemberRoleEnum role) {
        jdbcTemplate.update("INSERT INTO family_member (family_id, user_id, role) VALUES (?, ?, ?)",
                familyId, userId, role.getCode());
    }

    private void insertNode(Long id, Long familyId, Long userId, String name) {
        jdbcTemplate.update("INSERT INTO family_node (id, user_id, family_id, name) VALUES (?, ?, ?, ?)",
                id, userId, familyId, name);
    }

    private void insertBinding(Long familyId, Long userId, Long nodeId) {
        jdbcTemplate.update("INSERT INTO family_user_node (family_id, user_id, node_id) VALUES (?, ?, ?)",
                familyId, userId, nodeId);
    }
}
