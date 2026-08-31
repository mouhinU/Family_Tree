package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.MemorialMessageDTO;
import com.mouhin.family.tree.common.dto.MemorialMessageVO;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.MemorialMessage;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.MemorialMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 祭堂缅怀留言应用服务单元测试。
 * 覆盖：发布留言（正常/在世节点/节点不存在/内容为空）、列表查询（含own标记）、
 * 删除留言（正常/非本人/不存在/跨家族）。
 *
 * @author Family-Tree
 * @date 2026-08-31
 */
@ExtendWith(MockitoExtension.class)
class MemorialApplicationServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long FAMILY_ID = 100L;
    private static final Long NODE_ID = 30L;

    @Mock
    private MemorialMessageRepository memorialMessageRepository;

    @Mock
    private FamilyNodeRepository familyNodeRepository;

    @InjectMocks
    private MemorialApplicationService memorialApplicationService;

    // ========== 发布留言 ==========

    @Test
    void postMessage_success() {
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(buildNode(true));

        MemorialMessageDTO dto = new MemorialMessageDTO();
        dto.setContent("  永远怀念您  ");

        when(memorialMessageRepository.save(any(MemorialMessage.class))).thenAnswer(invocation -> {
            MemorialMessage msg = invocation.getArgument(0);
            msg.setId(50L);
            return msg;
        });

        MemorialMessageVO vo = memorialApplicationService.postMessage(
                FAMILY_ID, NODE_ID, USER_ID, "测试用户", dto);

        ArgumentCaptor<MemorialMessage> captor = ArgumentCaptor.forClass(MemorialMessage.class);
        verify(memorialMessageRepository).save(captor.capture());
        MemorialMessage saved = captor.getValue();
        assertEquals(FAMILY_ID, saved.getFamilyId());
        assertEquals(NODE_ID, saved.getNodeId());
        assertEquals(USER_ID, saved.getUserId());
        assertEquals("永远怀念您", saved.getContent());
        assertEquals(50L, vo.getId());
        assertTrue(vo.getOwn());
    }

    @Test
    void postMessage_livingNode_throws() {
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(buildNode(false));

        MemorialMessageDTO dto = new MemorialMessageDTO();
        dto.setContent("内容");

        assertThrows(BusinessException.class, () ->
                memorialApplicationService.postMessage(FAMILY_ID, NODE_ID, USER_ID, "测试用户", dto));
        verify(memorialMessageRepository, never()).save(any(MemorialMessage.class));
    }

    @Test
    void postMessage_nodeNotFound_throws() {
        when(familyNodeRepository.findById(999L)).thenReturn(null);

        MemorialMessageDTO dto = new MemorialMessageDTO();
        dto.setContent("内容");

        assertThrows(BusinessException.class, () ->
                memorialApplicationService.postMessage(FAMILY_ID, 999L, USER_ID, "测试用户", dto));
    }

    @Test
    void postMessage_blankContent_throws() {
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(buildNode(true));

        MemorialMessageDTO dto = new MemorialMessageDTO();
        dto.setContent("   ");

        assertThrows(BusinessException.class, () ->
                memorialApplicationService.postMessage(FAMILY_ID, NODE_ID, USER_ID, "测试用户", dto));
        verify(memorialMessageRepository, never()).save(any(MemorialMessage.class));
    }

    // ========== 列表查询 ==========

    @Test
    void listMessages_success_ownFlag() {
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(buildNode(true));

        MemorialMessage mine = buildMessage(1L, USER_ID, "我的留言");
        MemorialMessage others = buildMessage(2L, OTHER_USER_ID, "别人的留言");
        when(memorialMessageRepository.findByNodeId(NODE_ID)).thenReturn(List.of(mine, others));

        List<MemorialMessageVO> result =
                memorialApplicationService.listMessages(FAMILY_ID, NODE_ID, USER_ID);

        assertEquals(2, result.size());
        assertTrue(result.get(0).getOwn());
        assertFalse(result.get(1).getOwn());
        assertEquals("我的留言", result.get(0).getContent());
    }

    // ========== 删除留言 ==========

    @Test
    void deleteMessage_success() {
        when(memorialMessageRepository.findById(1L)).thenReturn(buildMessage(1L, USER_ID, "内容"));

        memorialApplicationService.deleteMessage(FAMILY_ID, 1L, USER_ID);

        verify(memorialMessageRepository).removeById(1L);
    }

    @Test
    void deleteMessage_notAuthor_throws() {
        when(memorialMessageRepository.findById(1L))
                .thenReturn(buildMessage(1L, OTHER_USER_ID, "内容"));

        assertThrows(BusinessException.class, () ->
                memorialApplicationService.deleteMessage(FAMILY_ID, 1L, USER_ID));
        verify(memorialMessageRepository, never()).removeById(any());
    }

    @Test
    void deleteMessage_otherFamily_throws() {
        MemorialMessage message = buildMessage(1L, USER_ID, "内容");
        message.setFamilyId(999L);
        when(memorialMessageRepository.findById(1L)).thenReturn(message);

        assertThrows(BusinessException.class, () ->
                memorialApplicationService.deleteMessage(FAMILY_ID, 1L, USER_ID));
        verify(memorialMessageRepository, never()).removeById(any());
    }

    @Test
    void deleteMessage_notFound_throws() {
        when(memorialMessageRepository.findById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () ->
                memorialApplicationService.deleteMessage(FAMILY_ID, 999L, USER_ID));
        verify(memorialMessageRepository, never()).removeById(any());
    }

    // ========== 辅助方法 ==========

    private FamilyNode buildNode(boolean deceased) {
        FamilyNode node = new FamilyNode();
        node.setId(NODE_ID);
        node.setFamilyId(FAMILY_ID);
        node.setName("先人");
        if (deceased) {
            node.setDeathDate(LocalDate.of(2020, 1, 1));
        }
        return node;
    }

    private MemorialMessage buildMessage(Long id, Long userId, String content) {
        MemorialMessage message = new MemorialMessage();
        message.setId(id);
        message.setFamilyId(FAMILY_ID);
        message.setNodeId(NODE_ID);
        message.setUserId(userId);
        message.setUsername("用户" + userId);
        message.setContent(content);
        message.setCreateTime(LocalDateTime.now());
        return message;
    }
}
