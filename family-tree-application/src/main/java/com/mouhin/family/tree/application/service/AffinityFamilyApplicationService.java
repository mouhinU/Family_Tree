package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.constant.OperationLogConsts;
import com.mouhin.family.tree.common.dto.AffinityCandidateVO;
import com.mouhin.family.tree.common.dto.FamilyBriefVO;
import com.mouhin.family.tree.common.dto.NodeAffinityFamilyVO;
import com.mouhin.family.tree.common.dto.NodeMemberFamilyVO;
import com.mouhin.family.tree.common.enums.BirthCalendarEnum;
import com.mouhin.family.tree.common.enums.NodeLinkSourceEnum;
import com.mouhin.family.tree.common.enums.NodeLinkTypeEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.CrossFamilyLink;
import com.mouhin.family.tree.domain.entity.Family;
import com.mouhin.family.tree.domain.entity.FamilyMember;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyUserNode;
import com.mouhin.family.tree.domain.entity.User;
import com.mouhin.family.tree.domain.event.OperationPerformedEvent;
import com.mouhin.family.tree.domain.repository.CrossFamilyLinkRepository;
import com.mouhin.family.tree.domain.repository.FamilyMemberRepository;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.FamilyRepository;
import com.mouhin.family.tree.domain.repository.FamilyUserNodeRepository;
import com.mouhin.family.tree.domain.repository.UserRepository;
import com.mouhin.family.tree.domain.service.AffinityFamilyDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 跨族亲族应用服务：亲族家族展示、归属家族展示、人工建链/解链、候选生成与家族只读摘要
 *
 * <p>边界口径（与跨族方案册一致）：缺链时空着不猜测；除账号绑定证据（「标记为我」事件驱动成链，
 * 自动账号与真人同口径）外禁止自动成链，
 * 候选仅提示、确认后才落链；查询时惰性解析不物化；按当前生效边呈现。
 * {@code @RequireFamilyManager} 切面只校验会话当前家族，解链可能由另一端家族管理者发起，
 * 故 {@link #unlinkNode} 在应用层显式校验其对链接两端家族的管理者身份。</p>
 *
 * @author Family-Tree
 * @date 2026-11-20
 */
@Service
public class AffinityFamilyApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(AffinityFamilyApplicationService.class);

    /**
     * 候选清单返回条数上限（防大族谱同名爆炸拖垮详情弹窗）
     */
    private static final int MAX_CANDIDATE_COUNT = 50;

    /**
     * 候选同名节点扫描上限
     */
    private static final int MAX_CANDIDATE_SCAN = 200;

    /**
     * 匹配置信度：录入历法口径一致精确吻合
     */
    private static final int CONFIDENCE_HIGH = 2;

    /**
     * 匹配置信度：未记录历法的存量回落比对命中
     */
    private static final int CONFIDENCE_MEDIUM = 1;

    private final FamilyNodeRepository familyNodeRepository;
    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final FamilyUserNodeRepository familyUserNodeRepository;
    private final UserRepository userRepository;
    private final CrossFamilyLinkRepository crossFamilyLinkRepository;
    private final AffinityFamilyDomainService affinityFamilyDomainService;
    private final FamilyNodeVisibilityApplicationService visibilityApplicationService;
    private final ApplicationEventPublisher eventPublisher;

    public AffinityFamilyApplicationService(FamilyNodeRepository familyNodeRepository,
                                            FamilyRepository familyRepository,
                                            FamilyMemberRepository familyMemberRepository,
                                            FamilyUserNodeRepository familyUserNodeRepository,
                                            UserRepository userRepository,
                                            CrossFamilyLinkRepository crossFamilyLinkRepository,
                                            AffinityFamilyDomainService affinityFamilyDomainService,
                                            FamilyNodeVisibilityApplicationService visibilityApplicationService,
                                            ApplicationEventPublisher eventPublisher) {
        this.familyNodeRepository = familyNodeRepository;
        this.familyRepository = familyRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.familyUserNodeRepository = familyUserNodeRepository;
        this.userRepository = userRepository;
        this.crossFamilyLinkRepository = crossFamilyLinkRepository;
        this.affinityFamilyDomainService = affinityFamilyDomainService;
        this.visibilityApplicationService = visibilityApplicationService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 解析节点的亲族家族清单（父/母/妻/夫族）
     *
     * @param familyId 会话当前家族ID
     * @param nodeId   节点ID
     * @return 亲族家族VO列表；父族恒有一条（族内父边优先、链接回指次之、回落本族）
     */
    public List<NodeAffinityFamilyVO> listAffinityFamilies(Long familyId, Long nodeId) {
        FamilyNode node = requireNodeInFamily(familyId, nodeId);
        List<AffinityFamilyDomainService.AffinityFamilyValueObject> views =
                affinityFamilyDomainService.resolveAffinityFamilies(node);
        List<NodeAffinityFamilyVO> result = new ArrayList<>(views.size());
        for (AffinityFamilyDomainService.AffinityFamilyValueObject view : views) {
            NodeAffinityFamilyVO vo = new NodeAffinityFamilyVO();
            vo.setType(view.type().getCode());
            vo.setFamilyId(view.familyId());
            vo.setFamilyName(view.familyName());
            vo.setRelatedPersonNodeId(view.relatedPersonNodeId());
            vo.setRelatedPersonName(view.relatedPersonName());
            vo.setLinkId(view.linkId());
            vo.setLinkSource(view.linkSource());
            vo.setConfirmedBy(view.confirmedBy());
            result.add(vo);
        }
        return result;
    }

    /**
     * 查询节点绑定真人账号归属的其他家族清单（Phase 0 受限展示）
     *
     * <p>语义是"该账号加入过的家族"，不区分父/母/妻(夫)族类型；仅本人与本族管理者可见，
     * 未绑定或绑定自动账号的节点返回空列表。</p>
     *
     * @param familyId 会话当前家族ID
     * @param nodeId   节点ID
     * @param userId   查看者用户ID
     * @return 归属家族VO列表（不含当前家族）
     */
    public List<NodeMemberFamilyVO> listMemberFamilies(Long familyId, Long nodeId, Long userId) {
        requireNodeInFamily(familyId, nodeId);
        FamilyUserNode binding = familyUserNodeRepository.findByFamilyIdAndNodeId(familyId, nodeId);
        if (binding == null) {
            return List.of();
        }
        User boundUser = userRepository.findById(binding.getUserId());
        if (boundUser == null || Boolean.TRUE.equals(boundUser.getAutoAccount())) {
            return List.of();
        }
        boolean isSelf = Objects.equals(userId, binding.getUserId());
        if (!isSelf && !visibilityApplicationService.isManager(familyId, userId)) {
            throw new BusinessException("仅本人或族长可查看该族人的归属家族");
        }
        List<FamilyMember> memberships = familyMemberRepository.findByUserId(binding.getUserId());
        List<FamilyMember> otherMemberships = new ArrayList<>(memberships.size());
        List<Long> otherFamilyIds = new ArrayList<>(memberships.size());
        for (FamilyMember member : memberships) {
            if (!Objects.equals(member.getFamilyId(), familyId)) {
                otherMemberships.add(member);
                otherFamilyIds.add(member.getFamilyId());
            }
        }
        if (otherFamilyIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Family> familyMap = new HashMap<>(otherFamilyIds.size() * 4 / 3 + 1);
        for (Family family : familyRepository.findByIds(otherFamilyIds)) {
            familyMap.put(family.getId(), family);
        }
        List<NodeMemberFamilyVO> result = new ArrayList<>(otherMemberships.size());
        for (FamilyMember member : otherMemberships) {
            Family family = familyMap.get(member.getFamilyId());
            if (family == null) {
                continue;
            }
            NodeMemberFamilyVO vo = new NodeMemberFamilyVO();
            vo.setFamilyId(family.getId());
            vo.setFamilyName(family.getName());
            vo.setRole(member.getRole());
            result.add(vo);
        }
        result.sort(Comparator.comparing(NodeMemberFamilyVO::getFamilyId));
        return result;
    }

    /**
     * 人工指认建链（MANUAL 通道）：当前家族管理者把本族节点关联到他族节点
     *
     * <p>A5 采纳方案册允许的降级口径：一方可建、另一方（同链接端点的任一家族管理者）可见可删；
     * 不新增待确认状态列。</p>
     *
     * @param familyId   会话当前家族ID（链接发起端）
     * @param srcNodeId  本族节点ID
     * @param dstNodeId  他族目标节点ID
     * @param linkType   链接类型编码，null 默认血缘同人
     * @param operatorId 操作者用户ID
     * @param username   操作者用户名
     * @param ipAddress  客户端IP
     * @return 新建链接ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long linkNode(Long familyId, Long srcNodeId, Long dstNodeId, Integer linkType,
                         Long operatorId, String username, String ipAddress) {
        FamilyNode srcNode = requireNodeInFamily(familyId, srcNodeId);
        FamilyNode dstNode = familyNodeRepository.findById(dstNodeId);
        if (dstNode == null) {
            throw new BusinessException("目标节点不存在");
        }
        if (Objects.equals(dstNode.getFamilyId(), familyId)) {
            throw new BusinessException("跨族链接两端必须属于不同家族");
        }
        if (crossFamilyLinkRepository.findByNodePair(srcNodeId, dstNodeId) != null) {
            throw new BusinessException("两节点已存在亲族关联");
        }
        LocalDateTime now = LocalDateTime.now();
        CrossFamilyLink link = new CrossFamilyLink();
        link.setSrcFamilyId(familyId);
        link.setSrcNodeId(srcNodeId);
        link.setDstFamilyId(dstNode.getFamilyId());
        link.setDstNodeId(dstNodeId);
        link.setLinkType(linkType != null
                ? NodeLinkTypeEnum.fromCode(linkType).getCode()
                : NodeLinkTypeEnum.BLOOD.getCode());
        link.setSource(NodeLinkSourceEnum.MANUAL.getCode());
        link.setConfirmedBy(operatorId);
        link.setCreateTime(now);
        link.setUpdateTime(now);
        link.validate();
        link.orderEndpoints();
        crossFamilyLinkRepository.save(link);

        eventPublisher.publishEvent(OperationPerformedEvent.of(operatorId, username,
                OperationLogConsts.OP_NODE_LINK,
                "建立亲族关联: " + srcNode.getName() + " ↔ " + dstNode.getName(),
                OperationLogConsts.TARGET_NODE_LINK, link.getId(), familyId, ipAddress));
        logger.info("Manual cross-family link created: linkId={} {}<->{} by userId={}",
                link.getId(), srcNodeId, dstNodeId, operatorId);
        return link.getId();
    }

    /**
     * 解除亲族关联：链接任一端家族的管理者均可操作（含候选/账号来源链的显式清除）
     *
     * @param familyId   会话当前家族ID（用于确认操作入口节点属于本族）
     * @param nodeId     操作入口节点ID（须为链接端点）
     * @param linkId     链接ID
     * @param userId     操作者用户ID
     * @param username   操作者用户名
     * @param ipAddress  客户端IP
     */
    @Transactional(rollbackFor = Exception.class)
    public void unlinkNode(Long familyId, Long nodeId, Long linkId, Long userId,
                           String username, String ipAddress) {
        requireNodeInFamily(familyId, nodeId);
        CrossFamilyLink link = crossFamilyLinkRepository.findById(linkId);
        if (link == null || !link.touchesNode(nodeId)) {
            throw new BusinessException("亲族关联不存在");
        }
        boolean srcManager = visibilityApplicationService.isManager(link.getSrcFamilyId(), userId);
        boolean dstManager = visibilityApplicationService.isManager(link.getDstFamilyId(), userId);
        if (!srcManager && !dstManager) {
            throw new BusinessException("仅家族管理者可解除亲族关联");
        }
        crossFamilyLinkRepository.removeById(linkId);

        eventPublisher.publishEvent(OperationPerformedEvent.of(userId, username,
                OperationLogConsts.OP_NODE_UNLINK, "解除亲族关联",
                OperationLogConsts.TARGET_NODE_LINK, linkId, familyId, ipAddress));
        logger.info("Cross-family link removed: linkId={} by userId={}", linkId, userId);
    }

    /**
     * 生成节点的跨族亲族候选清单（证据②：姓名相同 + 生日按录入历法口径吻合）
     *
     * <p>仅管理员可调（控制器 {@code @RequireFamilyManager} 收口）；候选不落库、忽略不留痕，
     * 确认后经 {@link #linkNode} 以 MANUAL 来源成链；已存在链接的节点对不再出候选。
     * 证据③（同手机/邮箱多账号）与④（GEDCOM XREF）本批未接入，理由见提交说明。</p>
     *
     * @param familyId 会话当前家族ID
     * @param nodeId   节点ID
     * @return 候选列表，按置信度降序，上限 {@value #MAX_CANDIDATE_COUNT} 条
     */
    public List<AffinityCandidateVO> suggestCandidates(Long familyId, Long nodeId) {
        FamilyNode node = requireNodeInFamily(familyId, nodeId);
        if (node.getName() == null || node.getName().isBlank()) {
            return List.of();
        }
        List<FamilyNode> sameNameNodes =
                familyNodeRepository.findByNameExcludeFamily(node.getName().trim(), familyId, MAX_CANDIDATE_SCAN);
        Map<Long, Family> familyCache = new HashMap<>(8);
        List<AffinityCandidateVO> candidates = new ArrayList<>();
        for (FamilyNode candidate : sameNameNodes) {
            int confidence = matchConfidence(node, candidate);
            if (confidence == 0
                    || crossFamilyLinkRepository.findByNodePair(nodeId, candidate.getId()) != null) {
                continue;
            }
            Family candidateFamily = familyCache.computeIfAbsent(candidate.getFamilyId(),
                    id -> familyRepository.findById(id));
            if (candidateFamily == null) {
                continue;
            }
            AffinityCandidateVO vo = new AffinityCandidateVO();
            vo.setNodeId(candidate.getId());
            vo.setNodeName(candidate.getName());
            vo.setGender(candidate.getGender());
            vo.setBirthDate(candidate.getBirthDate() != null ? candidate.getBirthDate().toString() : null);
            vo.setLunarBirthDate(candidate.getLunarBirthDate());
            vo.setFamilyId(candidateFamily.getId());
            vo.setFamilyName(candidateFamily.getName());
            vo.setConfidence(confidence);
            candidates.add(vo);
        }
        candidates.sort(Comparator.comparing(AffinityCandidateVO::getConfidence).reversed()
                .thenComparing(AffinityCandidateVO::getNodeId));
        return candidates.size() > MAX_CANDIDATE_COUNT
                ? new ArrayList<>(candidates.subList(0, MAX_CANDIDATE_COUNT))
                : candidates;
    }

    /**
     * 家族只读摘要（家族名/始祖/代数/成员数），不含敏感明细，任意登录用户可取
     *
     * <p>始祖口径：建族初始节点（家族内最早创建的节点）；代数取家族配置的辈分行数。</p>
     *
     * @param familyId 家族ID
     * @return 家族摘要VO
     */
    public FamilyBriefVO getFamilyBrief(Long familyId) {
        Family family = familyRepository.findById(familyId);
        if (family == null) {
            throw new BusinessException("家族不存在");
        }
        FamilyNode oldestNode = familyNodeRepository.findFirstByFamilyId(familyId);
        FamilyBriefVO vo = new FamilyBriefVO();
        vo.setFamilyId(family.getId());
        vo.setFamilyName(family.getName());
        vo.setAncestorName(oldestNode != null ? oldestNode.getName() : null);
        vo.setGenerationRows(family.getGenerationRows());
        vo.setMemberCount(familyMemberRepository.countByFamilyId(familyId));
        return vo;
    }

    /**
     * 按定稿口径比对两侧出生日期的匹配置信度：
     * 双方均阳历录入比 {@code birthDate}、任一侧阴历录入比 {@code lunarBirthDate}（精确串一致），命中为高；
     * 存在未记录历法的存量时回落"阳历为主、不符再比农历"，命中降一档为中；两侧均无日期不出候选
     */
    private int matchConfidence(FamilyNode node, FamilyNode other) {
        boolean nodeLunar = Objects.equals(node.getBirthCalendar(), BirthCalendarEnum.LUNAR.getCode());
        boolean otherLunar = Objects.equals(other.getBirthCalendar(), BirthCalendarEnum.LUNAR.getCode());
        boolean nodeRecorded = node.getBirthCalendar() != null;
        boolean otherRecorded = other.getBirthCalendar() != null;

        if (nodeRecorded && otherRecorded) {
            if (nodeLunar || otherLunar) {
                return lunarEquals(node, other) ? CONFIDENCE_HIGH : 0;
            }
            return dateEquals(node, other) ? CONFIDENCE_HIGH : 0;
        }
        if (dateEquals(node, other) || lunarEquals(node, other)) {
            return CONFIDENCE_MEDIUM;
        }
        return 0;
    }

    /**
     * 阳历生日是否双侧均有值且相同
     */
    private boolean dateEquals(FamilyNode a, FamilyNode b) {
        return a.getBirthDate() != null && Objects.equals(a.getBirthDate(), b.getBirthDate());
    }

    /**
     * 农历生日字符串是否双侧均非空且相同（含闰月口径一致才算）
     */
    private boolean lunarEquals(FamilyNode a, FamilyNode b) {
        return a.getLunarBirthDate() != null && !a.getLunarBirthDate().isBlank()
                && a.getLunarBirthDate().equals(b.getLunarBirthDate());
    }

    /**
     * 取节点并校验属于当前家族
     */
    private FamilyNode requireNodeInFamily(Long familyId, Long nodeId) {
        FamilyNode node = familyNodeRepository.findById(nodeId);
        if (node == null || !Objects.equals(node.getFamilyId(), familyId)) {
            throw new BusinessException("节点不存在或不属于当前家族");
        }
        return node;
    }
}
