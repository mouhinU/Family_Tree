package com.mouhin.family.tree.application.listener;

import com.mouhin.family.tree.application.service.FamilyUserNodeApplicationService;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.event.NodeCreatedEvent;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 族谱节点账号开通监听器单元测试
 *
 * @author Family-Tree
 * @date 2026-11-05
 */
@ExtendWith(MockitoExtension.class)
class NodeAccountProvisioningEventListenerTest {

    private static final Long FAMILY_ID = 100L;
    private static final Long OPERATOR_ID = 1L;
    private static final Long NODE_ID = 40L;

    @Mock
    private FamilyNodeRepository familyNodeRepository;

    @Mock
    private FamilyUserNodeApplicationService familyUserNodeApplicationService;

    private NodeAccountProvisioningEventListener createListener() {
        return new NodeAccountProvisioningEventListener(familyNodeRepository, familyUserNodeApplicationService);
    }

    private static NodeCreatedEvent nodeCreatedEvent() {
        return NodeCreatedEvent.of(FAMILY_ID, NODE_ID, "张大爷", OPERATOR_ID, "testUser", "127.0.0.1");
    }

    private static FamilyNode livingNode(String name) {
        FamilyNode node = new FamilyNode();
        node.setId(NODE_ID);
        node.setFamilyId(FAMILY_ID);
        node.setName(name);
        return node;
    }

    @Test
    void handleNodeCreated_livingNode_provisionsAndBindsAccount() {
        FamilyNode node = livingNode("张大爷");
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node);

        createListener().handleNodeCreated(nodeCreatedEvent());

        verify(familyUserNodeApplicationService).provisionAndBindNodeAccount(node);
    }

    @Test
    void handleNodeCreated_deceasedNode_doesNotProvision() {
        FamilyNode node = livingNode("张大爷");
        node.setDeathDate(LocalDate.of(1990, 5, 1));
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node);

        createListener().handleNodeCreated(nodeCreatedEvent());

        verifyNoInteractions(familyUserNodeApplicationService);
    }

    @Test
    void handleNodeCreated_nodeMissing_doesNotProvision() {
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(null);

        createListener().handleNodeCreated(nodeCreatedEvent());

        verifyNoInteractions(familyUserNodeApplicationService);
    }

    @Test
    void handleNodeCreated_provisionFails_exceptionIsSwallowed() {
        // 开通失败等异常不得冒泡：账号开通失败不应影响已提交的族谱新增
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(livingNode("张大爷"));
        when(familyUserNodeApplicationService.provisionAndBindNodeAccount(any(FamilyNode.class)))
                .thenThrow(new BusinessException("用户名已存在"));

        assertDoesNotThrow(() -> createListener().handleNodeCreated(nodeCreatedEvent()));

        verify(familyUserNodeApplicationService).provisionAndBindNodeAccount(any(FamilyNode.class));
    }

    @Test
    void handleNodeCreated_eventCarriesNoAccountInfo_readsAuthoritativeNode() {
        // 事件里的姓名可能是入参原值，账号昵称应以库中节点为准
        FamilyNode node = livingNode("张大爷");
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node);

        createListener().handleNodeCreated(NodeCreatedEvent.of(
                FAMILY_ID, NODE_ID, "  张大爷  ", OPERATOR_ID, "testUser", "127.0.0.1"));

        verify(familyUserNodeApplicationService).provisionAndBindNodeAccount(node);
        verify(familyNodeRepository, never()).update(node);
    }
}