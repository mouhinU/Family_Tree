package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.NodeHistory;
import com.mouhin.family.tree.domain.event.FamilyTreeUpdatedEvent;
import com.mouhin.family.tree.domain.event.NodeUpdatedEvent;
import com.mouhin.family.tree.domain.event.OperationPerformedEvent;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.FamilyRelationRepository;
import com.mouhin.family.tree.domain.repository.HistoryRepository;
import com.mouhin.family.tree.domain.repository.SnapshotRepository;
import com.mouhin.family.tree.domain.service.VersionControlDomainService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 版本控制应用服务单元测试。
 * <p>
 * 覆盖版本回滚的落地写入：历史校验、内容覆盖、世代与昵称联动、回滚历史与操作日志，
 * 以及版本不存在 / 跨家族 / 删除版本 / 节点已消失四类失败关闭。
 *
 * @author Family-Tree
 * @date 2026-11-17
 */
@ExtendWith(MockitoExtension.class)
class VersionControlApplicationServiceTest {

    private static final Long NODE_ID = 7L;
    private static final Long FAMILY_ID = 100L;
    private static final Long OTHER_FAMILY_ID = 200L;
    private static final Long USER_ID = 3L;
    private static final String USERNAME = "蒙斌";
    private static final String IP = "127.0.0.1";
    private static final String VERSION_JSON = "{\"name\":\"蒙一\"}";
    private static final int TARGET_VERSION = 2;

    @Mock
    private HistoryRepository historyRepository;

    @Mock
    private SnapshotRepository snapshotRepository;

    @Mock
    private FamilyNodeRepository familyNodeRepository;

    @Mock
    private FamilyRelationRepository familyRelationRepository;

    @Mock
    private VersionControlDomainService versionControlDomainService;

    @Mock
    private FamilyNodeApplicationService familyNodeApplicationService;

    @Mock
    private FamilyUserNodeApplicationService familyUserNodeApplicationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private VersionControlApplicationService service;

    @Test
    void rollbackWritesVersionContentAndRecordsRollbackHistory() {
        givenVersionHistory(FAMILY_ID, VERSION_JSON);
        FamilyNode versionNode = node("蒙一", 1);
        when(versionControlDomainService.parseNodeVersion(VERSION_JSON)).thenReturn(versionNode);
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node("蒙二", 1));

        String returned = service.rollbackNodeToVersion(NODE_ID, TARGET_VERSION, FAMILY_ID,
                USER_ID, USERNAME, IP);

        assertEquals(VERSION_JSON, returned);
        ArgumentCaptor<FamilyNode> updated = ArgumentCaptor.forClass(FamilyNode.class);
        verify(familyNodeRepository).update(updated.capture());
        assertEquals("蒙一", updated.getValue().getName());
        assertEquals(NODE_ID, updated.getValue().getId());
        assertEquals(FAMILY_ID, updated.getValue().getFamilyId());
        verify(versionControlDomainService).recordNodeRollback(any(FamilyNode.class),
                any(FamilyNode.class), eq(TARGET_VERSION), eq(USER_ID), eq(USERNAME), eq(IP));
        verify(eventPublisher).publishEvent(any(FamilyTreeUpdatedEvent.class));
        verify(eventPublisher).publishEvent(any(NodeUpdatedEvent.class));
        ArgumentCaptor<OperationPerformedEvent> operation =
                ArgumentCaptor.forClass(OperationPerformedEvent.class);
        verify(eventPublisher).publishEvent(operation.capture());
        assertEquals("VERSION_ROLLBACK", operation.getValue().operationType());
        assertEquals(FAMILY_ID, operation.getValue().familyId());
    }

    @Test
    void rollbackSyncsGenerationCascadeWhenGenerationChanged() {
        givenVersionHistory(FAMILY_ID, VERSION_JSON);
        when(versionControlDomainService.parseNodeVersion(VERSION_JSON)).thenReturn(node("蒙一", 1));
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node("蒙一", 3));

        service.rollbackNodeToVersion(NODE_ID, TARGET_VERSION, FAMILY_ID, USER_ID, USERNAME, IP);

        verify(familyNodeApplicationService).syncGenerationCascade(FAMILY_ID, NODE_ID, 1);
    }

    @Test
    void rollbackSkipsGenerationAndNicknameSyncWhenContentUnchanged() {
        givenVersionHistory(FAMILY_ID, VERSION_JSON);
        when(versionControlDomainService.parseNodeVersion(VERSION_JSON)).thenReturn(node("蒙一", 2));
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node("蒙一", 2));

        service.rollbackNodeToVersion(NODE_ID, TARGET_VERSION, FAMILY_ID, USER_ID, USERNAME, IP);

        verify(familyNodeApplicationService, never()).syncGenerationCascade(anyLong(), anyLong(), any());
        verify(familyUserNodeApplicationService, never()).syncNicknameFromNode(anyLong(), anyLong(), anyString());
    }

    @Test
    void rollbackSyncsBoundAccountNicknameOnRename() {
        givenVersionHistory(FAMILY_ID, VERSION_JSON);
        when(versionControlDomainService.parseNodeVersion(VERSION_JSON)).thenReturn(node("蒙一", 2));
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node("蒙二", 2));

        service.rollbackNodeToVersion(NODE_ID, TARGET_VERSION, FAMILY_ID, USER_ID, USERNAME, IP);

        verify(familyUserNodeApplicationService).syncNicknameFromNode(FAMILY_ID, NODE_ID, "蒙一");
    }

    @Test
    void rollbackFailsWhenVersionMissing() {
        when(historyRepository.findNodeHistoryByVersion(NODE_ID, TARGET_VERSION)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.rollbackNodeToVersion(NODE_ID, TARGET_VERSION, FAMILY_ID,
                        USER_ID, USERNAME, IP));

        assertEquals("版本" + TARGET_VERSION + "不存在", ex.getMessage());
        verify(familyNodeRepository, never()).update(any());
    }

    @Test
    void rollbackFailsWhenVersionBelongsToOtherFamily() {
        givenVersionHistory(OTHER_FAMILY_ID, VERSION_JSON);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.rollbackNodeToVersion(NODE_ID, TARGET_VERSION, FAMILY_ID,
                        USER_ID, USERNAME, IP));

        assertEquals("无权回滚该节点版本", ex.getMessage());
        verify(familyNodeRepository, never()).update(any());
    }

    @Test
    void rollbackFailsForDeleteVersionWithoutAfterData() {
        givenVersionHistory(FAMILY_ID, null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.rollbackNodeToVersion(NODE_ID, TARGET_VERSION, FAMILY_ID,
                        USER_ID, USERNAME, IP));

        assertEquals("该版本为删除记录，无法回滚", ex.getMessage());
        verify(familyNodeRepository, never()).update(any());
        verify(versionControlDomainService, never()).recordNodeRollback(any(), any(), anyInt(),
                anyLong(), anyString(), anyString());
    }

    @Test
    void rollbackFailsWhenNodeAlreadyRemoved() {
        givenVersionHistory(FAMILY_ID, VERSION_JSON);
        when(versionControlDomainService.parseNodeVersion(VERSION_JSON)).thenReturn(node("蒙一", 1));
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.rollbackNodeToVersion(NODE_ID, TARGET_VERSION, FAMILY_ID,
                        USER_ID, USERNAME, IP));

        assertEquals("节点不存在，无法回滚", ex.getMessage());
        verify(familyNodeRepository, never()).update(any());
    }

    @Test
    void rollbackFailsWhenNodeBelongsToOtherFamily() {
        givenVersionHistory(FAMILY_ID, VERSION_JSON);
        when(versionControlDomainService.parseNodeVersion(VERSION_JSON)).thenReturn(node("蒙一", 1));
        FamilyNode foreignNode = node("蒙二", 1);
        foreignNode.setFamilyId(OTHER_FAMILY_ID);
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(foreignNode);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.rollbackNodeToVersion(NODE_ID, TARGET_VERSION, FAMILY_ID,
                        USER_ID, USERNAME, IP));

        assertEquals("节点不存在，无法回滚", ex.getMessage());
        verify(familyNodeRepository, never()).update(any());
    }

    private void givenVersionHistory(Long familyId, String afterData) {
        NodeHistory history = new NodeHistory();
        history.setNodeId(NODE_ID);
        history.setFamilyId(familyId);
        history.setVersionNumber(TARGET_VERSION);
        history.setAfterData(afterData);
        when(historyRepository.findNodeHistoryByVersion(NODE_ID, TARGET_VERSION)).thenReturn(history);
    }

    private FamilyNode node(String name, Integer generation) {
        FamilyNode node = new FamilyNode();
        node.setId(NODE_ID);
        node.setFamilyId(FAMILY_ID);
        node.setName(name);
        node.setGender(1);
        node.setGeneration(generation);
        node.setBirthDate(LocalDate.of(1980, 3, 4));
        return node;
    }
}
