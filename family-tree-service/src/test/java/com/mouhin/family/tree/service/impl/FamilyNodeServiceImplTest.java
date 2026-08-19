package com.mouhin.family.tree.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.FamilyNodeDTO;
import com.mouhin.family.tree.common.dto.NodeCreateDTO;
import com.mouhin.family.tree.common.enums.ColorLabelEnum;
import com.mouhin.family.tree.common.enums.RelationTypeEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.persistence.entity.FamilyNodeDO;
import com.mouhin.family.tree.persistence.entity.FamilyRelationDO;
import com.mouhin.family.tree.persistence.mapper.FamilyNodeMapper;
import com.mouhin.family.tree.persistence.mapper.FamilyRelationMapper;
import com.mouhin.family.tree.service.FamilyRelationService;
import com.mouhin.family.tree.service.FamilyTreeService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
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
 * 族谱节点服务单元测试。
 * 覆盖：节点创建（含父节点/名称超长/日期非法）、世代同步、节点删除、
 * 搜索、批量颜色更新等核心场景。
 *
 * @author Family-Tree
 * @date 2026-08-09
 */
@ExtendWith(MockitoExtension.class)
class FamilyNodeServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final Long FAMILY_ID = 100L;

    @Mock
    private FamilyNodeMapper familyNodeMapper;

    @Mock
    private FamilyRelationMapper familyRelationMapper;

    @Mock
    private FamilyRelationService familyRelationService;

    @Mock
    private FamilyTreeService familyTreeService;

    @InjectMocks
    private FamilyNodeServiceImpl familyNodeService;

    /**
     * 初始化 MyBatis-Plus Lambda 缓存，避免单元测试中 LambdaUpdateWrapper 报
     * "can not find lambda cache for this entity" 异常。
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        org.apache.ibatis.session.Configuration configuration =
                new org.apache.ibatis.session.Configuration();
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, FamilyNodeDO.class);
        TableInfoHelper.initTableInfo(assistant, FamilyRelationDO.class);
    }

    // ========== 数据构造辅助 ==========

    private FamilyNodeDO node(long id, String name, int generation) {
        FamilyNodeDO n = new FamilyNodeDO();
        n.setId(id);
        n.setUserId(USER_ID);
        n.setFamilyId(FAMILY_ID);
        n.setName(name);
        n.setGender(1);
        n.setGeneration(generation);
        n.setColorLabel(ColorLabelEnum.DEFAULT.getCode());
        return n;
    }

    private FamilyRelationDO parentChildRelation(long fromNodeId, long toNodeId) {
        FamilyRelationDO rel = new FamilyRelationDO();
        rel.setId(System.currentTimeMillis());
        rel.setUserId(USER_ID);
        rel.setFamilyId(FAMILY_ID);
        rel.setFromNodeId(fromNodeId);
        rel.setToNodeId(toNodeId);
        rel.setRelationType(RelationTypeEnum.PARENT_CHILD.getCode());
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
        FamilyNodeDO parent = node(10L, "蒙父", 2);
        when(familyNodeMapper.selectById(10L)).thenReturn(parent);
        when(familyRelationMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        // 模拟 MyBatis-Plus 插入后自动回填 ID
        doAnswer(invocation -> {
            FamilyNodeDO nodeArg = invocation.getArgument(0);
            nodeArg.setId(99L);
            return 1;
        }).when(familyNodeMapper).insert(any(FamilyNodeDO.class));

        // 执行
        NodeCreateDTO dto = createDTO("蒙子", 10L);
        Long newNodeId = familyNodeService.createNode(FAMILY_ID, USER_ID, dto);

        // 验证：新节点世代 = 父节点 + 1
        ArgumentCaptor<FamilyNodeDO> nodeCaptor = ArgumentCaptor.forClass(FamilyNodeDO.class);
        verify(familyNodeMapper).insert(nodeCaptor.capture());
        FamilyNodeDO insertedNode = nodeCaptor.getValue();
        assertEquals(3, insertedNode.getGeneration(), "子节点世代应为父节点+1");
        assertEquals("蒙子", insertedNode.getName());
        assertEquals(99L, newNodeId, "返回的新节点ID应为回填值");

        // 验证：创建了 PARENT_CHILD 关系
        ArgumentCaptor<FamilyRelationDO> relCaptor = ArgumentCaptor.forClass(FamilyRelationDO.class);
        verify(familyRelationMapper).insert(relCaptor.capture());
        FamilyRelationDO insertedRel = relCaptor.getValue();
        assertEquals(10L, insertedRel.getFromNodeId());
        assertEquals(99L, insertedRel.getToNodeId());
        assertEquals(RelationTypeEnum.PARENT_CHILD.getCode(), insertedRel.getRelationType());

        // 验证：缓存已失效
        verify(familyTreeService).evictFamilyTree(FAMILY_ID);
    }

    @Test
    void createNode_nameTooLong_throwsException() {
        // 准备：名称超过 50 字符
        String longName = "a".repeat(FamilyTreeConsts.MAX_NAME_LENGTH + 1);
        NodeCreateDTO dto = createDTO(longName, null);

        // 执行 & 验证：抛出 BusinessException
        BusinessException exception = assertThrows(BusinessException.class,
                () -> familyNodeService.createNode(FAMILY_ID, USER_ID, dto));
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
                () -> familyNodeService.createNode(FAMILY_ID, USER_ID, dto));
        assertTrue(exception.getMessage().contains("去世日期不能早于出生日期"));
    }

    @Test
    void updateNode_changeGeneration_syncsDescendants() {
        // 准备：节点 1 世代从 1 改为 2，其子节点 2、3 应同步为 3
        FamilyNodeDO existingNode = node(1L, "蒙祖", 1);
        when(familyNodeMapper.selectById(1L)).thenReturn(existingNode);

        // 模拟关系查询：第一次调用（syncSpouseGeneration）返回空，
        // 第二次调用（parent-child query）返回亲子关系，
        // 第三次调用（spouse query in syncDescendantGenerations）返回空
        List<FamilyRelationDO> parentChildRelations = List.of(
                parentChildRelation(1L, 2L),
                parentChildRelation(1L, 3L)
        );
        when(familyRelationMapper.selectList(any(Wrapper.class)))
                .thenReturn(new ArrayList<>())  // syncSpouseGeneration: no spouses
                .thenReturn(new ArrayList<>(parentChildRelations))  // parent-child query
                .thenReturn(new ArrayList<>());  // spouse query in syncDescendantGenerations

        // 执行：更新世代
        FamilyNodeDTO dto = new FamilyNodeDTO();
        dto.setId(1L);
        dto.setGeneration(2);
        familyNodeService.updateNode(FAMILY_ID, dto);

        // 验证：节点自身更新
        ArgumentCaptor<FamilyNodeDO> nodeCaptor = ArgumentCaptor.forClass(FamilyNodeDO.class);
        verify(familyNodeMapper).updateById(nodeCaptor.capture());
        assertEquals(2, nodeCaptor.getValue().getGeneration());

        // 验证：子节点世代同步（通过 LambdaUpdateWrapper 批量更新）
        // syncDescendantGenerations 应更新 2 个子节点（节点 2 和 3）
        verify(familyNodeMapper, times(2)).update(eq(null), any(Wrapper.class));

        // 验证：缓存已失效（syncDescendantGenerations 和 updateNode 各调用一次）
        verify(familyTreeService, times(2)).evictFamilyTree(FAMILY_ID);
    }

    @Test
    void deleteNode_removesRelationsAndNode() {
        // 准备：节点存在
        FamilyNodeDO existingNode = node(5L, "蒙人", 2);
        when(familyNodeMapper.selectById(5L)).thenReturn(existingNode);

        // 执行
        familyNodeService.deleteNode(FAMILY_ID, 5L);

        // 验证：删除了涉及该节点的所有关系
        verify(familyRelationMapper).delete(any(Wrapper.class));

        // 验证：删除了节点本身
        verify(familyNodeMapper).deleteById(5L);

        // 验证：缓存已失效
        verify(familyTreeService).evictFamilyTree(FAMILY_ID);
    }

    @Test
    void searchNodes_emptyKeyword_returnsEmpty() {
        // 执行：空关键字
        List<com.mouhin.family.tree.common.dto.FamilyNodeDTO> result1 =
                familyNodeService.searchNodes(FAMILY_ID, null);
        List<com.mouhin.family.tree.common.dto.FamilyNodeDTO> result2 =
                familyNodeService.searchNodes(FAMILY_ID, "");
        List<com.mouhin.family.tree.common.dto.FamilyNodeDTO> result3 =
                familyNodeService.searchNodes(FAMILY_ID, "   ");

        // 验证：返回空列表，不查询数据库
        assertTrue(result1.isEmpty());
        assertTrue(result2.isEmpty());
        assertTrue(result3.isEmpty());
        verify(familyNodeMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    void searchNodes_withResults() {
        // 准备：数据库返回匹配节点
        List<FamilyNodeDO> mockNodes = List.of(
                node(1L, "蒙甲", 1),
                node(2L, "蒙乙", 2)
        );
        when(familyNodeMapper.selectList(any(Wrapper.class))).thenReturn(new ArrayList<>(mockNodes));

        // 执行
        List<com.mouhin.family.tree.common.dto.FamilyNodeDTO> results =
                familyNodeService.searchNodes(FAMILY_ID, "蒙");

        // 验证：返回匹配结果
        assertEquals(2, results.size());
        assertEquals("蒙甲", results.get(0).getName());
        assertEquals("蒙乙", results.get(1).getName());

        // 验证：查询了数据库（LIKE 查询 + LIMIT 20 由 MyBatis-Plus 处理）
        verify(familyNodeMapper).selectList(any(Wrapper.class));
    }

    @Test
    void updateColor_batchUpdate() {
        // 准备：节点存在且属于该家族
        List<FamilyNodeDO> mockNodes = List.of(
                node(1L, "蒙甲", 1),
                node(2L, "蒙乙", 2),
                node(3L, "蒙丙", 2)
        );
        when(familyNodeMapper.selectBatchIds(List.of(1L, 2L, 3L)))
                .thenReturn(new ArrayList<>(mockNodes));

        // 执行
        familyNodeService.updateColor(FAMILY_ID, List.of(1L, 2L, 3L), ColorLabelEnum.PATERNAL.getCode());

        // 验证：使用 selectBatchIds 批量查询（非 N+1）
        verify(familyNodeMapper).selectBatchIds(List.of(1L, 2L, 3L));
        verify(familyNodeMapper, never()).selectById(any());

        // 验证：使用单次批量更新
        verify(familyNodeMapper).update(eq(null), any(Wrapper.class));

        // 验证：缓存已失效
        verify(familyTreeService).evictFamilyTree(FAMILY_ID);
    }
}
