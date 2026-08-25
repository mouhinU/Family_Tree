package com.mouhin.family.tree.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.family.tree.common.dto.MessageCreateDTO;
import com.mouhin.family.tree.common.dto.MessageVO;
import com.mouhin.family.tree.common.dto.PageResult;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.persistence.entity.FamilyMessageDO;
import com.mouhin.family.tree.persistence.mapper.FamilyMessageMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 族谱留言服务单元测试。
 * 覆盖：发布留言（正常/空内容/超长/null用户名）、分页查询（空/有数据/own标记）、删除（正常/非本人/不存在）。
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@ExtendWith(MockitoExtension.class)
class FamilyMessageServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long FAMILY_ID = 100L;

    @Mock
    private FamilyMessageMapper messageMapper;

    @InjectMocks
    private FamilyMessageServiceImpl messageService;

    // ========== 发布留言 ==========

    @Test
    void postMessage_success() {
        MessageCreateDTO dto = new MessageCreateDTO();
        dto.setContent("  你好，世界  ");

        doAnswer(invocation -> {
            FamilyMessageDO msg = invocation.getArgument(0);
            msg.setId(10L);
            return 1;
        }).when(messageMapper).insert(ArgumentMatchers.<FamilyMessageDO>any());

        messageService.postMessage(FAMILY_ID, USER_ID, "测试用户", dto);

        ArgumentCaptor<FamilyMessageDO> captor = ArgumentCaptor.forClass(FamilyMessageDO.class);
        verify(messageMapper).insert(captor.capture());
        FamilyMessageDO saved = captor.getValue();
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
            FamilyMessageDO msg = invocation.getArgument(0);
            msg.setId(11L);
            return 1;
        }).when(messageMapper).insert(ArgumentMatchers.<FamilyMessageDO>any());

        messageService.postMessage(FAMILY_ID, USER_ID, null, dto);

        ArgumentCaptor<FamilyMessageDO> captor = ArgumentCaptor.forClass(FamilyMessageDO.class);
        verify(messageMapper).insert(captor.capture());
        assertEquals("匿名", captor.getValue().getUsername());
    }

    @Test
    void postMessage_emptyContent_throws() {
        MessageCreateDTO dto = new MessageCreateDTO();
        dto.setContent("   ");

        assertThrows(BusinessException.class, () ->
                messageService.postMessage(FAMILY_ID, USER_ID, "测试用户", dto));
        verify(messageMapper, never()).insert(ArgumentMatchers.<FamilyMessageDO>any());
    }

    @Test
    void postMessage_contentTooLong_throws() {
        MessageCreateDTO dto = new MessageCreateDTO();
        dto.setContent("a".repeat(501));

        assertThrows(BusinessException.class, () ->
                messageService.postMessage(FAMILY_ID, USER_ID, "测试用户", dto));
        verify(messageMapper, never()).insert(ArgumentMatchers.<FamilyMessageDO>any());
    }

    // ========== 分页查询 ==========

    @Test
    void listMessages_empty() {
        when(messageMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        PageResult<MessageVO> result = messageService.listMessages(FAMILY_ID, USER_ID, 1, 20);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    @Test
    void listMessages_withData_ownFlag() {
        when(messageMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        FamilyMessageDO msg1 = buildMessage(1L, USER_ID, "用户A", "我的留言");
        FamilyMessageDO msg2 = buildMessage(2L, OTHER_USER_ID, "用户B", "别人的留言");
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(msg1, msg2));

        PageResult<MessageVO> result = messageService.listMessages(FAMILY_ID, USER_ID, 1, 20);

        assertEquals(2L, result.getTotal());
        assertEquals(2, result.getRecords().size());
        assertTrue(result.getRecords().get(0).getOwn());
        assertFalse(result.getRecords().get(1).getOwn());
    }

    // ========== 删除留言 ==========

    @Test
    void deleteMessage_success() {
        FamilyMessageDO msg = buildMessage(10L, USER_ID, "测试用户", "内容");
        when(messageMapper.selectById(10L)).thenReturn(msg);

        messageService.deleteMessage(10L, USER_ID);

        verify(messageMapper).deleteById(ArgumentMatchers.<Long>any());
    }

    @Test
    void deleteMessage_notOwner_throws() {
        FamilyMessageDO msg = buildMessage(10L, OTHER_USER_ID, "其他用户", "内容");
        when(messageMapper.selectById(10L)).thenReturn(msg);

        assertThrows(BusinessException.class, () -> messageService.deleteMessage(10L, USER_ID));
        verify(messageMapper, never()).deleteById(ArgumentMatchers.<Long>any());
    }

    @Test
    void deleteMessage_notFound_throws() {
        when(messageMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> messageService.deleteMessage(999L, USER_ID));
        verify(messageMapper, never()).deleteById(ArgumentMatchers.<Long>any());
    }

    // ========== 辅助方法 ==========

    private FamilyMessageDO buildMessage(Long id, Long userId, String username, String content) {
        FamilyMessageDO msg = new FamilyMessageDO();
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
