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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 族谱留言应用服务单元测试。
 * 覆盖：发布留言（正常/空内容/超长/null用户名）、分页查询（空/有数据/own标记）、删除（正常/非本人/不存在）。
 *
 * @author Family-Tree
 * @date 2026-08-25
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

    // ========== 删除留言 ==========

    @Test
    void deleteMessage_success() {
        FamilyMessage msg = buildMessage(10L, USER_ID, "测试用户", "内容");
        when(familyMessageRepository.findById(10L)).thenReturn(msg);

        messageApplicationService.deleteMessage(10L, USER_ID);

        verify(familyMessageRepository).removeById(10L);
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
        msg.setCreateTime(LocalDateTime.now());
        msg.setUpdateTime(LocalDateTime.now());
        return msg;
    }
}
