package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.domain.entity.FamilyGeneration;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyRelation;
import com.mouhin.family.tree.domain.event.FamilyTreeUpdatedEvent;
import com.mouhin.family.tree.domain.repository.FamilyGenerationRepository;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.FamilyRelationRepository;
import com.mouhin.family.tree.domain.service.GenerationRepairDomainService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 世代动态修复应用服务单元测试：辈分表拦截、无变化跳过、落库与事件发布、空家族兜底。
 *
 * @author Family-Tree
 * @date 2026-11-19
 */
@ExtendWith(MockitoExtension.class)
class GenerationRepairApplicationServiceTest {

    private static final Long FAMILY_ID = 100L;

    @Mock
    private FamilyNodeRepository familyNodeRepository;

    @Mock
    private FamilyRelationRepository familyRelationRepository;

    @Mock
    private FamilyGenerationRepository familyGenerationRepository;

    @Mock
    private GenerationRepairDomainService generationRepairDomainService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private GenerationRepairApplicationService generationRepairApplicationService;

    // ========== 辈分表拦截 ==========

    @Test
    void repairIfUnmanagedSkipsFamilyWithManagedGenerations() {
        when(familyGenerationRepository.findByFamilyId(FAMILY_ID)).thenReturn(List.of(new FamilyGeneration()));

        GenerationRepairApplicationService.RepairResult result =
                generationRepairApplicationService.repairIfUnmanaged(FAMILY_ID);

        assertTrue(result.generationManaged());
        assertEquals(0, result.updatedNodes());
        verify(familyNodeRepository, never()).findByFamilyId(any());
        verify(familyNodeRepository, never()).batchUpdateGeneration(anyList());
    }

    @Test
    void repairIfUnmanagedRepairsWhenGenerationsAbsent() {
        when(familyGenerationRepository.findByFamilyId(FAMILY_ID)).thenReturn(List.of());
        when(familyNodeRepository.findByFamilyId(FAMILY_ID)).thenReturn(List.of(node(1L)));
        when(familyRelationRepository.findByFamilyId(FAMILY_ID)).thenReturn(List.of(relation(1L, 2L)));
        FamilyNode changedNode = node(2L, 1);
        when(generationRepairDomainService.repair(anyList(), anyList())).thenReturn(List.of(changedNode));

        GenerationRepairApplicationService.RepairResult result =
                generationRepairApplicationService.repairIfUnmanaged(FAMILY_ID);

        assertFalse(result.generationManaged());
        assertEquals(1, result.updatedNodes());
        verify(familyNodeRepository).batchUpdateGeneration(List.of(changedNode));
    }

    // ========== 修复落库 ==========

    @Test
    void repairFamilyPersistsChangesWithUpdateTimeAndTreeEvent() {
        FamilyNode changedNode = node(2L, 1);
        when(familyNodeRepository.findByFamilyId(FAMILY_ID)).thenReturn(List.of(node(1L), changedNode));
        when(familyRelationRepository.findByFamilyId(FAMILY_ID)).thenReturn(List.of(relation(1L, 2L)));
        when(generationRepairDomainService.repair(anyList(), anyList())).thenReturn(List.of(changedNode));

        int updated = generationRepairApplicationService.repairFamily(FAMILY_ID);

        assertEquals(1, updated);
        assertNotNull(changedNode.getUpdateTime(), "批量更新世代必须同步 update_time");
        verify(familyNodeRepository).batchUpdateGeneration(List.of(changedNode));
        ArgumentCaptor<FamilyTreeUpdatedEvent> event = ArgumentCaptor.forClass(FamilyTreeUpdatedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals(FAMILY_ID, event.getValue().familyId());
    }

    @Test
    void repairFamilyWithoutChangesSkipsPersistenceAndEvent() {
        when(familyNodeRepository.findByFamilyId(FAMILY_ID)).thenReturn(List.of(node(1L)));
        when(familyRelationRepository.findByFamilyId(FAMILY_ID)).thenReturn(List.of());
        when(generationRepairDomainService.repair(anyList(), anyList())).thenReturn(List.of());

        int updated = generationRepairApplicationService.repairFamily(FAMILY_ID);

        assertEquals(0, updated);
        verify(familyNodeRepository, never()).batchUpdateGeneration(anyList());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void repairFamilyOnEmptyNodesReturnsZeroWithoutLoadingRelations() {
        when(familyNodeRepository.findByFamilyId(FAMILY_ID)).thenReturn(List.of());

        int updated = generationRepairApplicationService.repairFamily(FAMILY_ID);

        assertEquals(0, updated);
        verify(familyRelationRepository, never()).findByFamilyId(any());
    }

    // ========== 数据构造辅助 ==========

    private FamilyNode node(Long id) {
        return node(id, 1);
    }

    private FamilyNode node(Long id, Integer generation) {
        FamilyNode node = new FamilyNode();
        node.setId(id);
        node.setFamilyId(FAMILY_ID);
        node.setName("节点" + id);
        node.setGeneration(generation);
        return node;
    }

    private FamilyRelation relation(Long fromId, Long toId) {
        FamilyRelation relation = new FamilyRelation();
        relation.setFamilyId(FAMILY_ID);
        relation.setFromNodeId(fromId);
        relation.setToNodeId(toId);
        relation.setRelationType(1);
        return relation;
    }
}
