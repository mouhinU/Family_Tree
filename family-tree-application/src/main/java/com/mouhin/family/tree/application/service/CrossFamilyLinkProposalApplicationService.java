package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.constant.OperationLogConsts;
import com.mouhin.family.tree.common.dto.AffinityLinkDTO;
import com.mouhin.family.tree.common.dto.CrossFamilyLinkProposalCommentDTO;
import com.mouhin.family.tree.common.dto.CrossFamilyLinkProposalEventVO;
import com.mouhin.family.tree.common.dto.CrossFamilyLinkProposalVO;
import com.mouhin.family.tree.common.dto.CrossFamilyLinkProposalVoteVO;
import com.mouhin.family.tree.common.dto.PageResult;
import com.mouhin.family.tree.common.enums.CrossFamilyLinkProposalEventTypeEnum;
import com.mouhin.family.tree.common.enums.CrossFamilyLinkProposalStatusEnum;
import com.mouhin.family.tree.common.enums.CrossFamilyLinkProposalVoteStatusEnum;
import com.mouhin.family.tree.common.enums.FamilySettingKeyEnum;
import com.mouhin.family.tree.common.enums.FamilyStatusEnum;
import com.mouhin.family.tree.common.enums.NodeLinkSourceEnum;
import com.mouhin.family.tree.common.enums.NodeLinkTypeEnum;
import com.mouhin.family.tree.common.enums.PlatformSettingKeyEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.CrossFamilyLink;
import com.mouhin.family.tree.domain.entity.CrossFamilyLinkProposal;
import com.mouhin.family.tree.domain.entity.CrossFamilyLinkProposalEvent;
import com.mouhin.family.tree.domain.entity.CrossFamilyLinkProposalVote;
import com.mouhin.family.tree.domain.entity.Family;
import com.mouhin.family.tree.domain.entity.FamilyMember;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilySetting;
import com.mouhin.family.tree.domain.entity.FamilyUserNode;
import com.mouhin.family.tree.domain.entity.User;
import com.mouhin.family.tree.domain.event.OperationPerformedEvent;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 跨族链接提案应用服务：创建、投票、撤回、取消、平台审核与到期失效。
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
@Service
public class CrossFamilyLinkProposalApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(CrossFamilyLinkProposalApplicationService.class);

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAINTENANCE_BATCH_SIZE = 200;
    private static final long MAINTENANCE_INTERVAL_MS = 60_000L;

    private final CrossFamilyLinkProposalRepository proposalRepository;
    private final CrossFamilyLinkProposalVoteRepository voteRepository;
    private final CrossFamilyLinkProposalEventRepository eventRepository;
    private final CrossFamilyLinkRepository linkRepository;
    private final FamilyNodeRepository familyNodeRepository;
    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final FamilyUserNodeRepository familyUserNodeRepository;
    private final FamilySettingRepository familySettingRepository;
    private final UserRepository userRepository;
    private final PlatformSettingApplicationService platformSettingService;
    private final FamilySettingDomainService familySettingDomainService;
    private final ApplicationEventPublisher eventPublisher;

    public CrossFamilyLinkProposalApplicationService(CrossFamilyLinkProposalRepository proposalRepository,
                                                     CrossFamilyLinkProposalVoteRepository voteRepository,
                                                     CrossFamilyLinkProposalEventRepository eventRepository,
                                                     CrossFamilyLinkRepository linkRepository,
                                                     FamilyNodeRepository familyNodeRepository,
                                                     FamilyRepository familyRepository,
                                                     FamilyMemberRepository familyMemberRepository,
                                                     FamilyUserNodeRepository familyUserNodeRepository,
                                                     FamilySettingRepository familySettingRepository,
                                                     UserRepository userRepository,
                                                     PlatformSettingApplicationService platformSettingService,
                                                     FamilySettingDomainService familySettingDomainService,
                                                     ApplicationEventPublisher eventPublisher) {
        this.proposalRepository = proposalRepository;
        this.voteRepository = voteRepository;
        this.eventRepository = eventRepository;
        this.linkRepository = linkRepository;
        this.familyNodeRepository = familyNodeRepository;
        this.familyRepository = familyRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.familyUserNodeRepository = familyUserNodeRepository;
        this.familySettingRepository = familySettingRepository;
        this.userRepository = userRepository;
        this.platformSettingService = platformSettingService;
        this.familySettingDomainService = familySettingDomainService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 创建人工跨族链接提案。
     *
     * @param dto         提案内容
     * @param familyId    当前家族ID
     * @param nodeId      当前家族节点ID
     * @param operatorId  操作人用户ID
     * @param username    操作人用户名
     * @param ipAddress   客户端IP
     * @return 提案详情
     */
    @Transactional(rollbackFor = Exception.class)
    public CrossFamilyLinkProposalVO createProposal(AffinityLinkDTO dto, Long familyId, Long nodeId,
                                                    Long operatorId, String username, String ipAddress) {
        platformSettingService.assertEnabled(PlatformSettingKeyEnum.CROSS_FAMILY_LINK_PROPOSAL_ENABLED);
        requireManager(familyId, operatorId);
        requireFamilyNormal(familyId);
        FamilyNode srcNode = requireNodeInFamily(familyId, nodeId);
        FamilyNode dstNode = familyNodeRepository.findById(dto.getDstNodeId());
        if (dstNode == null) {
            throw new BusinessException("目标节点不存在");
        }
        requireFamilyNormal(dstNode.getFamilyId());
        if (Objects.equals(dstNode.getFamilyId(), familyId)) {
            throw new BusinessException("跨族链接提案两端必须属于不同家族");
        }
        if (linkRepository.findByNodePair(nodeId, dstNode.getId()) != null
                || proposalRepository.findActiveByNodePair(nodeId, dstNode.getId()) != null) {
            throw new BusinessException("两节点已存在亲族关联或活跃提案");
        }

        int srcRequirement = effectiveRequirement(familyId);
        int dstRequirement = effectiveRequirement(dstNode.getFamilyId());
        LocalDateTime now = LocalDateTime.now();
        CrossFamilyLinkProposal proposal = new CrossFamilyLinkProposal();
        proposal.setSrcFamilyId(familyId);
        proposal.setSrcNodeId(nodeId);
        proposal.setSrcRequirement(srcRequirement);
        proposal.setDstFamilyId(dstNode.getFamilyId());
        proposal.setDstNodeId(dstNode.getId());
        proposal.setDstRequirement(dstRequirement);
        proposal.setLinkType(dto.getLinkType() == null
                ? NodeLinkTypeEnum.BLOOD.getCode() : NodeLinkTypeEnum.fromCode(dto.getLinkType()).getCode());
        proposal.setInitiatorId(operatorId);
        proposal.setInitiatorFamilyId(familyId);
        proposal.setReason(trimToNull(dto.getReason()));
        proposal.setExpireAt(now.plusDays(platformSettingService.getInt(
                PlatformSettingKeyEnum.CROSS_FAMILY_LINK_PROPOSAL_EXPIRE_DAYS)));
        proposal.setCreateTime(now);
        proposal.setUpdateTime(now);
        proposal.orderEndpoints();
        proposal.validate();
        proposal.changeStatus(CrossFamilyLinkProposalStatusEnum.PENDING_VOTES, now);
        try {
            proposalRepository.save(proposal);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("两节点已存在活跃提案");
        }
        saveEvent(proposal, CrossFamilyLinkProposalEventTypeEnum.CREATED, null, proposal.getStatus(),
                operatorId, familyId, proposal.getReason(), ipAddress, now);
        publishAudit(operatorId, username, OperationLogConsts.OP_NODE_LINK_PROPOSAL_CREATE,
                "创建跨族链接提案: " + srcNode.getName() + " ↔ " + dstNode.getName(),
                proposal.getId(), familyId, ipAddress);
        logger.info("Cross-family link proposal created: proposalId={} {}<->{} by userId={}",
                proposal.getId(), nodeId, dstNode.getId(), operatorId);
        return buildVO(proposal, operatorId, familyId, true, now);
    }

    /**
     * 当前家族成员对所绑定节点提案表示同意。
     *
     * @param proposalId 提案ID
     * @param familyId   当前家族ID
     * @param userId     投票用户ID
     * @param username   用户名
     * @param ipAddress  客户端IP
     * @return 提案详情
     */
    @Transactional(rollbackFor = Exception.class)
    public CrossFamilyLinkProposalVO agree(Long proposalId, Long familyId, Long userId,
                                           String username, String ipAddress) {
        LocalDateTime now = LocalDateTime.now();
        CrossFamilyLinkProposal proposal = requireProposalForUpdate(proposalId);
        proposal.requireVotingOpen(now);
        requireFamilyNormal(familyId);
        FamilyUserNode binding = requireVoter(proposal, familyId, userId);
        List<CrossFamilyLinkProposalVote> votes = voteRepository.listByProposalId(proposalId);
        CrossFamilyLinkProposalVote vote = findByNode(votes, binding.getNodeId());
        if (vote != null && isActive(vote) && isValidVote(proposal, vote)
                && Objects.equals(vote.getUserId(), userId) && Objects.equals(vote.getFamilyId(), familyId)) {
            return buildVO(proposal, userId, familyId, true, now);
        }
        normalizeVotes(proposal, votes, now);
        if (vote != null && isActive(vote) && isValidVote(proposal, vote)) {
            throw new BusinessException("该节点已由其他账号投票");
        }
        String fromStatus = proposal.getStatus();
        if (vote == null) {
            vote = new CrossFamilyLinkProposalVote();
            vote.setProposalId(proposalId);
            vote.setFamilyId(familyId);
            vote.setNodeId(binding.getNodeId());
            vote.setUserId(userId);
            vote.setStatus(CrossFamilyLinkProposalVoteStatusEnum.ACTIVE.getCode());
            vote.setCreateTime(now);
            vote.setUpdateTime(now);
            voteRepository.save(vote);
        } else {
            vote.setUserId(userId);
            vote.setStatus(CrossFamilyLinkProposalVoteStatusEnum.ACTIVE.getCode());
            vote.setUpdateTime(now);
            voteRepository.update(vote);
        }
        VoteCounts counts = countValidVotes(proposal, voteRepository.listByProposalId(proposalId));
        boolean ready = meetsRequirements(proposal, counts);
        if (ready && CrossFamilyLinkProposalStatusEnum.PENDING_VOTES.getCode().equals(proposal.getStatus())) {
            proposal.changeStatus(CrossFamilyLinkProposalStatusEnum.READY_FOR_REVIEW, now);
            proposalRepository.update(proposal);
        }
        saveEvent(proposal, CrossFamilyLinkProposalEventTypeEnum.AGREED, fromStatus, proposal.getStatus(),
                userId, familyId, null, ipAddress, now);
        if (ready) {
            saveEvent(proposal, CrossFamilyLinkProposalEventTypeEnum.READY_FOR_REVIEW,
                    proposal.getStatus(), proposal.getStatus(), userId, familyId, null, ipAddress, now);
        }
        publishAudit(userId, username, OperationLogConsts.OP_NODE_LINK_PROPOSAL_AGREE,
                "同意跨族链接提案", proposalId, familyId, ipAddress);
        return buildVO(proposal, userId, familyId, true, now);
    }

    /**
     * 撤回当前用户对提案的同意。
     *
     * @param proposalId 提案ID
     * @param familyId   当前家族ID
     * @param userId     投票用户ID
     * @param username   用户名
     * @param ipAddress  客户端IP
     * @return 提案详情
     */
    @Transactional(rollbackFor = Exception.class)
    public CrossFamilyLinkProposalVO withdraw(Long proposalId, Long familyId, Long userId,
                                              String username, String ipAddress) {
        LocalDateTime now = LocalDateTime.now();
        CrossFamilyLinkProposal proposal = requireProposalForUpdate(proposalId);
        requireActive(proposal);
        requireNotExpired(proposal, now);
        if (!touchesFamily(proposal, familyId)) {
            throw new BusinessException("当前家族不是提案端点家族");
        }
        CrossFamilyLinkProposalVote vote = findByUserAndFamily(
                voteRepository.listByProposalId(proposalId), userId, familyId);
        if (vote == null || !Objects.equals(vote.getUserId(), userId)
                || !Objects.equals(vote.getFamilyId(), familyId)) {
            throw new BusinessException("当前账号未对该提案投票");
        }
        if (!isActive(vote)) {
            return buildVO(proposal, userId, familyId, true, now);
        }
        String fromStatus = proposal.getStatus();
        vote.setStatus(CrossFamilyLinkProposalVoteStatusEnum.REVOKED.getCode());
        vote.setUpdateTime(now);
        voteRepository.update(vote);
        VoteCounts counts = countValidVotes(proposal, voteRepository.listByProposalId(proposalId));
        if (CrossFamilyLinkProposalStatusEnum.READY_FOR_REVIEW.getCode().equals(proposal.getStatus())
                && !meetsRequirements(proposal, counts)) {
            proposal.changeStatus(CrossFamilyLinkProposalStatusEnum.PENDING_VOTES, now);
            proposalRepository.update(proposal);
        }
        saveEvent(proposal, CrossFamilyLinkProposalEventTypeEnum.WITHDRAWN, fromStatus, proposal.getStatus(),
                userId, familyId, null, ipAddress, now);
        publishAudit(userId, username, OperationLogConsts.OP_NODE_LINK_PROPOSAL_WITHDRAW,
                "撤回跨族链接提案同意", proposalId, familyId, ipAddress);
        return buildVO(proposal, userId, familyId, true, now);
    }

    /**
     * 发起人或任一端家族管理者取消活跃提案。
     *
     * @param proposalId 提案ID
     * @param familyId   当前家族ID
     * @param userId     操作人用户ID
     * @param username   用户名
     * @param ipAddress  客户端IP
     * @return 提案详情
     */
    @Transactional(rollbackFor = Exception.class)
    public CrossFamilyLinkProposalVO cancel(Long proposalId, Long familyId, Long userId,
                                            String username, String ipAddress) {
        LocalDateTime now = LocalDateTime.now();
        CrossFamilyLinkProposal proposal = requireProposalForUpdate(proposalId);
        requireActive(proposal);
        requireNotExpired(proposal, now);
        boolean initiator = Objects.equals(proposal.getInitiatorId(), userId);
        boolean manager = isManager(proposal.getSrcFamilyId(), userId) || isManager(proposal.getDstFamilyId(), userId);
        if (!initiator && !manager) {
            throw new BusinessException("仅发起人或提案两端家族管理者可取消提案");
        }
        String fromStatus = proposal.getStatus();
        proposal.changeStatus(CrossFamilyLinkProposalStatusEnum.CANCELLED, now);
        proposalRepository.update(proposal);
        saveEvent(proposal, CrossFamilyLinkProposalEventTypeEnum.CANCELLED, fromStatus, proposal.getStatus(),
                userId, familyId, null, ipAddress, now);
        publishAudit(userId, username, OperationLogConsts.OP_NODE_LINK_PROPOSAL_CANCEL,
                "取消跨族链接提案", proposalId, familyId, ipAddress);
        return buildVO(proposal, userId, familyId, true, now);
    }

    /**
     * 查询当前家族相关提案。
     *
     * @param familyId 当前家族ID
     * @param userId   当前用户ID
     * @param page     页码
     * @param size     每页大小
     * @return 提案分页
     */
    public PageResult<CrossFamilyLinkProposalVO> listFamilyProposals(Long familyId, Long userId, int page, int size) {
        requireMember(familyId, userId);
        validatePage(page, size);
        List<CrossFamilyLinkProposal> proposals = proposalRepository.listByFamilyId(familyId);
        int fromIndex = Math.min((page - 1) * size, proposals.size());
        int toIndex = Math.min(fromIndex + size, proposals.size());
        List<CrossFamilyLinkProposalVO> records = new ArrayList<>(toIndex - fromIndex);
        LocalDateTime now = LocalDateTime.now();
        for (CrossFamilyLinkProposal proposal : proposals.subList(fromIndex, toIndex)) {
            records.add(buildVO(proposal, userId, familyId, false, now));
        }
        return new PageResult<>(records, proposals.size(), page, size);
    }

    /**
     * 查询当前家族可见的提案详情。
     *
     * @param proposalId 提案ID
     * @param familyId   当前家族ID
     * @param userId     当前用户ID
     * @return 提案详情
     */
    public CrossFamilyLinkProposalVO getFamilyProposal(Long proposalId, Long familyId, Long userId) {
        CrossFamilyLinkProposal proposal = requireProposal(proposalId);
        requireMember(familyId, userId);
        if (!touchesFamily(proposal, familyId)) {
            throw new BusinessException("无权查看该提案");
        }
        return buildVO(proposal, userId, familyId, true, LocalDateTime.now());
    }

    /**
     * 平台管理员分页查询提案。
     *
     * @param status 状态编码，可为空
     * @param page   页码
     * @param size   每页大小
     * @return 提案分页
     */
    public PageResult<CrossFamilyLinkProposalVO> listAdminProposals(String status, int page, int size) {
        validatePage(page, size);
        String normalizedStatus = normalizeStatus(status);
        long total = proposalRepository.countForAdmin(normalizedStatus);
        if (total == 0L) {
            return new PageResult<>(List.of(), total, page, size);
        }
        List<CrossFamilyLinkProposal> proposals = proposalRepository.listForAdmin(
                normalizedStatus, (page - 1) * size, size);
        List<CrossFamilyLinkProposalVO> records = new ArrayList<>(proposals.size());
        LocalDateTime now = LocalDateTime.now();
        for (CrossFamilyLinkProposal proposal : proposals) {
            records.add(buildVO(proposal, null, null, false, now));
        }
        return new PageResult<>(records, total, page, size);
    }

    /**
     * 平台管理员查询提案详情。
     *
     * @param proposalId 提案ID
     * @return 提案详情
     */
    public CrossFamilyLinkProposalVO getAdminProposal(Long proposalId) {
        return buildVO(requireProposal(proposalId), null, null, true, LocalDateTime.now());
    }

    /**
     * 平台管理员追加提案备注。
     *
     * @param proposalId 提案ID
     * @param dto        备注内容
     * @param operatorId 操作人用户ID
     * @param username   操作人用户名
     * @param ipAddress  客户端IP
     * @return 提案详情
     */
    @Transactional(rollbackFor = Exception.class)
    public CrossFamilyLinkProposalVO comment(Long proposalId, CrossFamilyLinkProposalCommentDTO dto,
                                             Long operatorId, String username, String ipAddress) {
        LocalDateTime now = LocalDateTime.now();
        CrossFamilyLinkProposal proposal = requireProposalForUpdate(proposalId);
        requireActive(proposal);
        requireNotExpired(proposal, now);
        String comment = trimToNull(dto == null ? null : dto.getComment());
        if (comment == null) {
            throw new BusinessException("备注内容不能为空");
        }
        saveEvent(proposal, CrossFamilyLinkProposalEventTypeEnum.COMMENTED,
                proposal.getStatus(), proposal.getStatus(), operatorId, null, comment, ipAddress, now);
        publishAudit(operatorId, username, OperationLogConsts.OP_NODE_LINK_PROPOSAL_REVIEW,
                "备注跨族链接提案", proposalId, null, ipAddress);
        return buildVO(proposal, null, null, true, now);
    }

    /**
     * 平台管理员审核通过并原子创建正式链接。
     *
     * @param proposalId 提案ID
     * @param dto        审核意见
     * @param operatorId 操作人用户ID
     * @param username   操作人用户名
     * @param ipAddress  客户端IP
     * @return 提案详情
     */
    @Transactional(rollbackFor = Exception.class)
    public CrossFamilyLinkProposalVO approve(Long proposalId, CrossFamilyLinkProposalCommentDTO dto,
                                             Long operatorId, String username, String ipAddress) {
        LocalDateTime now = LocalDateTime.now();
        CrossFamilyLinkProposal proposal = requireProposalForUpdate(proposalId);
        proposal.requireReviewable();
        requireNotExpired(proposal, now);
        assertEndpointsValid(proposal);
        VoteCounts counts = countValidVotes(proposal, voteRepository.listByProposalId(proposalId));
        if (!meetsRequirements(proposal, counts)) {
            throw new BusinessException("有效同意数不足，提案需重新征集投票");
        }
        if (linkRepository.findByNodePair(proposal.getSrcNodeId(), proposal.getDstNodeId()) != null) {
            throw new BusinessException("两节点已存在亲族关联");
        }
        CrossFamilyLink link = new CrossFamilyLink();
        link.setSrcFamilyId(proposal.getSrcFamilyId());
        link.setSrcNodeId(proposal.getSrcNodeId());
        link.setDstFamilyId(proposal.getDstFamilyId());
        link.setDstNodeId(proposal.getDstNodeId());
        link.setLinkType(proposal.getLinkType());
        link.setSource(NodeLinkSourceEnum.MANUAL.getCode());
        link.setProposalId(proposalId);
        link.setConfirmedBy(operatorId);
        link.setCreateTime(now);
        link.setUpdateTime(now);
        link.validate();
        link.orderEndpoints();
        try {
            linkRepository.save(link);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("两节点已存在亲族关联");
        }
        String fromStatus = proposal.getStatus();
        proposal.setReviewComment(trimToNull(dto == null ? null : dto.getComment()));
        proposal.setReviewedBy(operatorId);
        proposal.setReviewedAt(now);
        proposal.changeStatus(CrossFamilyLinkProposalStatusEnum.APPROVED, now);
        proposalRepository.update(proposal);
        saveEvent(proposal, CrossFamilyLinkProposalEventTypeEnum.APPROVED, fromStatus, proposal.getStatus(),
                operatorId, null, proposal.getReviewComment(), ipAddress, now);
        publishAudit(operatorId, username, OperationLogConsts.OP_NODE_LINK_PROPOSAL_REVIEW,
                "审核通过跨族链接提案并建立正式关联", proposalId, null, ipAddress);
        logger.info("Cross-family link proposal approved: proposalId={} linkId={} by userId={}",
                proposalId, link.getId(), operatorId);
        return buildVO(proposal, null, null, true, now);
    }

    /**
     * 平台管理员驳回提案。
     *
     * @param proposalId 提案ID
     * @param dto        审核意见
     * @param operatorId 操作人用户ID
     * @param username   操作人用户名
     * @param ipAddress  客户端IP
     * @return 提案详情
     */
    @Transactional(rollbackFor = Exception.class)
    public CrossFamilyLinkProposalVO reject(Long proposalId, CrossFamilyLinkProposalCommentDTO dto,
                                            Long operatorId, String username, String ipAddress) {
        LocalDateTime now = LocalDateTime.now();
        CrossFamilyLinkProposal proposal = requireProposalForUpdate(proposalId);
        proposal.requireReviewable();
        requireNotExpired(proposal, now);
        String comment = trimToNull(dto == null ? null : dto.getComment());
        if (comment == null) {
            throw new BusinessException("驳回时必须填写审核意见");
        }
        String fromStatus = proposal.getStatus();
        proposal.setReviewComment(comment);
        proposal.setReviewedBy(operatorId);
        proposal.setReviewedAt(now);
        proposal.changeStatus(CrossFamilyLinkProposalStatusEnum.REJECTED, now);
        proposalRepository.update(proposal);
        saveEvent(proposal, CrossFamilyLinkProposalEventTypeEnum.REJECTED, fromStatus, proposal.getStatus(),
                operatorId, null, comment, ipAddress, now);
        publishAudit(operatorId, username, OperationLogConsts.OP_NODE_LINK_PROPOSAL_REVIEW,
                "驳回跨族链接提案", proposalId, null, ipAddress);
        return buildVO(proposal, null, null, true, now);
    }

    /**
     * 定时处理提案到期、端点失效与投票资格失效。
     */
    @Scheduled(fixedDelay = MAINTENANCE_INTERVAL_MS)
    @Transactional(rollbackFor = Exception.class)
    public void maintainProposals() {
        LocalDateTime now = LocalDateTime.now();
        List<CrossFamilyLinkProposal> proposals = proposalRepository.listActive(MAINTENANCE_BATCH_SIZE);
        for (CrossFamilyLinkProposal snapshot : proposals) {
            CrossFamilyLinkProposal proposal = proposalRepository.findByIdForUpdate(snapshot.getId());
            if (proposal == null || !proposal.isActive()) {
                continue;
            }
            if (isExpired(proposal, now)) {
                String fromStatus = proposal.getStatus();
                proposal.changeStatus(CrossFamilyLinkProposalStatusEnum.EXPIRED, now);
                proposalRepository.update(proposal);
                saveEvent(proposal, CrossFamilyLinkProposalEventTypeEnum.EXPIRED, fromStatus, proposal.getStatus(),
                        null, null, null, null, now);
                continue;
            }
            if (!endpointsValid(proposal)) {
                String fromStatus = proposal.getStatus();
                proposal.changeStatus(CrossFamilyLinkProposalStatusEnum.INVALIDATED, now);
                proposalRepository.update(proposal);
                saveEvent(proposal, CrossFamilyLinkProposalEventTypeEnum.INVALIDATED, fromStatus,
                        proposal.getStatus(), null, null, "提案端点或家族状态失效", null, now);
                continue;
            }
            normalizeVotes(proposal, voteRepository.listByProposalId(proposal.getId()), now);
        }
    }

    private CrossFamilyLinkProposal requireProposal(Long proposalId) {
        CrossFamilyLinkProposal proposal = proposalRepository.findById(proposalId);
        if (proposal == null) {
            throw new BusinessException("跨族链接提案不存在");
        }
        return proposal;
    }

    private CrossFamilyLinkProposal requireProposalForUpdate(Long proposalId) {
        CrossFamilyLinkProposal proposal = proposalRepository.findByIdForUpdate(proposalId);
        if (proposal == null) {
            throw new BusinessException("跨族链接提案不存在");
        }
        return proposal;
    }

    private void requireActive(CrossFamilyLinkProposal proposal) {
        if (!proposal.isActive()) {
            throw new BusinessException("提案已结束，无法继续操作");
        }
    }

    private void requireNotExpired(CrossFamilyLinkProposal proposal, LocalDateTime now) {
        if (isExpired(proposal, now)) {
            throw new BusinessException("提案已过期");
        }
    }

    private boolean isExpired(CrossFamilyLinkProposal proposal, LocalDateTime now) {
        return proposal.isActive() && (proposal.getExpireAt() == null || !now.isBefore(proposal.getExpireAt()));
    }

    private void requireManager(Long familyId, Long userId) {
        if (!isManager(familyId, userId)) {
            throw new BusinessException("仅家族管理者可创建跨族链接提案");
        }
    }

    private boolean isManager(Long familyId, Long userId) {
        FamilyMember member = familyMemberRepository.findByFamilyIdAndUserId(familyId, userId);
        return member != null && member.isManager();
    }

    private void requireMember(Long familyId, Long userId) {
        if (familyMemberRepository.findByFamilyIdAndUserId(familyId, userId) == null) {
            throw new BusinessException("仅提案相关家族成员可操作");
        }
    }

    private void requireFamilyNormal(Long familyId) {
        Family family = familyRepository.findById(familyId);
        if (family == null || family.statusOrDefault() != FamilyStatusEnum.NORMAL) {
            throw new BusinessException("提案相关家族不存在或当前不可用");
        }
    }

    private FamilyNode requireNodeInFamily(Long familyId, Long nodeId) {
        FamilyNode node = familyNodeRepository.findById(nodeId);
        if (node == null || !Objects.equals(node.getFamilyId(), familyId)) {
            throw new BusinessException("节点不存在或不属于当前家族");
        }
        return node;
    }

    private FamilyUserNode requireVoter(CrossFamilyLinkProposal proposal, Long familyId, Long userId) {
        Long nodeId = endpointNodeId(proposal, familyId);
        if (nodeId == null) {
            throw new BusinessException("当前家族不是提案端点家族");
        }
        requireMember(familyId, userId);
        FamilyUserNode binding = familyUserNodeRepository.findByFamilyIdAndUserId(familyId, userId);
        if (binding == null) {
            throw new BusinessException("投票账号必须绑定到本家族节点");
        }
        FamilyNode node = familyNodeRepository.findById(binding.getNodeId());
        if (node == null || !Objects.equals(node.getFamilyId(), familyId)) {
            throw new BusinessException("投票节点已失效");
        }
        for (CrossFamilyLinkProposalVote vote : voteRepository.listByProposalId(proposal.getId())) {
            if (isActive(vote) && isValidVote(proposal, vote) && Objects.equals(vote.getUserId(), userId)
                    && !Objects.equals(vote.getFamilyId(), familyId)) {
                throw new BusinessException("同一账号不能在提案两端家族都投票");
            }
        }
        return binding;
    }

    private Long endpointNodeId(CrossFamilyLinkProposal proposal, Long familyId) {
        if (Objects.equals(proposal.getSrcFamilyId(), familyId)) {
            return proposal.getSrcNodeId();
        }
        if (Objects.equals(proposal.getDstFamilyId(), familyId)) {
            return proposal.getDstNodeId();
        }
        return null;
    }

    private boolean touchesFamily(CrossFamilyLinkProposal proposal, Long familyId) {
        return Objects.equals(proposal.getSrcFamilyId(), familyId)
                || Objects.equals(proposal.getDstFamilyId(), familyId);
    }

    private int effectiveRequirement(Long familyId) {
        Map<String, String> overrides = new LinkedHashMap<>(FamilySettingKeyEnum.listAll().size() * 2);
        for (FamilySetting setting : familySettingRepository.findByFamilyId(familyId)) {
            overrides.put(setting.getSettingKey(), setting.getSettingValue());
        }
        Map<String, String> effective = familySettingDomainService.mergeWithDefaults(
                overrides, platformSettingService.familyScopedDefaults());
        return Integer.parseInt(effective.get(FamilySettingKeyEnum.CROSS_FAMILY_LINK_MIN_AGREEMENTS.getCode()));
    }

    private boolean endpointsValid(CrossFamilyLinkProposal proposal) {
        Family srcFamily = familyRepository.findById(proposal.getSrcFamilyId());
        Family dstFamily = familyRepository.findById(proposal.getDstFamilyId());
        if (srcFamily == null || dstFamily == null
                || srcFamily.statusOrDefault() != FamilyStatusEnum.NORMAL
                || dstFamily.statusOrDefault() != FamilyStatusEnum.NORMAL) {
            return false;
        }
        FamilyNode srcNode = familyNodeRepository.findById(proposal.getSrcNodeId());
        FamilyNode dstNode = familyNodeRepository.findById(proposal.getDstNodeId());
        return srcNode != null && dstNode != null
                && Objects.equals(srcNode.getFamilyId(), proposal.getSrcFamilyId())
                && Objects.equals(dstNode.getFamilyId(), proposal.getDstFamilyId());
    }

    private void assertEndpointsValid(CrossFamilyLinkProposal proposal) {
        if (!endpointsValid(proposal)) {
            throw new BusinessException("提案端点或家族状态已失效");
        }
    }

    private boolean isValidVote(CrossFamilyLinkProposal proposal, CrossFamilyLinkProposalVote vote) {
        if (!isActive(vote) || !touchesFamily(proposal, vote.getFamilyId())) {
            return false;
        }
        FamilyMember member = familyMemberRepository.findByFamilyIdAndUserId(vote.getFamilyId(), vote.getUserId());
        FamilyUserNode binding = familyUserNodeRepository.findByFamilyIdAndUserId(vote.getFamilyId(), vote.getUserId());
        FamilyNode node = familyNodeRepository.findById(vote.getNodeId());
        Family family = familyRepository.findById(vote.getFamilyId());
        return member != null && binding != null && node != null && family != null
                && family.statusOrDefault() == FamilyStatusEnum.NORMAL
                && Objects.equals(binding.getNodeId(), vote.getNodeId())
                && Objects.equals(node.getFamilyId(), vote.getFamilyId())
                && userRepository.findById(vote.getUserId()) != null;
    }

    private boolean normalizeVotes(CrossFamilyLinkProposal proposal,
                                   List<CrossFamilyLinkProposalVote> votes, LocalDateTime now) {
        boolean changed = false;
        for (CrossFamilyLinkProposalVote vote : votes) {
            if (isActive(vote) && !isValidVote(proposal, vote)) {
                vote.setStatus(CrossFamilyLinkProposalVoteStatusEnum.REVOKED.getCode());
                vote.setUpdateTime(now);
                voteRepository.update(vote);
                saveEvent(proposal, CrossFamilyLinkProposalEventTypeEnum.WITHDRAWN,
                        proposal.getStatus(), proposal.getStatus(), null, vote.getFamilyId(),
                        "成员关系、节点绑定或节点归属发生变化，原投票失效", null, now);
                changed = true;
            }
        }
        VoteCounts counts = countValidVotes(proposal, votes);
        if (CrossFamilyLinkProposalStatusEnum.READY_FOR_REVIEW.getCode().equals(proposal.getStatus())
                && !meetsRequirements(proposal, counts)) {
            String fromStatus = proposal.getStatus();
            proposal.changeStatus(CrossFamilyLinkProposalStatusEnum.PENDING_VOTES, now);
            proposalRepository.update(proposal);
            saveEvent(proposal, CrossFamilyLinkProposalEventTypeEnum.WITHDRAWN, fromStatus,
                    proposal.getStatus(), null, null, "有效同意数不足，提案重新进入投票阶段", null, now);
            changed = true;
        }
        return changed;
    }

    private VoteCounts countValidVotes(CrossFamilyLinkProposal proposal,
                                       List<CrossFamilyLinkProposalVote> votes) {
        Set<Long> srcNodes = new HashSet<>(proposal.getSrcRequirement() * 2);
        Set<Long> dstNodes = new HashSet<>(proposal.getDstRequirement() * 2);
        Set<Long> voters = new HashSet<>(votes.size() * 2);
        for (CrossFamilyLinkProposalVote vote : votes) {
            if (!isActive(vote) || !isValidVote(proposal, vote) || !voters.add(vote.getUserId())) {
                continue;
            }
            if (Objects.equals(vote.getFamilyId(), proposal.getSrcFamilyId())) {
                srcNodes.add(vote.getNodeId());
            } else if (Objects.equals(vote.getFamilyId(), proposal.getDstFamilyId())) {
                dstNodes.add(vote.getNodeId());
            }
        }
        return new VoteCounts(srcNodes.size(), dstNodes.size());
    }

    private boolean meetsRequirements(CrossFamilyLinkProposal proposal, VoteCounts counts) {
        return counts.srcCount() >= proposal.getSrcRequirement() && counts.dstCount() >= proposal.getDstRequirement();
    }

    private CrossFamilyLinkProposalVote findByNode(List<CrossFamilyLinkProposalVote> votes, Long nodeId) {
        for (CrossFamilyLinkProposalVote vote : votes) {
            if (Objects.equals(vote.getNodeId(), nodeId)) {
                return vote;
            }
        }
        return null;
    }

    private boolean isActive(CrossFamilyLinkProposalVote vote) {
        return Objects.equals(vote.getStatus(), CrossFamilyLinkProposalVoteStatusEnum.ACTIVE.getCode());
    }

    private CrossFamilyLinkProposalVO buildVO(CrossFamilyLinkProposal proposal, Long viewerUserId,
                                              Long viewerFamilyId, boolean includeDetails, LocalDateTime now) {
        CrossFamilyLinkProposalVO vo = new CrossFamilyLinkProposalVO();
        vo.setId(proposal.getId());
        vo.setSrcFamilyId(proposal.getSrcFamilyId());
        vo.setSrcFamilyName(familyName(proposal.getSrcFamilyId()));
        vo.setSrcNodeId(proposal.getSrcNodeId());
        vo.setSrcNodeName(nodeName(proposal.getSrcNodeId()));
        vo.setDstFamilyId(proposal.getDstFamilyId());
        vo.setDstFamilyName(familyName(proposal.getDstFamilyId()));
        vo.setDstNodeId(proposal.getDstNodeId());
        vo.setDstNodeName(nodeName(proposal.getDstNodeId()));
        vo.setLinkType(proposal.getLinkType());
        vo.setStatus(proposal.getStatus());
        vo.setSrcRequirement(proposal.getSrcRequirement());
        vo.setDstRequirement(proposal.getDstRequirement());
        vo.setInitiatorId(proposal.getInitiatorId());
        vo.setInitiatorName(userName(proposal.getInitiatorId()));
        vo.setInitiatorFamilyId(proposal.getInitiatorFamilyId());
        vo.setReason(proposal.getReason());
        vo.setReviewComment(proposal.getReviewComment());
        vo.setReviewedBy(proposal.getReviewedBy());
        vo.setReviewedAt(proposal.getReviewedAt());
        vo.setExpireAt(proposal.getExpireAt());
        vo.setCreateTime(proposal.getCreateTime());
        vo.setUpdateTime(proposal.getUpdateTime());
        vo.setExpired(isExpired(proposal, now));
        List<CrossFamilyLinkProposalVote> votes = voteRepository.listByProposalId(proposal.getId());
        VoteCounts counts = countValidVotes(proposal, votes);
        vo.setSrcAgreementCount((long) counts.srcCount());
        vo.setDstAgreementCount((long) counts.dstCount());
        vo.setViewerCanVote(canVote(proposal, viewerUserId, viewerFamilyId, votes, now));
        vo.setViewerHasVoted(hasVoted(proposal, votes, viewerUserId));
        if (includeDetails) {
            vo.setVotes(buildVoteVOs(proposal, votes));
            vo.setEvents(buildEventVOs(eventRepository.listByProposalId(proposal.getId())));
        }
        return vo;
    }

    private boolean canVote(CrossFamilyLinkProposal proposal, Long userId, Long familyId,
                            List<CrossFamilyLinkProposalVote> votes, LocalDateTime now) {
        if (userId == null || familyId == null || isExpired(proposal, now)
                || !CrossFamilyLinkProposalStatusEnum.PENDING_VOTES.getCode().equals(proposal.getStatus())
                || !touchesFamily(proposal, familyId) || hasVoted(proposal, votes, userId)) {
            return false;
        }
        if (familyMemberRepository.findByFamilyIdAndUserId(familyId, userId) == null) {
            return false;
        }
        Family family = familyRepository.findById(familyId);
        FamilyUserNode binding = familyUserNodeRepository.findByFamilyIdAndUserId(familyId, userId);
        if (family == null || family.statusOrDefault() != FamilyStatusEnum.NORMAL || binding == null) {
            return false;
        }
        FamilyNode node = familyNodeRepository.findById(binding.getNodeId());
        return node != null && Objects.equals(node.getFamilyId(), familyId);
    }

    private boolean hasVoted(CrossFamilyLinkProposal proposal,
                            List<CrossFamilyLinkProposalVote> votes, Long userId) {
        if (userId == null) {
            return false;
        }
        for (CrossFamilyLinkProposalVote vote : votes) {
            if (Objects.equals(vote.getUserId(), userId) && isValidVote(proposal, vote)) {
                return true;
            }
        }
        return false;
    }

    private CrossFamilyLinkProposalVote findByUserAndFamily(List<CrossFamilyLinkProposalVote> votes,
                                                           Long userId, Long familyId) {
        for (CrossFamilyLinkProposalVote vote : votes) {
            if (Objects.equals(vote.getUserId(), userId) && Objects.equals(vote.getFamilyId(), familyId)) {
                return vote;
            }
        }
        return null;
    }

    private List<CrossFamilyLinkProposalVoteVO> buildVoteVOs(CrossFamilyLinkProposal proposal,
                                                             List<CrossFamilyLinkProposalVote> votes) {
        List<CrossFamilyLinkProposalVoteVO> result = new ArrayList<>(votes.size());
        for (CrossFamilyLinkProposalVote vote : votes) {
            CrossFamilyLinkProposalVoteVO vo = new CrossFamilyLinkProposalVoteVO();
            vo.setId(vote.getId());
            vo.setProposalId(vote.getProposalId());
            vo.setFamilyId(vote.getFamilyId());
            vo.setNodeId(vote.getNodeId());
            vo.setNodeName(nodeName(vote.getNodeId()));
            vo.setUserId(vote.getUserId());
            vo.setUsername(userName(vote.getUserId()));
            vo.setStatus(vote.getStatus() == null
                    ? null : CrossFamilyLinkProposalVoteStatusEnum.fromCode(vote.getStatus()).name());
            vo.setValid(isActive(vote) && isValidVote(proposal, vote));
            vo.setCreateTime(vote.getCreateTime());
            vo.setUpdateTime(vote.getUpdateTime());
            result.add(vo);
        }
        return result;
    }

    private List<CrossFamilyLinkProposalEventVO> buildEventVOs(List<CrossFamilyLinkProposalEvent> events) {
        List<CrossFamilyLinkProposalEventVO> result = new ArrayList<>(events.size());
        for (CrossFamilyLinkProposalEvent event : events) {
            CrossFamilyLinkProposalEventVO vo = new CrossFamilyLinkProposalEventVO();
            vo.setId(event.getId());
            vo.setProposalId(event.getProposalId());
            vo.setEventType(event.getEventType());
            vo.setFromStatus(event.getFromStatus());
            vo.setToStatus(event.getToStatus());
            vo.setOperatorId(event.getOperatorId());
            vo.setOperatorName(userName(event.getOperatorId()));
            vo.setOperatorFamilyId(event.getOperatorFamilyId());
            vo.setComment(event.getComment());
            vo.setIpAddress(event.getIpAddress());
            vo.setCreateTime(event.getCreateTime());
            result.add(vo);
        }
        return result;
    }

    private void saveEvent(CrossFamilyLinkProposal proposal, CrossFamilyLinkProposalEventTypeEnum type,
                           String fromStatus, String toStatus, Long operatorId, Long operatorFamilyId,
                           String comment, String ipAddress, LocalDateTime now) {
        CrossFamilyLinkProposalEvent event = new CrossFamilyLinkProposalEvent();
        event.setProposalId(proposal.getId());
        event.setEventType(type.getCode());
        event.setFromStatus(fromStatus);
        event.setToStatus(toStatus);
        event.setOperatorId(operatorId);
        event.setOperatorFamilyId(operatorFamilyId);
        event.setComment(comment);
        event.setIpAddress(ipAddress);
        event.setCreateTime(now);
        eventRepository.save(event);
    }

    private void publishAudit(Long userId, String username, String operationType, String description,
                              Long targetId, Long familyId, String ipAddress) {
        eventPublisher.publishEvent(OperationPerformedEvent.of(userId, username, operationType, description,
                OperationLogConsts.TARGET_NODE_LINK_PROPOSAL, targetId, familyId, ipAddress));
    }

    private String familyName(Long familyId) {
        Family family = familyRepository.findById(familyId);
        return family == null ? null : family.getName();
    }

    private String nodeName(Long nodeId) {
        FamilyNode node = familyNodeRepository.findById(nodeId);
        return node == null ? null : node.getName();
    }

    private String userName(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = userRepository.findById(userId);
        return user == null ? null : user.getUsername();
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return CrossFamilyLinkProposalStatusEnum.fromCode(status.trim()).getCode();
        } catch (IllegalArgumentException e) {
            throw new BusinessException("不支持的提案状态：" + status);
        }
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException("分页参数无效，每页最多" + MAX_PAGE_SIZE + "条");
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record VoteCounts(int srcCount, int dstCount) {
    }
}
