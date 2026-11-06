package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyUserNode;
import com.mouhin.family.tree.domain.entity.User;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.FamilyUserNodeRepository;
import com.mouhin.family.tree.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 用户-家族-族谱节点绑定应用服务
 *
 * <p>承载"标记为我"的绑定读写，并负责族谱节点与用户账号之间的一致性：
 * 节点改名同步绑定用户昵称、被绑定节点禁止删除。</p>
 *
 * @author Family-Tree
 * @date 2026-11-05
 */
@Service
public class FamilyUserNodeApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(FamilyUserNodeApplicationService.class);

    private final FamilyUserNodeRepository familyUserNodeRepository;
    private final FamilyNodeRepository familyNodeRepository;
    private final UserRepository userRepository;

    public FamilyUserNodeApplicationService(FamilyUserNodeRepository familyUserNodeRepository,
                                            FamilyNodeRepository familyNodeRepository,
                                            UserRepository userRepository) {
        this.familyUserNodeRepository = familyUserNodeRepository;
        this.familyNodeRepository = familyNodeRepository;
        this.userRepository = userRepository;
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
                logger.info("User unbound from node: familyId={} userId={}", familyId, userId);
            }
            return;
        }

        FamilyNode node = familyNodeRepository.findById(nodeId);
        if (node == null || !Objects.equals(node.getFamilyId(), familyId)) {
            throw new BusinessException("节点不存在或不属于当前家族");
        }

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
     * 校验节点未被任何用户绑定，被绑定时阻止删除
     *
     * @param familyId 家族ID
     * @param nodeId   节点ID
     */
    public void assertNotBound(Long familyId, Long nodeId) {
        FamilyUserNode binding = familyUserNodeRepository.findByFamilyIdAndNodeId(familyId, nodeId);
        if (binding != null) {
            throw new BusinessException("该节点已被族人标记为本人，请先解除标记后再删除");
        }
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
}
