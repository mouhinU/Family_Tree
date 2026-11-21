package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.constant.AccountConsts;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.constant.OperationLogConsts;
import com.mouhin.family.tree.common.enums.FamilyMemberRoleEnum;
import com.mouhin.family.tree.common.enums.NodeLinkSourceEnum;
import com.mouhin.family.tree.common.enums.NodeLinkTypeEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.CrossFamilyLink;
import com.mouhin.family.tree.domain.entity.FamilyMember;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyUserNode;
import com.mouhin.family.tree.domain.entity.User;
import com.mouhin.family.tree.domain.event.OperationPerformedEvent;
import com.mouhin.family.tree.domain.repository.CrossFamilyLinkRepository;
import com.mouhin.family.tree.domain.repository.FamilyMemberRepository;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.FamilyUserNodeRepository;
import com.mouhin.family.tree.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 用户-家族-族谱节点绑定应用服务
 *
 * <p>承载"标记为我"的绑定读写，并负责族谱节点与用户账号之间的一致性：
 * 在世节点开通账号（用户名优先取姓名、重名/脱敏兜底 fp_编号）时一并建立绑定并补建家族成员身份、节点改名同步绑定用户昵称、
 * 真人标记节点时回收重复的自动账号、删除节点时区分自动绑定（回收放行）与真人标记（拦截）。
 * 自动账号靠 is_auto_account 标记识别，不再依赖用户名形态。
 * 另承载跨族方案的 ACCOUNT 通道：真人账号多族绑定/解绑时自动同步 source=ACCOUNT 的跨族链接
 * （{@link CrossFamilyLink}），候选链自动核销升级。</p>
 *
 * @author Family-Tree
 * @date 2026-11-05
 */
@Service
public class FamilyUserNodeApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(FamilyUserNodeApplicationService.class);

    /**
     * 脱敏姓名特征字符（隐私保护把姓名部分替换为 * 或 ?），此类姓名不宜作登录用户名
     */
    private static final Pattern MASKED_NAME_PATTERN = Pattern.compile("[*?]");

    private final FamilyUserNodeRepository familyUserNodeRepository;
    private final FamilyNodeRepository familyNodeRepository;
    private final UserRepository userRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final UserAuthApplicationService userAuthApplicationService;
    private final CrossFamilyLinkRepository crossFamilyLinkRepository;
    private final ApplicationEventPublisher eventPublisher;

    public FamilyUserNodeApplicationService(FamilyUserNodeRepository familyUserNodeRepository,
                                            FamilyNodeRepository familyNodeRepository,
                                            UserRepository userRepository,
                                            FamilyMemberRepository familyMemberRepository,
                                            UserAuthApplicationService userAuthApplicationService,
                                            CrossFamilyLinkRepository crossFamilyLinkRepository,
                                            ApplicationEventPublisher eventPublisher) {
        this.familyUserNodeRepository = familyUserNodeRepository;
        this.familyNodeRepository = familyNodeRepository;
        this.userRepository = userRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.userAuthApplicationService = userAuthApplicationService;
        this.crossFamilyLinkRepository = crossFamilyLinkRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 为在世族谱节点开通登录账号并建立节点绑定（同一事务，原子提交）
     *
     * <p>用户名优先取节点姓名（重名/脱敏/超长时兜底 fp_{节点ID}），昵称取节点姓名，初始密码见
     * {@link AccountConsts#NODE_ACCOUNT_INITIAL_PASSWORD}，账号标记为自动账号。
     * 节点已绑定自动账号时直接复用（幂等，避免重复开通）；已被真人标记时不再自动开通，避免覆盖本人归属。</p>
     *
     * @param node 在世族谱节点
     * @return 账号用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long provisionAndBindNodeAccount(FamilyNode node) {
        Long familyId = node.getFamilyId();
        Long nodeId = node.getId();

        // 节点已绑定账号：自动账号直接复用（幂等），真人标记则不再自动开通
        FamilyUserNode existingBinding = familyUserNodeRepository.findByFamilyIdAndNodeId(familyId, nodeId);
        if (existingBinding != null) {
            User boundUser = userRepository.findById(existingBinding.getUserId());
            if (boundUser != null && Boolean.TRUE.equals(boundUser.getAutoAccount())) {
                return boundUser.getId();
            }
            return existingBinding.getUserId();
        }

        String username = resolveNodeAccountUsername(node.getName(), nodeId);
        User existing = userRepository.findByUsername(username);
        Long userId = existing != null ? existing.getId()
                : userAuthApplicationService.provisionAccount(
                        username, node.getName(), AccountConsts.NODE_ACCOUNT_INITIAL_PASSWORD);
        bindProvisionedAccount(familyId, userId, nodeId);
        backfillProfileFromNode(userRepository.findById(userId), node);
        return userId;
    }

    /**
     * 查询用户在指定家族内绑定的族谱节点ID
     *
     * @param familyId 家族ID
     * @param userId   用户ID
     * @return 节点ID，未绑定返回null
     */
    public Long getBoundNodeId(Long familyId, Long userId) {
        if (familyId == null || userId == null) {
            return null;
        }
        FamilyUserNode binding = familyUserNodeRepository.findByFamilyIdAndUserId(familyId, userId);
        return binding != null ? binding.getNodeId() : null;
    }

    /**
     * 查询家族内全部绑定关系（用户ID -> 节点ID）
     *
     * @param familyId 家族ID
     * @return 绑定映射，无绑定时返回空Map
     */
    public Map<Long, Long> listBindingsByFamily(Long familyId) {
        if (familyId == null) {
            return Map.of();
        }
        List<FamilyUserNode> bindings = familyUserNodeRepository.findByFamilyId(familyId);
        Map<Long, Long> bindingMap = new HashMap<>(bindings.size() * 4 / 3 + 1);
        for (FamilyUserNode binding : bindings) {
            bindingMap.put(binding.getUserId(), binding.getNodeId());
        }
        return bindingMap;
    }

    /**
     * 标记/取消标记当前用户在族谱中的位置
     *
     * @param familyId 家族ID
     * @param userId   用户ID
     * @param nodeId   目标节点ID，传null表示解除标记
     */
    @Transactional(rollbackFor = Exception.class)
    public void bindMyNode(Long familyId, Long userId, Long nodeId) {
        if (familyId == null) {
            throw new BusinessException("请先加入家族");
        }
        FamilyUserNode existing = familyUserNodeRepository.findByFamilyIdAndUserId(familyId, userId);

        if (nodeId == null) {
            if (existing != null) {
                familyUserNodeRepository.removeByFamilyIdAndUserId(familyId, userId);
                syncAccountLinksForUser(userId, existing.getNodeId());
                logger.info("User unbound from node: familyId={} userId={}", familyId, userId);
            }
            return;
        }

        FamilyNode node = familyNodeRepository.findById(nodeId);
        if (node == null || !Objects.equals(node.getFamilyId(), familyId)) {
            throw new BusinessException("节点不存在或不属于当前家族");
        }

        // 真人标记节点时，回收该节点重复开通的自动账号（延续 V28 思路），避免一人两号
        reclaimAutoAccountIfPresent(familyId, nodeId, userId);

        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            FamilyUserNode binding = new FamilyUserNode();
            binding.setFamilyId(familyId);
            binding.setUserId(userId);
            binding.setNodeId(nodeId);
            binding.setCreateTime(now);
            binding.setUpdateTime(now);
            binding.validate();
            familyUserNodeRepository.save(binding);
        } else if (!existing.isBoundTo(nodeId)) {
            existing.setNodeId(nodeId);
            existing.setUpdateTime(now);
            familyUserNodeRepository.update(existing);
        }
        backfillProfileFromNode(userRepository.findById(userId), node);
        syncAccountLinksForUser(userId, nodeId);
        logger.info("User bound to node: familyId={} userId={} nodeId={}", familyId, userId, nodeId);
    }

    /**
     * 族谱节点改名后同步绑定用户的昵称（仅同步昵称，其余个人资料不动）
     *
     * @param familyId 家族ID
     * @param nodeId   节点ID
     * @param nodeName 节点最新姓名
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncNicknameFromNode(Long familyId, Long nodeId, String nodeName) {
        if (nodeName == null || nodeName.isBlank()) {
            return;
        }
        FamilyUserNode binding = familyUserNodeRepository.findByFamilyIdAndNodeId(familyId, nodeId);
        if (binding == null) {
            return;
        }
        User user = userRepository.findById(binding.getUserId());
        if (user == null || Objects.equals(user.getNickname(), nodeName)) {
            return;
        }
        if (nodeName.length() > FamilyTreeConsts.MAX_NAME_LENGTH) {
            throw new BusinessException("节点姓名超出昵称长度限制");
        }
        user.setNickname(nodeName);
        user.setUpdateTime(LocalDateTime.now());
        userRepository.update(user);
        logger.info("Synced nickname from renamed node: userId={} nodeId={} nickname={}",
                user.getId(), nodeId, nodeName);
    }

    /**
     * 删除节点前的绑定处置：真人标记则拦截，仅自动账号绑定则回收后放行
     *
     * <p>自动开通的账号绑定不算"族人标记为本人"，删除节点时连同账号一并回收；
     * 只有真人账号的绑定才阻止删除，避免用户账号失去族谱归属。</p>
     *
     * @param familyId 家族ID
     * @param nodeId   节点ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void prepareForNodeDeletion(Long familyId, Long nodeId) {
        FamilyUserNode binding = familyUserNodeRepository.findByFamilyIdAndNodeId(familyId, nodeId);
        if (binding == null) {
            return;
        }
        if (!isAutoAccountForNode(binding, nodeId)) {
            throw new BusinessException("该节点已被族人标记为本人，请先解除标记后再删除");
        }
        familyUserNodeRepository.removeByFamilyIdAndUserId(familyId, binding.getUserId());
        removeMemberIfPresent(familyId, binding.getUserId());
        userRepository.deleteById(binding.getUserId());
        logger.info("Reclaimed auto-provisioned account on node deletion: familyId={} nodeId={} userId={}",
                familyId, nodeId, binding.getUserId());
    }

    /**
     * 清空家族的全部绑定关系（族谱数据整体重建时使用）
     *
     * @param familyId 家族ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void clearFamilyBindings(Long familyId) {
        familyUserNodeRepository.removeByFamilyId(familyId);
        logger.info("Cleared all user-node bindings for familyId={}", familyId);
    }

    /**
     * 为自动开通的账号建立节点绑定，按 (家族,用户) 幂等
     *
     * <p>节点已被其他用户（真人）绑定时不再自动绑定，避免覆盖本人归属。</p>
     */
    private void bindProvisionedAccount(Long familyId, Long userId, Long nodeId) {
        ensureFamilyMember(familyId, userId);
        LocalDateTime now = LocalDateTime.now();
        FamilyUserNode byUser = familyUserNodeRepository.findByFamilyIdAndUserId(familyId, userId);
        if (byUser != null) {
            if (!byUser.isBoundTo(nodeId)) {
                byUser.setNodeId(nodeId);
                byUser.setUpdateTime(now);
                familyUserNodeRepository.update(byUser);
            }
            return;
        }
        if (familyUserNodeRepository.findByFamilyIdAndNodeId(familyId, nodeId) != null) {
            return;
        }
        FamilyUserNode binding = new FamilyUserNode();
        binding.setFamilyId(familyId);
        binding.setUserId(userId);
        binding.setNodeId(nodeId);
        binding.setCreateTime(now);
        binding.setUpdateTime(now);
        binding.validate();
        familyUserNodeRepository.save(binding);
    }

    /**
     * 确保节点绑定用户在所属家族内具备成员身份。
     *
     * <p>家族成员列表、登录后的当前家族解析、以及按角色的详情可见性均以 {@code family_member} 为准；
     * 自动开通的节点账号若只写 {@code family_user_node} 而不建成员行，就会出现"绑定了节点却不属于家族"。
     * 该方法在绑定时幂等补建 MEMBER：已存在成员记录时原样返回，绝不覆盖 OWNER/ADMIN 角色。</p>
     *
     * @param familyId 家族ID
     * @param userId   用户ID
     */
    private void ensureFamilyMember(Long familyId, Long userId) {
        if (familyMemberRepository.findByFamilyIdAndUserId(familyId, userId) != null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        FamilyMember member = new FamilyMember();
        member.setFamilyId(familyId);
        member.setUserId(userId);
        member.setRole(FamilyMemberRoleEnum.MEMBER.getCode());
        member.setJoinedTime(now);
        member.setCreateTime(now);
        member.setUpdateTime(now);
        familyMemberRepository.save(member);
        logger.info("Ensured family membership for provisioned node account: familyId={} userId={}", familyId, userId);
    }

    /**
     * 回收自动账号时同步移除其在本家族的成员记录。
     *
     * <p>自动账号由 {@link #ensureFamilyMember} 补建 MEMBER 行，若只删 {@code account} 而遗留
     * {@code family_member}，成员列表会出现查无此人的幽灵行、成员上限统计也会虚高。</p>
     */
    private void removeMemberIfPresent(Long familyId, Long userId) {
        FamilyMember member = familyMemberRepository.findByFamilyIdAndUserId(familyId, userId);
        if (member != null) {
            familyMemberRepository.removeById(member.getId());
        }
    }

    /**
     * 绑定节点后把节点档案回填到账号资料（性别/出生日期/农历/录入历法），仅在账号字段为空时补，不覆盖已有值
     *
     * <p>自动开通的账号建档时只写了昵称，本人激活或建族初始化绑定后若资料仍为空，
     * 个人信息页会显示一片空白，与其在族谱中的节点信息不一致。</p>
     *
     * <p>世代（辈分）不回填：辈分正式存储在节点层 {@code family_node.generation} 按家族维护，
     * 账号级副本会被后绑家族覆写造成串辈（罗梦桃案例），V39 已删除 {@code account.generation} 列。</p>
     */
    private void backfillProfileFromNode(User user, FamilyNode node) {
        if (user == null || node == null) {
            return;
        }
        boolean changed = false;
        if (user.getGender() == null && node.getGender() != null) {
            user.setGender(node.getGender());
            changed = true;
        }
        if (user.getBirthDate() == null && node.getBirthDate() != null) {
            user.setBirthDate(node.getBirthDate().toString());
            changed = true;
        }
        if (user.getLunarBirthDate() == null && node.getLunarBirthDate() != null) {
            user.setLunarBirthDate(node.getLunarBirthDate());
            changed = true;
        }
        if (user.getBirthCalendar() == null && node.getBirthCalendar() != null) {
            user.setBirthCalendar(node.getBirthCalendar());
            changed = true;
        }
        if (!changed) {
            return;
        }
        user.setUpdateTime(LocalDateTime.now());
        userRepository.update(user);
        logger.info("Backfilled user profile from bound node: userId={} nodeId={}", user.getId(), node.getId());
    }

    /**
     * 回收节点上重复开通的自动账号（解除绑定并删除账号）
     *
     * @param excludeUserId 标记发起人ID，其自身即自动账号时不回收（保护"自动账号本人绑定"情形）
     */
    private void reclaimAutoAccountIfPresent(Long familyId, Long nodeId, Long excludeUserId) {
        FamilyUserNode binding = familyUserNodeRepository.findByFamilyIdAndNodeId(familyId, nodeId);
        if (binding == null || Objects.equals(binding.getUserId(), excludeUserId)) {
            return;
        }
        if (!isAutoAccountForNode(binding, nodeId)) {
            return;
        }
        familyUserNodeRepository.removeByFamilyIdAndUserId(familyId, binding.getUserId());
        removeMemberIfPresent(familyId, binding.getUserId());
        userRepository.deleteById(binding.getUserId());
        logger.info("Reclaimed auto-provisioned account on real-user binding: familyId={} nodeId={} userId={}",
                familyId, nodeId, binding.getUserId());
    }

    /**
     * 判断绑定用户是否为该节点的自动开通账号（依据 is_auto_account 标记，不再依赖用户名形态）
     *
     * <p>自动账号用户名可能是姓名或 fp_{节点ID} 兜底，故以标记区分；真人注册账号标记为 false，
     * 避免误删真人账号。</p>
     */
    private boolean isAutoAccountForNode(FamilyUserNode binding, Long nodeId) {
        User boundUser = userRepository.findById(binding.getUserId());
        return boundUser != null && Boolean.TRUE.equals(boundUser.getAutoAccount())
                && Objects.equals(binding.getNodeId(), nodeId);
    }

    /**
     * ACCOUNT 通道自动成链同步：按真人账号当前全部跨族绑定重算 source=ACCOUNT 的跨族链接（收敛为期望态）
     *
     * <p>同一真人账号在两族分别「标记为我」即自动建链（血缘同人），解绑即自动释放；
     * fp_/autoAccount 自动账号不成链（跨族对不上人，见方案册 §4.1 口径）。
     * 候选确认（SUGGESTED）链接在本人绑定后自动核销升级为 ACCOUNT；
     * 人工指认（MANUAL）链接不受绑定变化影响。仅由 {@link #bindMyNode} 绑定/解绑两分支调用。</p>
     *
     * @param userId        绑定用户ID（真人账号才同步）
     * @param touchedNodeId 本次绑定/解绑涉及的节点ID（用于发现需释放的旧链）
     */
    private void syncAccountLinksForUser(Long userId, Long touchedNodeId) {
        User user = userRepository.findById(userId);
        if (user == null || Boolean.TRUE.equals(user.getAutoAccount())) {
            return;
        }
        List<FamilyUserNode> bindings = new ArrayList<>();
        for (FamilyUserNode binding : familyUserNodeRepository.findByUserId(userId)) {
            if (binding.getNodeId() != null && binding.getFamilyId() != null) {
                bindings.add(binding);
            }
        }

        Set<String> expectedPairs = new HashSet<>(bindings.size() * bindings.size() + 1);
        for (int i = 0; i < bindings.size(); i++) {
            for (int j = i + 1; j < bindings.size(); j++) {
                FamilyUserNode a = bindings.get(i);
                FamilyUserNode b = bindings.get(j);
                if (!Objects.equals(a.getFamilyId(), b.getFamilyId())) {
                    expectedPairs.add(pairKey(a.getNodeId(), b.getNodeId()));
                }
            }
        }

        releaseStaleAccountLinks(userId, bindings, touchedNodeId, expectedPairs);
        createMissingAccountLinks(user, bindings, expectedPairs);
    }

    /**
     * 释放不再成立的账号自动链：仅回收 source=ACCOUNT 且两端已不构成跨族绑定对的链接
     */
    private void releaseStaleAccountLinks(Long userId, List<FamilyUserNode> bindings,
                                          Long touchedNodeId, Set<String> expectedPairs) {
        Set<Long> nodeIds = new HashSet<>(bindings.size() * 4 / 3 + 2);
        for (FamilyUserNode binding : bindings) {
            nodeIds.add(binding.getNodeId());
        }
        if (touchedNodeId != null) {
            nodeIds.add(touchedNodeId);
        }
        Set<Long> releasedLinkIds = new HashSet<>(nodeIds.size() + 1);
        for (Long nodeId : nodeIds) {
            for (CrossFamilyLink link : crossFamilyLinkRepository.findByNodeId(nodeId)) {
                if (link.getId() == null || !releasedLinkIds.add(link.getId())) {
                    continue;
                }
                if (link.isAccountSourced()
                        && !expectedPairs.contains(pairKey(link.getSrcNodeId(), link.getDstNodeId()))) {
                    crossFamilyLinkRepository.removeById(link.getId());
                    eventPublisher.publishEvent(OperationPerformedEvent.of(userId, null,
                            OperationLogConsts.OP_NODE_UNLINK, "解除账号自动亲族链接",
                            OperationLogConsts.TARGET_NODE_LINK, link.getId(),
                            link.getSrcFamilyId(), null));
                    logger.info("Released account-derived cross-family link: linkId={} userId={} {}<->{}",
                            link.getId(), userId, link.getSrcNodeId(), link.getDstNodeId());
                }
            }
        }
    }

    /**
     * 补齐缺失的账号自动链并把候选确认链核销升级为账号链；人工指认链原样保留
     */
    private void createMissingAccountLinks(User user, List<FamilyUserNode> bindings,
                                           Set<String> expectedPairs) {
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < bindings.size(); i++) {
            for (int j = i + 1; j < bindings.size(); j++) {
                FamilyUserNode a = bindings.get(i);
                FamilyUserNode b = bindings.get(j);
                if (!Objects.equals(a.getFamilyId(), b.getFamilyId())) {
                    upsertAccountLink(user, a, b, now);
                }
            }
        }
    }

    /**
     * 单对绑定的成链落库：无链新建；候选链核销升级为账号链；账号链/人工链已存在则不动
     */
    private void upsertAccountLink(User user, FamilyUserNode a, FamilyUserNode b, LocalDateTime now) {
        CrossFamilyLink existing = crossFamilyLinkRepository.findByNodePair(a.getNodeId(), b.getNodeId());
        if (existing != null) {
            if (Objects.equals(existing.getSource(), NodeLinkSourceEnum.SUGGESTED.getCode())) {
                existing.setSource(NodeLinkSourceEnum.ACCOUNT.getCode());
                existing.setConfirmedBy(null);
                existing.setUpdateTime(now);
                crossFamilyLinkRepository.update(existing);
                logger.info("Upgraded suggested link to account link: linkId={} userId={}",
                        existing.getId(), user.getId());
            }
            return;
        }
        CrossFamilyLink link = new CrossFamilyLink();
        link.setSrcFamilyId(a.getFamilyId());
        link.setSrcNodeId(a.getNodeId());
        link.setDstFamilyId(b.getFamilyId());
        link.setDstNodeId(b.getNodeId());
        link.setLinkType(NodeLinkTypeEnum.BLOOD.getCode());
        link.setSource(NodeLinkSourceEnum.ACCOUNT.getCode());
        link.setCreateTime(now);
        link.setUpdateTime(now);
        link.validate();
        link.orderEndpoints();
        crossFamilyLinkRepository.save(link);
        eventPublisher.publishEvent(OperationPerformedEvent.of(user.getId(), user.getUsername(),
                OperationLogConsts.OP_NODE_LINK, "账号多族绑定自动建立亲族链接",
                OperationLogConsts.TARGET_NODE_LINK, link.getId(), link.getSrcFamilyId(), null));
        logger.info("Created account-derived cross-family link: linkId={} userId={} {}<->{}",
                link.getId(), user.getId(), link.getSrcNodeId(), link.getDstNodeId());
    }

    /**
     * 节点对的规范化键（小ID在前），与 {@link CrossFamilyLink#orderEndpoints()} 同口径
     */
    private static String pairKey(Long nodeA, Long nodeB) {
        if (nodeA == null || nodeB == null) {
            return "";
        }
        return nodeA <= nodeB ? nodeA + "-" + nodeB : nodeB + "-" + nodeA;
    }

    /**
     * 计算自动账号用户名：姓名干净（非空、未脱敏、不超长）且未被占用时取姓名，否则兜底 fp_{节点ID}
     *
     * @param nodeName 节点姓名
     * @param nodeId   节点ID
     * @return 登录用户名
     */
    private String resolveNodeAccountUsername(String nodeName, Long nodeId) {
        String fallbackUsername = AccountConsts.NODE_ACCOUNT_USERNAME_PREFIX + nodeId;
        if (nodeName == null) {
            return fallbackUsername;
        }
        String name = nodeName.trim();
        if (name.isEmpty() || name.length() > FamilyTreeConsts.MAX_NAME_LENGTH
                || MASKED_NAME_PATTERN.matcher(name).find()) {
            return fallbackUsername;
        }
        if (userRepository.findByUsername(name) != null) {
            return fallbackUsername;
        }
        return name;
    }
}
