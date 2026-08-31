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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 私信消息应用服务单元测试。
 * 覆盖：发送私信（正常/未选收信人/发送者不存在/收信人不存在/收信人跨家族/内容为空）、
 * 会话列表（聚合/未读计数/对方名称解析）、会话详情（标记已读）、未读总数、联系人列表。
 *
 * @author Family-Tree
 * @date 2026-08-31
 */
@ExtendWith(MockitoExtension.class)
class PrivateMessageApplicationServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long PEER_ID = 2L;
    private static final Long FAMILY_ID = 100L;

    @Mock
    private PrivateMessageRepository privateMessageRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PrivateMessageApplicationService privateMessageApplicationService;

    // ========== 发送私信 ==========

    @Test
    void sendMessage_success() {
        when(userRepository.findById(USER_ID)).thenReturn(buildUser(USER_ID, "发送者"));
        when(userRepository.findById(PEER_ID)).thenReturn(buildUser(PEER_ID, "接收者"));

        PrivateMessageDTO dto = new PrivateMessageDTO();
        dto.setReceiverId(PEER_ID);
        dto.setContent("  你好  ");

        PrivateMessageVO vo = privateMessageApplicationService.sendMessage(FAMILY_ID, USER_ID, dto);

        ArgumentCaptor<PrivateMessage> captor = ArgumentCaptor.forClass(PrivateMessage.class);
        verify(privateMessageRepository).save(captor.capture());
        PrivateMessage saved = captor.getValue();
        assertEquals(FAMILY_ID, saved.getFamilyId());
        assertEquals(USER_ID, saved.getSenderId());
        assertEquals("发送者", saved.getSenderName());
        assertEquals(PEER_ID, saved.getReceiverId());
        assertEquals("你好", saved.getContent());
        assertFalse(saved.getRead());
        assertTrue(vo.getOwn());
    }

    @Test
    void sendMessage_nullReceiver_throws() {
        PrivateMessageDTO dto = new PrivateMessageDTO();
        dto.setContent("内容");

        assertThrows(BusinessException.class, () ->
                privateMessageApplicationService.sendMessage(FAMILY_ID, USER_ID, dto));
        verify(privateMessageRepository, never()).save(any(PrivateMessage.class));
    }

    @Test
    void sendMessage_senderNotFound_throws() {
        when(userRepository.findById(USER_ID)).thenReturn(null);

        PrivateMessageDTO dto = new PrivateMessageDTO();
        dto.setReceiverId(PEER_ID);
        dto.setContent("内容");

        assertThrows(BusinessException.class, () ->
                privateMessageApplicationService.sendMessage(FAMILY_ID, USER_ID, dto));
    }

    @Test
    void sendMessage_receiverNotFound_throws() {
        when(userRepository.findById(USER_ID)).thenReturn(buildUser(USER_ID, "发送者"));
        when(userRepository.findById(PEER_ID)).thenReturn(null);

        PrivateMessageDTO dto = new PrivateMessageDTO();
        dto.setReceiverId(PEER_ID);
        dto.setContent("内容");

        assertThrows(BusinessException.class, () ->
                privateMessageApplicationService.sendMessage(FAMILY_ID, USER_ID, dto));
    }

    @Test
    void sendMessage_receiverOtherFamily_throws() {
        when(userRepository.findById(USER_ID)).thenReturn(buildUser(USER_ID, "发送者"));
        User receiver = buildUser(PEER_ID, "接收者");
        receiver.setCurrentFamilyId(999L);
        when(userRepository.findById(PEER_ID)).thenReturn(receiver);

        PrivateMessageDTO dto = new PrivateMessageDTO();
        dto.setReceiverId(PEER_ID);
        dto.setContent("内容");

        assertThrows(BusinessException.class, () ->
                privateMessageApplicationService.sendMessage(FAMILY_ID, USER_ID, dto));
    }

    @Test
    void sendMessage_blankContent_throws() {
        when(userRepository.findById(USER_ID)).thenReturn(buildUser(USER_ID, "发送者"));
        when(userRepository.findById(PEER_ID)).thenReturn(buildUser(PEER_ID, "接收者"));

        PrivateMessageDTO dto = new PrivateMessageDTO();
        dto.setReceiverId(PEER_ID);
        dto.setContent("   ");

        assertThrows(BusinessException.class, () ->
                privateMessageApplicationService.sendMessage(FAMILY_ID, USER_ID, dto));
        verify(privateMessageRepository, never()).save(any(PrivateMessage.class));
    }

    // ========== 会话列表 ==========

    @Test
    void listConversations_aggregatesByPeer() {
        // 最近消息（倒序）：两条来自用户2（一条未读），一条发给用户3
        PrivateMessage fromPeerUnread = buildMessage(1L, PEER_ID, USER_ID, "最新消息", false);
        PrivateMessage fromPeerRead = buildMessage(2L, PEER_ID, USER_ID, "较早消息", true);
        PrivateMessage toPeer3 = buildMessage(3L, USER_ID, 3L, "发出的消息", false);
        when(privateMessageRepository.findRecentByUser(USER_ID, 200))
                .thenReturn(List.of(fromPeerUnread, fromPeerRead, toPeer3));
        when(userRepository.findById(3L)).thenReturn(buildUser(3L, "用户3"));

        List<ConversationVO> conversations = privateMessageApplicationService.listConversations(USER_ID);

        assertEquals(2, conversations.size());

        ConversationVO withPeer2 = conversations.get(0);
        assertEquals(PEER_ID, withPeer2.getPeerUserId());
        assertEquals("接收者", withPeer2.getPeerName());
        assertEquals("最新消息", withPeer2.getLastContent());
        assertEquals(1L, withPeer2.getUnreadCount());

        ConversationVO withPeer3 = conversations.get(1);
        assertEquals(3L, withPeer3.getPeerUserId());
        assertEquals("用户3", withPeer3.getPeerName());
        assertEquals(0L, withPeer3.getUnreadCount());
    }

    @Test
    void listConversations_empty() {
        when(privateMessageRepository.findRecentByUser(USER_ID, 200)).thenReturn(List.of());

        assertTrue(privateMessageApplicationService.listConversations(USER_ID).isEmpty());
    }

    // ========== 会话详情 ==========

    @Test
    void getConversation_marksReadAndReturnsMessages() {
        PrivateMessage received = buildMessage(1L, PEER_ID, USER_ID, "对方消息", false);
        PrivateMessage sent = buildMessage(2L, USER_ID, PEER_ID, "我的消息", true);
        when(privateMessageRepository.findConversation(USER_ID, PEER_ID))
                .thenReturn(List.of(received, sent));

        List<PrivateMessageVO> messages =
                privateMessageApplicationService.getConversation(USER_ID, PEER_ID);

        verify(privateMessageRepository).markRead(USER_ID, PEER_ID);
        assertEquals(2, messages.size());
        assertFalse(messages.get(0).getOwn());
        assertTrue(messages.get(1).getOwn());
    }

    // ========== 未读总数 ==========

    @Test
    void countUnread_success() {
        when(privateMessageRepository.countUnread(USER_ID)).thenReturn(3L);

        assertEquals(3L, privateMessageApplicationService.countUnread(USER_ID));
    }

    // ========== 联系人列表 ==========

    @Test
    void listContacts_excludesSelf() {
        when(userRepository.findByCurrentFamilyId(FAMILY_ID))
                .thenReturn(List.of(buildUser(USER_ID, "自己"), buildUser(PEER_ID, "族人")));

        List<UserContactVO> contacts = privateMessageApplicationService.listContacts(FAMILY_ID, USER_ID);

        assertEquals(1, contacts.size());
        assertEquals(PEER_ID, contacts.get(0).getId());
        assertEquals("族人", contacts.get(0).getUsername());
    }

    // ========== 辅助方法 ==========

    private User buildUser(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setCurrentFamilyId(FAMILY_ID);
        return user;
    }

    private PrivateMessage buildMessage(Long id, Long senderId, Long receiverId,
                                        String content, Boolean read) {
        PrivateMessage message = new PrivateMessage();
        message.setId(id);
        message.setFamilyId(FAMILY_ID);
        message.setSenderId(senderId);
        message.setSenderName(senderId.equals(PEER_ID) ? "接收者" : "用户" + senderId);
        message.setReceiverId(receiverId);
        message.setContent(content);
        message.setRead(read);
        message.setCreateTime(LocalDateTime.now());
        return message;
    }
}
