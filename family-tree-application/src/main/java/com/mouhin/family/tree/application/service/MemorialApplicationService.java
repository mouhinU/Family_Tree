package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.MemorialMessageDTO;
import com.mouhin.family.tree.common.dto.MemorialMessageVO;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.MemorialMessage;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.MemorialMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 祭堂缅怀留言应用服务
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Service
public class MemorialApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(MemorialApplicationService.class);

    private final MemorialMessageRepository memorialMessageRepository;
    private final FamilyNodeRepository familyNodeRepository;

    public MemorialApplicationService(MemorialMessageRepository memorialMessageRepository,
                                      FamilyNodeRepository familyNodeRepository) {
        this.memorialMessageRepository = memorialMessageRepository;
        this.familyNodeRepository = familyNodeRepository;
    }

    /**
     * 查询节点的缅怀留言列表
     *
     * @param familyId      家族ID
     * @param nodeId        已故节点ID
     * @param currentUserId 当前用户ID
     * @return 留言列表（时间倒序）
     */
    public List<MemorialMessageVO> listMessages(Long familyId, Long nodeId, Long currentUserId) {
        checkNode(familyId, nodeId);
        List<MemorialMessage> messages = memorialMessageRepository.findByNodeId(nodeId);
        return messages.stream()
                .map(message -> toVO(message, currentUserId))
                .collect(Collectors.toList());
    }

    /**
     * 发布缅怀留言
     *
     * @param familyId 家族ID
     * @param nodeId   已故节点ID
     * @param userId   留言用户ID
     * @param username 留言用户名
     * @param dto      留言内容
     * @return 留言展示对象
     */
    @Transactional(rollbackFor = Exception.class)
    public MemorialMessageVO postMessage(Long familyId, Long nodeId, Long userId, String username,
                                         MemorialMessageDTO dto) {
        FamilyNode node = checkNode(familyId, nodeId);
        if (!node.isDeceased()) {
            throw new BusinessException("只能为已故亲人留言缅怀");
        }

        MemorialMessage message = new MemorialMessage();
        message.setFamilyId(familyId);
        message.setNodeId(nodeId);
        message.setUserId(userId);
        message.setUsername(username);
        message.setContent(dto.getContent() != null ? dto.getContent().trim() : null);
        message.setCreateTime(LocalDateTime.now());
        message.validateContent();
        memorialMessageRepository.save(message);
        logger.info("用户 {} 为节点 {} 发布缅怀留言: id={}", userId, nodeId, message.getId());
        return toVO(message, userId);
    }

    /**
     * 删除缅怀留言（仅留言人可删除）
     *
     * @param familyId 家族ID
     * @param id       留言ID
     * @param userId   当前用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteMessage(Long familyId, Long id, Long userId) {
        MemorialMessage message = memorialMessageRepository.findById(id);
        if (message == null || !Objects.equals(message.getFamilyId(), familyId)) {
            throw new BusinessException("留言不存在");
        }
        if (!message.isAuthor(userId)) {
            throw new BusinessException("只能删除自己的留言");
        }
        memorialMessageRepository.removeById(id);
        logger.info("用户 {} 删除缅怀留言: id={}", userId, id);
    }

    /**
     * 校验节点存在且属于当前家族
     */
    private FamilyNode checkNode(Long familyId, Long nodeId) {
        FamilyNode node = familyNodeRepository.findById(nodeId);
        if (node == null || !Objects.equals(node.getFamilyId(), familyId)) {
            throw new BusinessException("家族成员不存在");
        }
        return node;
    }

    private MemorialMessageVO toVO(MemorialMessage message, Long currentUserId) {
        MemorialMessageVO vo = new MemorialMessageVO();
        vo.setId(message.getId());
        vo.setNodeId(message.getNodeId());
        vo.setUserId(message.getUserId());
        vo.setUsername(message.getUsername());
        vo.setContent(message.getContent());
        vo.setCreateTime(message.getCreateTime());
        vo.setOwn(Objects.equals(message.getUserId(), currentUserId));
        return vo;
    }
}
