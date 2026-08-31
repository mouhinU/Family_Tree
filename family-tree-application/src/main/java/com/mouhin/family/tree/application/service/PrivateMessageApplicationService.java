package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.ConversationVO;
import com.mouhin.family.tree.common.dto.PrivateMessageDTO;
import com.mouhin.family.tree.common.dto.PrivateMessageVO;
import com.mouhin.family.tree.common.dto.UserContactVO;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.PrivateMessage;
import com.mouhin.family.tree.domain.entity.User;
import com.mouhin.family.tree.domain.repository.PrivateMessageRepository;
import com.mouhin.family.tree.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 私信消息应用服务
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Service
public class PrivateMessageApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(PrivateMessageApplicationService.class);

    /**
     * 会话聚合时拉取的最近消息条数上限
     */
    private static final int RECENT_MESSAGE_LIMIT = 200;

    private final PrivateMessageRepository privateMessageRepository;
    private final UserRepository userRepository;

    public PrivateMessageApplicationService(PrivateMessageRepository privateMessageRepository,
                                            UserRepository userRepository) {
        this.privateMessageRepository = privateMessageRepository;
        this.userRepository = userRepository;
    }

    /**
     * 发送私信
     *
     * @param familyId 家族ID
     * @param senderId 发送者用户ID
     * @param dto      私信内容（接收者ID + 内容）
     * @return 私信展示对象
     */
    @Transactional(rollbackFor = Exception.class)
    public PrivateMessageVO sendMessage(Long familyId, Long senderId, PrivateMessageDTO dto) {
        if (dto.getReceiverId() == null) {
            throw new BusinessException("请选择收信人");
        }
        User sender = userRepository.findById(senderId);
        if (sender == null) {
            throw new BusinessException("用户不存在");
        }
        User receiver = userRepository.findById(dto.getReceiverId());
        if (receiver == null || !Objects.equals(receiver.getCurrentFamilyId(), familyId)) {
            throw new BusinessException("收信人不存在或不在同一家族");
        }

        PrivateMessage message = new PrivateMessage();
        message.setFamilyId(familyId);
        message.setSenderId(senderId);
        message.setSenderName(sender.getUsername());
        message.setReceiverId(dto.getReceiverId());
        message.setContent(dto.getContent() != null ? dto.getContent().trim() : null);
        message.setRead(Boolean.FALSE);
        message.setCreateTime(LocalDateTime.now());
        message.validateContent();
        privateMessageRepository.save(message);
        logger.info("用户 {} 向用户 {} 发送私信: id={}", senderId, dto.getReceiverId(), message.getId());
        return toVO(message, senderId);
    }

    /**
     * 查询会话列表（按最近消息时间倒序）
     *
     * @param userId 当前用户ID
     * @return 会话列表
     */
    public List<ConversationVO> listConversations(Long userId) {
        List<PrivateMessage> recentMessages =
                privateMessageRepository.findRecentByUser(userId, RECENT_MESSAGE_LIMIT);
        // 按对方用户聚合，LinkedHashMap 保持最近时间倒序
        Map<Long, ConversationVO> conversationMap = new LinkedHashMap<>();
        for (PrivateMessage message : recentMessages) {
            boolean received = Objects.equals(message.getReceiverId(), userId);
            Long peerId = received ? message.getSenderId() : message.getReceiverId();
            ConversationVO vo = conversationMap.get(peerId);
            if (vo == null) {
                vo = new ConversationVO();
                vo.setPeerUserId(peerId);
                vo.setPeerName(received ? message.getSenderName() : resolvePeerName(peerId));
                vo.setLastContent(message.getContent());
                vo.setLastTime(message.getCreateTime());
                vo.setUnreadCount(0L);
                conversationMap.put(peerId, vo);
            }
            if (received && !Boolean.TRUE.equals(message.getRead())) {
                vo.setUnreadCount(vo.getUnreadCount() + 1);
            }
        }
        return new ArrayList<>(conversationMap.values());
    }

    /**
     * 查询与某用户的会话消息（同时标记对方消息为已读）
     *
     * @param userId 当前用户ID
     * @param peerId 对方用户ID
     * @return 消息列表（时间正序）
     */
    @Transactional(rollbackFor = Exception.class)
    public List<PrivateMessageVO> getConversation(Long userId, Long peerId) {
        privateMessageRepository.markRead(userId, peerId);
        List<PrivateMessage> messages = privateMessageRepository.findConversation(userId, peerId);
        return messages.stream()
                .map(message -> toVO(message, userId))
                .collect(Collectors.toList());
    }

    /**
     * 查询未读私信总数
     *
     * @param userId 当前用户ID
     * @return 未读数量
     */
    public long countUnread(Long userId) {
        return privateMessageRepository.countUnread(userId);
    }

    /**
     * 查询当前家族的私信联系人（排除自己）
     *
     * @param familyId      家族ID
     * @param currentUserId 当前用户ID
     * @return 联系人列表
     */
    public List<UserContactVO> listContacts(Long familyId, Long currentUserId) {
        List<User> users = userRepository.findByCurrentFamilyId(familyId);
        return users.stream()
                .filter(user -> !Objects.equals(user.getId(), currentUserId))
                .map(this::toContactVO)
                .collect(Collectors.toList());
    }

    private PrivateMessageVO toVO(PrivateMessage message, Long currentUserId) {
        PrivateMessageVO vo = new PrivateMessageVO();
        vo.setId(message.getId());
        vo.setSenderId(message.getSenderId());
        vo.setSenderName(message.getSenderName());
        vo.setReceiverId(message.getReceiverId());
        vo.setContent(message.getContent());
        vo.setCreateTime(message.getCreateTime());
        vo.setOwn(Objects.equals(message.getSenderId(), currentUserId));
        return vo;
    }

    /**
     * 解析对方用户名（发送方向的历史消息无对方名称快照）
     */
    private String resolvePeerName(Long peerId) {
        User peer = userRepository.findById(peerId);
        return peer != null ? peer.getUsername() : "未知用户";
    }

    private UserContactVO toContactVO(User user) {
        UserContactVO vo = new UserContactVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNodeId(user.getNodeId());
        return vo;
    }
}
