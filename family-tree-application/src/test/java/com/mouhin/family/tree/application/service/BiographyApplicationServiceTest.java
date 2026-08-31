package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.BiographyUpdateDTO;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.common.util.HtmlSanitizeUtils;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.event.OperationPerformedEvent;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 人物传记应用服务单元测试。
 * 覆盖：查询传记（正常/未撰写/节点不存在/跨家族）、
 * 更新传记（正常清洗/脚本过滤/清空/超长/节点不存在）。
 *
 * @author Family-Tree
 * @date 2026-08-31
 */
@ExtendWith(MockitoExtension.class)
class BiographyApplicationServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long FAMILY_ID = 100L;
    private static final Long NODE_ID = 30L;

    @Mock
    private FamilyNodeRepository familyNodeRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private BiographyApplicationService biographyApplicationService;

    // ========== 查询传记 ==========

    @Test
    void getBiography_success() {
        FamilyNode node = buildNode("<p>生平简介</p>");
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node);

        String biography = biographyApplicationService.getBiography(FAMILY_ID, NODE_ID);

        assertEquals("<p>生平简介</p>", biography);
    }

    @Test
    void getBiography_notWritten_returnsEmpty() {
        FamilyNode node = buildNode(null);
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node);

        assertEquals("", biographyApplicationService.getBiography(FAMILY_ID, NODE_ID));
    }

    @Test
    void getBiography_nodeNotFound_throws() {
        when(familyNodeRepository.findById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () ->
                biographyApplicationService.getBiography(FAMILY_ID, 999L));
    }

    @Test
    void getBiography_otherFamily_throws() {
        FamilyNode node = buildNode("内容");
        node.setFamilyId(999L);
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node);

        assertThrows(BusinessException.class, () ->
                biographyApplicationService.getBiography(FAMILY_ID, NODE_ID));
    }

    // ========== 更新传记 ==========

    @Test
    void updateBiography_success() {
        FamilyNode node = buildNode(null);
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node);

        BiographyUpdateDTO dto = new BiographyUpdateDTO();
        dto.setBiography("<p>新的生平故事</p>");

        biographyApplicationService.updateBiography(FAMILY_ID, NODE_ID, USER_ID, "测试用户", dto);

        ArgumentCaptor<FamilyNode> captor = ArgumentCaptor.forClass(FamilyNode.class);
        verify(familyNodeRepository).update(captor.capture());
        assertTrue(captor.getValue().getBiography().contains("新的生平故事"));
        assertNotNull(captor.getValue().getUpdateTime());

        ArgumentCaptor<OperationPerformedEvent> eventCaptor =
                ArgumentCaptor.forClass(OperationPerformedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        OperationPerformedEvent event = eventCaptor.getValue();
        assertEquals("BIOGRAPHY_UPDATE", event.operationType());
        assertEquals(USER_ID, event.userId());
        assertEquals("测试用户", event.username());
        assertEquals("node", event.targetType());
        assertEquals(NODE_ID, event.targetId());
        assertEquals(FAMILY_ID, event.familyId());
    }

    @Test
    void updateBiography_stripsScript() {
        FamilyNode node = buildNode(null);
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node);

        BiographyUpdateDTO dto = new BiographyUpdateDTO();
        dto.setBiography("<p>正文</p><script>alert(1)</script>");

        biographyApplicationService.updateBiography(FAMILY_ID, NODE_ID, USER_ID, "测试用户", dto);

        ArgumentCaptor<FamilyNode> captor = ArgumentCaptor.forClass(FamilyNode.class);
        verify(familyNodeRepository).update(captor.capture());
        String saved = captor.getValue().getBiography();
        assertFalse(saved.contains("script"));
        assertTrue(saved.contains("正文"));
    }

    @Test
    void updateBiography_blankContent_clears() {
        FamilyNode node = buildNode("<p>旧传记</p>");
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node);

        BiographyUpdateDTO dto = new BiographyUpdateDTO();
        dto.setBiography("   ");

        biographyApplicationService.updateBiography(FAMILY_ID, NODE_ID, USER_ID, "测试用户", dto);

        ArgumentCaptor<FamilyNode> captor = ArgumentCaptor.forClass(FamilyNode.class);
        verify(familyNodeRepository).update(captor.capture());
        assertNull(captor.getValue().getBiography());
    }

    @Test
    void updateBiography_tooLong_throws() {
        FamilyNode node = buildNode(null);
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node);

        BiographyUpdateDTO dto = new BiographyUpdateDTO();
        dto.setBiography("a".repeat(HtmlSanitizeUtils.MAX_RICH_TEXT_LENGTH + 1));

        assertThrows(BusinessException.class, () ->
                biographyApplicationService.updateBiography(FAMILY_ID, NODE_ID, USER_ID, "测试用户", dto));
        verify(familyNodeRepository, never()).update(any(FamilyNode.class));
    }

    // ========== 辅助方法 ==========

    private FamilyNode buildNode(String biography) {
        FamilyNode node = new FamilyNode();
        node.setId(NODE_ID);
        node.setFamilyId(FAMILY_ID);
        node.setName("张三");
        node.setBiography(biography);
        return node;
    }
}
