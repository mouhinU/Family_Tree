package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.*;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyRelation;
import com.mouhin.family.tree.domain.gateway.AiChatGateway;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.FamilyRelationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AI 应用服务单元测试。
 * 覆盖：智能录入（正常/网关异常传播）、自然语言查询、家族故事（正常/节点不存在/家族不匹配）、
 * OCR 解析。大模型调用通过 mock AiChatGateway 隔离。
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@ExtendWith(MockitoExtension.class)
class AiApplicationServiceTest {

    private static final Long FAMILY_ID = 100L;
    private static final Long NODE_ID = 10L;

    @Mock
    private FamilyNodeRepository familyNodeRepository;

    @Mock
    private FamilyRelationRepository familyRelationRepository;

    @Mock
    private FamilyTreeApplicationService familyTreeApplicationService;

    @Mock
    private AiChatGateway aiChatGateway;

    @InjectMocks
    private AiApplicationService aiApplicationService;

    // ========== 智能录入 ==========

    @Test
    void smartEntry_returnsStructuredResult() {
        AiSmartEntryVO expected = buildSmartEntryVO("张三", "张小五");
        when(aiChatGateway.chatForEntity(anyString(), anyString(), eq(AiSmartEntryVO.class)))
                .thenReturn(expected);

        AiSmartEntryDTO dto = new AiSmartEntryDTO();
        dto.setDescription("张三是张小五的父亲，张三生于1950年");
        AiSmartEntryVO result = aiApplicationService.smartEntry(FAMILY_ID, dto);

        assertNotNull(result);
        assertEquals(2, result.getNodes().size());
        assertEquals("张三", result.getNodes().get(0).getName());
        assertEquals(1, result.getRelations().size());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiChatGateway).chatForEntity(anyString(), promptCaptor.capture(), eq(AiSmartEntryVO.class));
        assertTrue(promptCaptor.getValue().contains("张三是张小五的父亲"));
    }

    @Test
    void smartEntry_propagatesGatewayBusinessException() {
        when(aiChatGateway.chatForEntity(anyString(), anyString(), eq(AiSmartEntryVO.class)))
                .thenThrow(new BusinessException("AI 功能未启用，请在配置中设置 ai.llm.enabled=true"));

        AiSmartEntryDTO dto = new AiSmartEntryDTO();
        dto.setDescription("张三生于1950年");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> aiApplicationService.smartEntry(FAMILY_ID, dto));
        assertTrue(ex.getMessage().contains("AI 功能未启用"));
    }

    // ========== 自然语言查询 ==========

    @Test
    void query_returnsAnswer() {
        TreeNodeVO root = new TreeNodeVO();
        root.setId(1L);
        root.setName("张三");
        root.setGender(1);
        root.setBirthDate("1950-01-01");
        when(familyTreeApplicationService.getFullTree(FAMILY_ID)).thenReturn(List.of(root));
        when(aiChatGateway.chat(anyString(), anyString())).thenReturn("最高辈分是张三");

        AiQueryDTO dto = new AiQueryDTO();
        dto.setQuestion("族谱里谁是最高辈分？");
        AiQueryVO result = aiApplicationService.query(FAMILY_ID, dto);

        assertEquals("最高辈分是张三", result.getAnswer());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiChatGateway).chat(anyString(), promptCaptor.capture());
        assertTrue(promptCaptor.getValue().contains("张三"));
        assertTrue(promptCaptor.getValue().contains("族谱里谁是最高辈分？"));
    }

    @Test
    void query_emptyTree_buildsEmptySummary() {
        when(familyTreeApplicationService.getFullTree(FAMILY_ID)).thenReturn(List.of());
        when(aiChatGateway.chat(anyString(), anyString())).thenReturn("族谱数据为空");

        AiQueryDTO dto = new AiQueryDTO();
        dto.setQuestion("有几个人？");
        AiQueryVO result = aiApplicationService.query(FAMILY_ID, dto);

        assertEquals("族谱数据为空", result.getAnswer());
    }

    // ========== 家族故事 ==========

    @Test
    void generateStory_returnsStory() {
        FamilyNode node = buildNode(NODE_ID, FAMILY_ID, "张三");
        FamilyNode child = buildNode(11L, FAMILY_ID, "张小五");
        FamilyRelation relation = new FamilyRelation();
        relation.setFromNodeId(NODE_ID);
        relation.setToNodeId(11L);
        relation.setRelationType(1);

        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node);
        when(familyNodeRepository.findByFamilyId(FAMILY_ID)).thenReturn(List.of(node, child));
        when(familyRelationRepository.findByFamilyId(FAMILY_ID)).thenReturn(List.of(relation));
        when(aiChatGateway.chat(anyString(), anyString())).thenReturn("张三，一位勤劳朴实的父亲……");

        AiStoryDTO dto = new AiStoryDTO();
        dto.setNodeId(NODE_ID);
        AiStoryVO result = aiApplicationService.generateStory(FAMILY_ID, dto);

        assertEquals("张三，一位勤劳朴实的父亲……", result.getStory());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiChatGateway).chat(anyString(), promptCaptor.capture());
        assertTrue(promptCaptor.getValue().contains("张三"));
        assertTrue(promptCaptor.getValue().contains("张小五"));
    }

    @Test
    void generateStory_throwsWhenNodeNotFound() {
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(null);

        AiStoryDTO dto = new AiStoryDTO();
        dto.setNodeId(NODE_ID);

        assertThrows(BusinessException.class, () -> aiApplicationService.generateStory(FAMILY_ID, dto));
        verify(aiChatGateway, never()).chat(anyString(), anyString());
    }

    @Test
    void generateStory_throwsWhenNodeBelongsToOtherFamily() {
        FamilyNode node = buildNode(NODE_ID, 999L, "张三");
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node);

        AiStoryDTO dto = new AiStoryDTO();
        dto.setNodeId(NODE_ID);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> aiApplicationService.generateStory(FAMILY_ID, dto));
        assertTrue(ex.getMessage().contains("节点不存在或无权访问"));
        verify(aiChatGateway, never()).chat(anyString(), anyString());
    }

    // ========== OCR 解析 ==========

    @Test
    void ocrParse_returnsStructuredResult() {
        AiSmartEntryVO expected = buildSmartEntryVO("李四", "李小明");
        when(aiChatGateway.chatForEntity(anyString(), anyString(), eq(AiSmartEntryVO.class)))
                .thenReturn(expected);

        AiOcrParseDTO dto = new AiOcrParseDTO();
        dto.setRecognizedText("四公李四，生于一九三二年，子小明");
        AiSmartEntryVO result = aiApplicationService.ocrParse(FAMILY_ID, dto);

        assertNotNull(result);
        assertEquals("李四", result.getNodes().get(0).getName());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiChatGateway).chatForEntity(anyString(), promptCaptor.capture(), eq(AiSmartEntryVO.class));
        assertTrue(promptCaptor.getValue().contains("四公李四"));
    }

    // ========== 辅助方法 ==========

    private AiSmartEntryVO buildSmartEntryVO(String parentName, String childName) {
        AiSmartEntryVO vo = new AiSmartEntryVO();
        AiSmartEntryVO.AiNodeDTO parent = new AiSmartEntryVO.AiNodeDTO();
        parent.setName(parentName);
        parent.setGender(1);
        AiSmartEntryVO.AiNodeDTO child = new AiSmartEntryVO.AiNodeDTO();
        child.setName(childName);
        child.setGender(1);
        vo.setNodes(List.of(parent, child));

        AiSmartEntryVO.AiRelationDTO relation = new AiSmartEntryVO.AiRelationDTO();
        relation.setFromName(parentName);
        relation.setToName(childName);
        relation.setRelationType(1);
        vo.setRelations(List.of(relation));
        return vo;
    }

    private FamilyNode buildNode(Long id, Long familyId, String name) {
        FamilyNode node = new FamilyNode();
        node.setId(id);
        node.setFamilyId(familyId);
        node.setName(name);
        node.setGender(1);
        return node;
    }
}
