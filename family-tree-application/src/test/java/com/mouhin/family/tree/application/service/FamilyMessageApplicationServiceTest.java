package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.MessageCreateDTO;
import com.mouhin.family.tree.common.dto.MessageVO;
import com.mouhin.family.tree.common.dto.PageResult;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilyMessage;
import com.mouhin.family.tree.domain.repository.FamilyMessageRepository;
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
 * 族谱留言应用服务单元测试。
 * 覆盖：发布留言（正常/空内容/超长/null用户名/回复）、分页查询（空/有数据/own标记/含回复）、
 * 删除（正常/非本人/不存在/级联删除回复/删除回复减计数）、回复列表查询。
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
@ExtendWith(MockitoExtension.class)
class FamilyMessageApplicationServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long FAMILY_ID = 100L;

    @Mock
    private FamilyMessageRepository familyMessageRepository;

    @InjectMocks
    private FamilyMessageApplicationService messageApplicationService;

    // ========== 发布留言 ==========

    @Test
    void postMessage_success() {
        MessageCreateDTO dto = new MessageCreateDTO();
        dto.setContent("  你好，世界  ");

        doAnswer(invocation -> {
            FamilyMessage msg = invocation.getArgument(0);
            msg.setId(10L);
            return msg;
        }).when(familyMessageRepository).save(any(FamilyMessage.class));

        messageApplicationService.postMessage(FAMILY_ID, USER_ID, "测试用户", dto);

        ArgumentCaptor<FamilyMessage> captor = ArgumentCaptor.forClass(FamilyMessage.class);
        verify(familyMessageRepository).save(captor.capture());
        FamilyMessage saved = captor.getValue();
        assertEquals(FAMILY_ID, saved.getFamilyId());
        assertEquals(USER_ID, saved.getUserId());
        assertEquals("测试用户", saved.getUsername());
        assertEquals("你好，世界", saved.getContent());
        assertNull(saved.getParentId());
        assertEquals(0L, saved.getReplyCount());
    }

    @Test
    void postMessage_nullUsername_fallback() {
        MessageCreateDTO dto = new MessageCreateDTO();
        dto.setContent("测试内容");

        doAnswer(invocation -> {
            FamilyMessage msg = invocation.getArgument(0);
            msg.setId(11L);
            return msg;
        }).when(familyMessageRepository).save(any(FamilyMessage.class));

        messageApplicationService.postMessage(FAMILY_ID, USER_ID, null, dto);

        ArgumentCaptor<FamilyMessage> captor = ArgumentCaptor.forClass(FamilyMessage.class);
        verify(familyMessageRepository).save(captor.capture());
        assertEquals("匿名", captor.getValue().getUsername());
    }

    @Test
    void postMessage_emptyContent_throws() {
        MessageCreateDTO dto = new MessageCreateDTO();
        dto.setContent("   ");

        assertThrows(BusinessException.class, () ->
                messageApplicationService.postMessage(FAMILY_ID, USER_ID, "测试用户", dto));
        verify(familyMessageRepository, never()).save(any(FamilyMessage.class));
    }

    @Test
    void postMessage_contentTooLong_throws() {
        MessageCreateDTO dto = new MessageCreateDTO();
        dto.setContent("a".repeat(501));

        assertThrows(BusinessException.class, () ->
                messageApplicationService.postMessage(FAMILY_ID, USER_ID, "测试用户", dto));
        verify(familyMessageRepository, never()).save(any(FamilyMessage.class));
    }

    // ========== 发布回复 ==========

    @Test
    void postMessage_reply_success() {
        MessageCreateDTO dto = new MessageCreateDTO();
        dto.setContent("这是回复");
        dto.setParentId(10L);

        FamilyMessage parent = buildMessage(10L, OTHER_USER_ID, "用户A", "原始留言");
        when(familyMessageRepository.findById(10L)).thenReturn(parent);

        doAnswer(invocation -> {
            FamilyMessage msg = invocation.getArgument(0);
            msg.setId(20L);
            return msg;
        }).when(familyMessageRepository).save(any(FamilyMessage.class));

        messageApplicationService.postMessage(FAMILY_ID, USER_ID, "测试用户", dto);

        ArgumentCaptor<FamilyMessage> captor = ArgumentCaptor.forClass(FamilyMessage.class);
        verify(familyMessageRepository).save(captor.capture());
        FamilyMessage saved = captor.getValue();
        assertEquals(10L, saved.getParentId());
        assertEquals(FAMILY_ID, saved.getFamilyId());
        verify(familyMessageRepository).incrementReplyCount(10L);
    }

    @Test
    void postMessage_replyParentNotFound_throws() {
        MessageCreateDTO dto = new MessageCreateDTO();
        dto.setContent("这是回复");
        dto.setParentId(999L);

        when(familyMessageRepository.findById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () ->
                messageApplicationService.postMessage(FAMILY_ID, USER_ID, "测试用户", dto));
        verify(familyMessageRepository, never()).save(any(FamilyMessage.class));
    }

    @Test
    void postMessage_replyToReply_throws() {
        MessageCreateDTO dto = new MessageCreateDTO();
        dto.setContent("嵌套回复");
        dto.setParentId(20L);

        FamilyMessage reply = buildMessage(20L, OTHER_USER_ID, "用户B", "一条回复");
        reply.setParentId(10L);
        when(familyMessageRepository.findById(20L)).thenReturn(reply);

        assertThrows(BusinessException.class, () ->
                messageApplicationService.postMessage(FAMILY_ID, USER_ID, "测试用户", dto));
        verify(familyMessageRepository, never()).save(any(FamilyMessage.class));
    }

    // ========== 分页查询 ==========

    @Test
    void listMessages_empty() {
        when(familyMessageRepository.countByFamilyId(FAMILY_ID, null)).thenReturn(0L);

        PageResult<MessageVO> result = messageApplicationService.listMessages(
                FAMILY_ID, USER_ID, null, 1, 20);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    @Test
    void listMessages_withData_ownFlag() {
        when(familyMessageRepository.countByFamilyId(FAMILY_ID, null)).thenReturn(2L);

        FamilyMessage msg1 = buildMessage(1L, USER_ID, "用户A", "我的留言");
        FamilyMessage msg2 = buildMessage(2L, OTHER_USER_ID, "用户B", "别人的留言");
        when(familyMessageRepository.findByFamilyId(FAMILY_ID, null, 0, 20))
                .thenReturn(List.of(msg1, msg2));

        PageResult<MessageVO> result = messageApplicationService.listMessages(
                FAMILY_ID, USER_ID, null, 1, 20);

        assertEquals(2L, result.getTotal());
        assertEquals(2, result.getRecords().size());
        assertTrue(result.getRecords().get(0).getOwn());
        assertFalse(result.getRecords().get(1).getOwn());
    }

    @Test
    void listMessages_withReplies() {
        when(familyMessageRepository.countByFamilyId(FAMILY_ID, null)).thenReturn(1L);

        FamilyMessage msg = buildMessage(1L, USER_ID, "用户A", "有回复的留言");
        msg.setReplyCount(1L);
        when(familyMessageRepository.findByFamilyId(FAMILY_ID, null, 0, 20))
                .thenReturn(List.of(msg));

        FamilyMessage reply = buildMessage(2L, OTHER_USER_ID, "用户B", "回复内容");
        reply.setParentId(1L);
        when(familyMessageRepository.findByParentId(1L)).thenReturn(List.of(reply));

        PageResult<MessageVO> result = messageApplicationService.listMessages(
                FAMILY_ID, USER_ID, null, 1, 20);

        assertEquals(1, result.getRecords().size());
        MessageVO vo = result.getRecords().get(0);
        assertEquals(1L, vo.getReplyCount());
        assertNotNull(vo.getReplies());
        assertEquals(1, vo.getReplies().size());
        assertEquals("回复内容", vo.getReplies().get(0).getContent());
    }

    // ========== 回复列表查询 ==========

    @Test
    void listReplies_success() {
        FamilyMessage parent = buildMessage(1L, USER_ID, "用户A", "原始留言");
        when(familyMessageRepository.findById(1L)).thenReturn(parent);

        FamilyMessage reply1 = buildMessage(2L, OTHER_USER_ID, "用户B", "回复1");
        reply1.setParentId(1L);
        FamilyMessage reply2 = buildMessage(3L, USER_ID, "用户A", "回复2");
        reply2.setParentId(1L);
        when(familyMessageRepository.findByParentId(1L)).thenReturn(List.of(reply1, reply2));

        List<MessageVO> replies = messageApplicationService.listReplies(1L, USER_ID);

        assertEquals(2, replies.size());
        assertEquals("回复1", replies.get(0).getContent());
        assertEquals("回复2", replies.get(1).getContent());
    }

    @Test
    void listReplies_parentNotFound_throws() {
        when(familyMessageRepository.findById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () ->
                messageApplicationService.listReplies(999L, USER_ID));
    }

    // ========== 删除留言 ==========

    @Test
    void deleteMessage_success() {
        FamilyMessage msg = buildMessage(10L, USER_ID, "测试用户", "内容");
        when(familyMessageRepository.findById(10L)).thenReturn(msg);

        messageApplicationService.deleteMessage(10L, USER_ID);

        verify(familyMessageRepository).removeById(10L);
    }

    @Test
    void deleteMessage_rootMessage_cascadeDeleteReplies() {
        FamilyMessage msg = buildMessage(10L, USER_ID, "测试用户", "顶级留言");
        when(familyMessageRepository.findById(10L)).thenReturn(msg);

        messageApplicationService.deleteMessage(10L, USER_ID);

        verify(familyMessageRepository).removeByParentId(10L);
        verify(familyMessageRepository).removeById(10L);
    }

    @Test
    void deleteMessage_reply_decrementsParentCount() {
        FamilyMessage reply = buildMessage(20L, USER_ID, "测试用户", "回复内容");
        reply.setParentId(10L);
        when(familyMessageRepository.findById(20L)).thenReturn(reply);

        messageApplicationService.deleteMessage(20L, USER_ID);

        verify(familyMessageRepository).decrementReplyCount(10L);
        verify(familyMessageRepository).removeById(20L);
    }

    @Test
    void deleteMessage_notOwner_throws() {
        FamilyMessage msg = buildMessage(10L, OTHER_USER_ID, "其他用户", "内容");
        when(familyMessageRepository.findById(10L)).thenReturn(msg);

        assertThrows(BusinessException.class,
                () -> messageApplicationService.deleteMessage(10L, USER_ID));
        verify(familyMessageRepository, never()).removeById(any());
    }

    @Test
    void deleteMessage_notFound_throws() {
        when(familyMessageRepository.findById(999L)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> messageApplicationService.deleteMessage(999L, USER_ID));
        verify(familyMessageRepository, never()).removeById(any());
    }

    // ========== 辅助方法 ==========

    private FamilyMessage buildMessage(Long id, Long userId, String username, String content) {
        FamilyMessage msg = new FamilyMessage();
        msg.setId(id);
        msg.setFamilyId(FAMILY_ID);
        msg.setUserId(userId);
        msg.setUsername(username);
        msg.setContent(content);
        msg.setLikeCount(0L);
        msg.setReplyCount(0L);
        msg.setCreateTime(LocalDateTime.now());
        msg.setUpdateTime(LocalDateTime.now());
        return msg;
    }
}
