package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.FamilyNodeDTO;
import com.mouhin.family.tree.common.dto.NodeCreateDTO;
import com.mouhin.family.tree.common.enums.ColorLabelEnum;
import com.mouhin.family.tree.common.enums.RelationTypeEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyRelation;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.FamilyRelationRepository;
import com.mouhin.family.tree.domain.service.FamilyNodeDomainService;
import com.mouhin.family.tree.domain.service.RelationValidationDomainService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 族谱节点应用服务单元测试。
 * 覆盖：节点创建（含父节点/名称超长/日期非法）、世代同步、节点删除、
 * 搜索、批量颜色更新等核心场景。
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@ExtendWith(MockitoExtension.class)
class FamilyNodeApplicationServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long FAMILY_ID = 100L;

    @Mock
    private FamilyNodeRepository familyNodeRepository;

    @Mock
    private FamilyRelationRepository familyRelationRepository;

    @Mock
    private RelationValidationDomainService relationValidationDomainService;

    @Mock
    private FamilyNodeDomainService familyNodeDomainService;

    @Mock
    private FamilyTreeApplicationService familyTreeApplicationService;

    @InjectMocks
    private FamilyNodeApplicationService familyNodeApplicationService;

    // ========== 数据构造辅助 ==========

    private FamilyNode node(long id, String name, int generation) {
        FamilyNode n = new FamilyNode();
        n.setId(id);
        n.setUserId(USER_ID);
        n.setFamilyId(FAMILY_ID);
        n.setName(name);
        n.setGender(1);
        n.setGeneration(generation);
        n.setColorLabel(ColorLabelEnum.DEFAULT.getCode());
        return n;
    }

    private FamilyRelation parentChildRelation(long fromNodeId, long toNodeId) {
        FamilyRelation rel = new FamilyRelation();
        rel.setId(System.currentTimeMillis());
        rel.setUserId(USER_ID);
        rel.setFamilyId(FAMILY_ID);
        rel.setFromNodeId(fromNodeId);
        rel.setToNodeId(toNodeId);
        rel.setRelationType(RelationTypeEnum.PARENT_CHILD.getCode());
        return rel;
    }

    private FamilyRelation spouseRelation(long fromNodeId, long toNodeId) {
        FamilyRelation rel = new FamilyRelation();
        rel.setId(System.currentTimeMillis());
        rel.setUserId(USER_ID);
        rel.setFamilyId(FAMILY_ID);
        rel.setFromNodeId(fromNodeId);
        rel.setToNodeId(toNodeId);
        rel.setRelationType(RelationTypeEnum.SPOUSE.getCode());
        return rel;
    }

    private NodeCreateDTO createDTO(String name, Long parentNodeId) {
        NodeCreateDTO dto = new NodeCreateDTO();
        dto.setName(name);
        dto.setGender(1);
        dto.setParentNodeId(parentNodeId);
        return dto;
    }

    // ========== 测试用例 ==========

    @Test
    void createNode_success_withParent() {
        // 准备：父节点存在，世代为 2
        FamilyNode parent = node(10L, "蒙父", 2);
        when(familyNodeRepository.findById(10L)).thenReturn(parent);
        when(familyRelationRepository.countChildren(FAMILY_ID, 10L)).thenReturn(0L);

        // 模拟仓储保存后自动回填 ID
        doAnswer(invocation -> {
            FamilyNode nodeArg = invocation.getArgument(0);
            nodeArg.setId(99L);
            return nodeArg;
        }).when(familyNodeRepository).save(any(FamilyNode.class));

        // 执行
        NodeCreateDTO dto = createDTO("蒙子", 10L);
        Long newNodeId = familyNodeApplicationService.createNode(FAMILY_ID, USER_ID, dto);

        // 验证：新节点世代 = 父节点 + 1
        ArgumentCaptor<FamilyNode> nodeCaptor = ArgumentCaptor.forClass(FamilyNode.class);
        verify(familyNodeRepository).save(nodeCaptor.capture());
        FamilyNode insertedNode = nodeCaptor.getValue();
        assertEquals(3, insertedNode.getGeneration(), "子节点世代应为父节点+1");
        assertEquals("蒙子", insertedNode.getName());
        assertEquals(99L, newNodeId, "返回的新节点ID应为回填值");

        // 验证：创建了 PARENT_CHILD 关系
        ArgumentCaptor<FamilyRelation> relCaptor = ArgumentCaptor.forClass(FamilyRelation.class);
        verify(familyRelationRepository).save(relCaptor.capture());
        FamilyRelation insertedRel = relCaptor.getValue();
        assertEquals(10L, insertedRel.getFromNodeId());
        assertEquals(99L, insertedRel.getToNodeId());
        assertEquals(RelationTypeEnum.PARENT_CHILD.getCode(), insertedRel.getRelationType());

        // 验证：缓存已失效
        verify(familyTreeApplicationService).evictFamilyTree(FAMILY_ID);
    }

    @Test
    void createNode_nameTooLong_throwsException() {
        // 准备：名称超过 50 字符
        String longName = "a".repeat(FamilyTreeConsts.MAX_NAME_LENGTH + 1);
        NodeCreateDTO dto = createDTO(longName, null);

        // 执行 & 验证：抛出 BusinessException
        BusinessException exception = assertThrows(BusinessException.class,
                () -> familyNodeApplicationService.createNode(FAMILY_ID, USER_ID, dto));
        assertTrue(exception.getMessage().contains("不能超过"));
    }

    @Test
    void createNode_deathBeforeBirth_throwsException() {
        // 准备：去世日期早于出生日期
        NodeCreateDTO dto = createDTO("蒙人", null);
        dto.setBirthDate("2000-05-05");
        dto.setDeathDate("1990-01-01");

        // 执行 & 验证：抛出 BusinessException
        BusinessException exception = assertThrows(BusinessException.class,
                () -> familyNodeApplicationService.createNode(FAMILY_ID, USER_ID, dto));
        assertTrue(exception.getMessage().contains("去世日期不能早于出生日期"));
    }

    @Test
    void updateNode_changeGeneration_syncsDescendants() {
        // 准备：节点 1 世代从 1 改为 2
        FamilyNode existingNode = node(1L, "蒙祖", 1);
        when(familyNodeRepository.findById(1L)).thenReturn(existingNode);

        // syncSpouseGeneration：无配偶
        when(familyRelationRepository.findSpouseRelations(FAMILY_ID, 1L))
                .thenReturn(List.of());

        // syncDescendantGenerations：领域服务返回 2 个已更新世代的子节点
        FamilyNode child1 = node(2L, "蒙子一", 3);
        FamilyNode child2 = node(3L, "蒙子二", 3);
        when(familyNodeRepository.findByFamilyId(FAMILY_ID)).thenReturn(
                new ArrayList<>(List.of(existingNode, child1, child2)));
        when(familyRelationRepository.findByFamilyId(FAMILY_ID)).thenReturn(new ArrayList<>());
        when(familyNodeDomainService.syncDescendantGenerations(
                eq(1L), eq(2), any(List.class), any(List.class)))
                .thenReturn(List.of(child1, child2));

        // 执行：更新世代
        FamilyNodeDTO dto = new FamilyNodeDTO();
        dto.setId(1L);
        dto.setGeneration(2);
        familyNodeApplicationService.updateNode(FAMILY_ID, dto);

        // 验证：节点自身保存
        verify(familyNodeRepository).save(existingNode);
        assertEquals(2, existingNode.getGeneration());

        // 验证：子节点世代同步（领域服务返回的 2 个子节点各保存一次）
        verify(familyNodeRepository).save(child1);
        verify(familyNodeRepository).save(child2);

        // 验证：缓存已失效（syncDescendantGenerations 和 updateNode 各调用一次）
        verify(familyTreeApplicationService, times(2)).evictFamilyTree(FAMILY_ID);
    }

    @Test
    void deleteNode_removesRelationsAndNode() {
        // 准备：节点存在
        FamilyNode existingNode = node(5L, "蒙人", 2);
        when(familyNodeRepository.findById(5L)).thenReturn(existingNode);

        // 执行
        familyNodeApplicationService.deleteNode(FAMILY_ID, 5L);

        // 验证：删除了涉及该节点的所有关系
        verify(familyRelationRepository).removeByNodeId(FAMILY_ID, 5L);

        // 验证：删除了节点本身
        verify(familyNodeRepository).removeById(5L);

        // 验证：缓存已失效
        verify(familyTreeApplicationService).evictFamilyTree(FAMILY_ID);
    }

    @Test
    void searchNodes_emptyKeyword_returnsEmpty() {
        // 执行：空关键字
        List<FamilyNodeDTO> result1 =
                familyNodeApplicationService.searchNodes(FAMILY_ID, null);
        List<FamilyNodeDTO> result2 =
                familyNodeApplicationService.searchNodes(FAMILY_ID, "");
        List<FamilyNodeDTO> result3 =
                familyNodeApplicationService.searchNodes(FAMILY_ID, "   ");

        // 验证：返回空列表，不查询数据库
        assertTrue(result1.isEmpty());
        assertTrue(result2.isEmpty());
        assertTrue(result3.isEmpty());
        verify(familyNodeRepository, never()).findByFamilyIdAndNameContaining(
                any(), any());
    }

    @Test
    void searchNodes_withResults() {
        // 准备：仓储返回匹配节点
        List<FamilyNode> mockNodes = List.of(
                node(1L, "蒙甲", 1),
                node(2L, "蒙乙", 2)
        );
        when(familyNodeRepository.findByFamilyIdAndNameContaining(FAMILY_ID, "蒙"))
                .thenReturn(new ArrayList<>(mockNodes));

        // 执行
        List<FamilyNodeDTO> results =
                familyNodeApplicationService.searchNodes(FAMILY_ID, "蒙");

        // 验证：返回匹配结果
        assertEquals(2, results.size());
        assertEquals("蒙甲", results.get(0).getName());
        assertEquals("蒙乙", results.get(1).getName());

        // 验证：查询了仓储
        verify(familyNodeRepository).findByFamilyIdAndNameContaining(FAMILY_ID, "蒙");
    }

    @Test
    void updateColor_batchUpdate() {
        // 准备：节点存在且属于该家族
        List<FamilyNode> mockNodes = List.of(
                node(1L, "蒙甲", 1),
                node(2L, "蒙乙", 2),
                node(3L, "蒙丙", 2)
        );
        when(familyNodeRepository.findByIds(List.of(1L, 2L, 3L)))
                .thenReturn(new ArrayList<>(mockNodes));

        // 执行
        familyNodeApplicationService.updateColor(
                FAMILY_ID, List.of(1L, 2L, 3L), ColorLabelEnum.PATERNAL.getCode());

        // 验证：批量查询校验归属
        verify(familyNodeRepository).findByIds(List.of(1L, 2L, 3L));

        // 验证：批量更新颜色
        verify(familyNodeRepository).updateColorLabel(
                FAMILY_ID, List.of(1L, 2L, 3L), ColorLabelEnum.PATERNAL.getCode());

        // 验证：缓存已失效
        verify(familyTreeApplicationService).evictFamilyTree(FAMILY_ID);
    }
}
