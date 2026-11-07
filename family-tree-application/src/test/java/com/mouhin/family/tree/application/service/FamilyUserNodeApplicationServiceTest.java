package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.constant.AccountConsts;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyUserNode;
import com.mouhin.family.tree.domain.entity.User;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.FamilyUserNodeRepository;
import com.mouhin.family.tree.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户-家族-族谱节点绑定应用服务单元测试。
 * 覆盖：标记/取消标记、跨家族节点校验、改名同步昵称、被绑定节点删除拦截。
 *
 * @author Family-Tree
 * @date 2026-11-05
 */
@ExtendWith(MockitoExtension.class)
class FamilyUserNodeApplicationServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long FAMILY_ID = 100L;
    private static final Long NODE_ID = 7L;

    @Mock
    private FamilyUserNodeRepository familyUserNodeRepository;

    @Mock
    private FamilyNodeRepository familyNodeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserAuthApplicationService userAuthApplicationService;

    @InjectMocks
    private FamilyUserNodeApplicationService familyUserNodeApplicationService;

    // ========== 标记为我 ==========

    @Test
    void bindMyNode_noExistingBinding_savesNewBinding() {
        when(familyUserNodeRepository.findByFamilyIdAndUserId(FAMILY_ID, USER_ID)).thenReturn(null);
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node(NODE_ID, FAMILY_ID));

        familyUserNodeApplicationService.bindMyNode(FAMILY_ID, USER_ID, NODE_ID);

        ArgumentCaptor<FamilyUserNode> captor = ArgumentCaptor.forClass(FamilyUserNode.class);
        verify(familyUserNodeRepository).save(captor.capture());
        assertEquals(FAMILY_ID, captor.getValue().getFamilyId());
        assertEquals(USER_ID, captor.getValue().getUserId());
        assertEquals(NODE_ID, captor.getValue().getNodeId());
    }

    @Test
    void bindMyNode_existingBinding_repointsToNewNode() {
        FamilyUserNode existing = binding(5L, NODE_ID);
        when(familyUserNodeRepository.findByFamilyIdAndUserId(FAMILY_ID, USER_ID)).thenReturn(existing);
        when(familyNodeRepository.findById(8L)).thenReturn(node(8L, FAMILY_ID));

        familyUserNodeApplicationService.bindMyNode(FAMILY_ID, USER_ID, 8L);

        ArgumentCaptor<FamilyUserNode> captor = ArgumentCaptor.forClass(FamilyUserNode.class);
        verify(familyUserNodeRepository).update(captor.capture());
        assertEquals(Long.valueOf(8L), captor.getValue().getNodeId());
        verify(familyUserNodeRepository, never()).save(any());
    }

    @Test
    void bindMyNode_sameNodeTwice_writesNothing() {
        FamilyUserNode existing = binding(5L, NODE_ID);
        when(familyUserNodeRepository.findByFamilyIdAndUserId(FAMILY_ID, USER_ID)).thenReturn(existing);
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node(NODE_ID, FAMILY_ID));

        familyUserNodeApplicationService.bindMyNode(FAMILY_ID, USER_ID, NODE_ID);

        verify(familyUserNodeRepository, never()).save(any());
        verify(familyUserNodeRepository, never()).update(any());
    }

    @Test
    void bindMyNode_nullNodeId_removesBinding() {
        when(familyUserNodeRepository.findByFamilyIdAndUserId(FAMILY_ID, USER_ID))
                .thenReturn(binding(5L, NODE_ID));

        familyUserNodeApplicationService.bindMyNode(FAMILY_ID, USER_ID, null);

        verify(familyUserNodeRepository).removeByFamilyIdAndUserId(FAMILY_ID, USER_ID);
    }

    @Test
    void bindMyNode_notBoundAndUnbindRequest_writesNothing() {
        when(familyUserNodeRepository.findByFamilyIdAndUserId(FAMILY_ID, USER_ID)).thenReturn(null);

        familyUserNodeApplicationService.bindMyNode(FAMILY_ID, USER_ID, null);

        verify(familyUserNodeRepository, never()).removeByFamilyIdAndUserId(FAMILY_ID, USER_ID);
    }

    @Test
    void bindMyNode_withoutFamily_throws() {
        assertThrows(BusinessException.class,
                () -> familyUserNodeApplicationService.bindMyNode(null, USER_ID, NODE_ID));
    }

    @Test
    void bindMyNode_nodeBelongsToOtherFamily_throws() {
        when(familyUserNodeRepository.findByFamilyIdAndUserId(FAMILY_ID, USER_ID)).thenReturn(null);
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node(NODE_ID, 999L));

        assertThrows(BusinessException.class,
                () -> familyUserNodeApplicationService.bindMyNode(FAMILY_ID, USER_ID, NODE_ID));

        verify(familyUserNodeRepository, never()).save(any());
    }

    @Test
    void bindMyNode_nodeNotFound_throws() {
        when(familyUserNodeRepository.findByFamilyIdAndUserId(FAMILY_ID, USER_ID)).thenReturn(null);
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> familyUserNodeApplicationService.bindMyNode(FAMILY_ID, USER_ID, NODE_ID));
    }

    // ========== 绑定查询 ==========

    @Test
    void getBoundNodeId_returnsNodeIdWhenBound() {
        when(familyUserNodeRepository.findByFamilyIdAndUserId(FAMILY_ID, USER_ID))
                .thenReturn(binding(5L, NODE_ID));

        assertEquals(NODE_ID, familyUserNodeApplicationService.getBoundNodeId(FAMILY_ID, USER_ID));
    }

    @Test
    void getBoundNodeId_returnsNullWhenUnbound() {
        when(familyUserNodeRepository.findByFamilyIdAndUserId(FAMILY_ID, USER_ID)).thenReturn(null);

        assertNull(familyUserNodeApplicationService.getBoundNodeId(FAMILY_ID, USER_ID));
    }

    @Test
    void getBoundNodeId_missingFamilyId_returnsNullWithoutQuery() {
        assertNull(familyUserNodeApplicationService.getBoundNodeId(null, USER_ID));

        verify(familyUserNodeRepository, never()).findByFamilyIdAndUserId(any(), any());
    }

    @Test
    void listBindingsByFamily_buildsUserIdToNodeIdMap() {
        when(familyUserNodeRepository.findByFamilyId(FAMILY_ID))
                .thenReturn(List.of(userBinding(USER_ID, NODE_ID), userBinding(2L, 8L)));

        Map<Long, Long> bindings = familyUserNodeApplicationService.listBindingsByFamily(FAMILY_ID);

        assertEquals(2, bindings.size());
        assertEquals(NODE_ID, bindings.get(USER_ID));
        assertEquals(Long.valueOf(8L), bindings.get(2L));
    }

    // ========== 改名同步昵称 ==========

    @Test
    void syncNicknameFromNode_updatesBoundUserNickname() {
        when(familyUserNodeRepository.findByFamilyIdAndNodeId(FAMILY_ID, NODE_ID))
                .thenReturn(binding(5L, NODE_ID));
        User user = new User();
        user.setId(USER_ID);
        user.setNickname("旧名");
        when(userRepository.findById(USER_ID)).thenReturn(user);

        familyUserNodeApplicationService.syncNicknameFromNode(FAMILY_ID, NODE_ID, "新名");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).update(captor.capture());
        assertEquals("新名", captor.getValue().getNickname());
    }

    @Test
    void syncNicknameFromNode_noBinding_touchesNoUser() {
        when(familyUserNodeRepository.findByFamilyIdAndNodeId(FAMILY_ID, NODE_ID)).thenReturn(null);

        familyUserNodeApplicationService.syncNicknameFromNode(FAMILY_ID, NODE_ID, "新名");

        verify(userRepository, never()).findById(any());
        verify(userRepository, never()).update(any());
    }

    @Test
    void syncNicknameFromNode_nameUnchanged_skipsUpdate() {
        when(familyUserNodeRepository.findByFamilyIdAndNodeId(FAMILY_ID, NODE_ID))
                .thenReturn(binding(5L, NODE_ID));
        User user = new User();
        user.setId(USER_ID);
        user.setNickname("同名");
        when(userRepository.findById(USER_ID)).thenReturn(user);

        familyUserNodeApplicationService.syncNicknameFromNode(FAMILY_ID, NODE_ID, "同名");

        verify(userRepository, never()).update(any());
    }

    @Test
    void syncNicknameFromNode_blankName_skipsUpdate() {
        familyUserNodeApplicationService.syncNicknameFromNode(FAMILY_ID, NODE_ID, "  ");

        verify(familyUserNodeRepository, never()).findByFamilyIdAndNodeId(any(), any());
    }

    // ========== 删除处置（区分自动绑定与真人标记） ==========

    @Test
    void prepareForNodeDeletion_freeNode_passes() {
        when(familyUserNodeRepository.findByFamilyIdAndNodeId(FAMILY_ID, NODE_ID)).thenReturn(null);

        familyUserNodeApplicationService.prepareForNodeDeletion(FAMILY_ID, NODE_ID);

        verify(userRepository, never()).deleteById(any());
    }

    @Test
    void prepareForNodeDeletion_realUserBinding_throwsAndKeepsAccount() {
        when(familyUserNodeRepository.findByFamilyIdAndNodeId(FAMILY_ID, NODE_ID))
                .thenReturn(userBinding(USER_ID, NODE_ID));
        when(userRepository.findById(USER_ID)).thenReturn(user(USER_ID, "zhangsan"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> familyUserNodeApplicationService.prepareForNodeDeletion(FAMILY_ID, NODE_ID));
        assertTrue(ex.getMessage().contains("解除标记"));
        verify(userRepository, never()).deleteById(any());
        verify(familyUserNodeRepository, never()).removeByFamilyIdAndUserId(any(), any());
    }

    @Test
    void prepareForNodeDeletion_autoAccountBinding_reclaimsAndPasses() {
        Long autoUserId = 90L;
        when(familyUserNodeRepository.findByFamilyIdAndNodeId(FAMILY_ID, NODE_ID))
                .thenReturn(userBinding(autoUserId, NODE_ID));
        when(userRepository.findById(autoUserId))
                .thenReturn(user(autoUserId, AccountConsts.NODE_ACCOUNT_USERNAME_PREFIX + NODE_ID));

        familyUserNodeApplicationService.prepareForNodeDeletion(FAMILY_ID, NODE_ID);

        verify(familyUserNodeRepository).removeByFamilyIdAndUserId(FAMILY_ID, autoUserId);
        verify(userRepository).deleteById(autoUserId);
    }

    // ========== 开通账号即绑定 ==========

    @Test
    void provisionAndBindNodeAccount_newAccount_createsAccountAndBinding() {
        Long autoUserId = 90L;
        String username = AccountConsts.NODE_ACCOUNT_USERNAME_PREFIX + NODE_ID;
        FamilyNode node = node(NODE_ID, FAMILY_ID);
        node.setName("张大爷");
        when(userRepository.findByUsername(username)).thenReturn(null);
        when(userAuthApplicationService.provisionAccount(
                username, "张大爷", AccountConsts.NODE_ACCOUNT_INITIAL_PASSWORD)).thenReturn(autoUserId);
        when(familyUserNodeRepository.findByFamilyIdAndUserId(FAMILY_ID, autoUserId)).thenReturn(null);
        when(familyUserNodeRepository.findByFamilyIdAndNodeId(FAMILY_ID, NODE_ID)).thenReturn(null);

        Long result = familyUserNodeApplicationService.provisionAndBindNodeAccount(node);

        assertEquals(autoUserId, result);
        ArgumentCaptor<FamilyUserNode> captor = ArgumentCaptor.forClass(FamilyUserNode.class);
        verify(familyUserNodeRepository).save(captor.capture());
        assertEquals(FAMILY_ID, captor.getValue().getFamilyId());
        assertEquals(autoUserId, captor.getValue().getUserId());
        assertEquals(NODE_ID, captor.getValue().getNodeId());
    }

    @Test
    void provisionAndBindNodeAccount_existingAccount_reusesWithoutProvisioning() {
        Long autoUserId = 90L;
        String username = AccountConsts.NODE_ACCOUNT_USERNAME_PREFIX + NODE_ID;
        FamilyNode node = node(NODE_ID, FAMILY_ID);
        node.setName("张大爷");
        when(userRepository.findByUsername(username)).thenReturn(user(autoUserId, username));
        when(familyUserNodeRepository.findByFamilyIdAndUserId(FAMILY_ID, autoUserId)).thenReturn(null);
        when(familyUserNodeRepository.findByFamilyIdAndNodeId(FAMILY_ID, NODE_ID)).thenReturn(null);

        Long result = familyUserNodeApplicationService.provisionAndBindNodeAccount(node);

        assertEquals(autoUserId, result);
        verify(userAuthApplicationService, never()).provisionAccount(any(), any(), any());
        verify(familyUserNodeRepository).save(any());
    }

    @Test
    void provisionAndBindNodeAccount_nodeClaimedByRealUser_skipsBinding() {
        Long autoUserId = 90L;
        String username = AccountConsts.NODE_ACCOUNT_USERNAME_PREFIX + NODE_ID;
        FamilyNode node = node(NODE_ID, FAMILY_ID);
        node.setName("张大爷");
        when(userRepository.findByUsername(username)).thenReturn(null);
        when(userAuthApplicationService.provisionAccount(
                username, "张大爷", AccountConsts.NODE_ACCOUNT_INITIAL_PASSWORD)).thenReturn(autoUserId);
        when(familyUserNodeRepository.findByFamilyIdAndUserId(FAMILY_ID, autoUserId)).thenReturn(null);
        when(familyUserNodeRepository.findByFamilyIdAndNodeId(FAMILY_ID, NODE_ID))
                .thenReturn(userBinding(USER_ID, NODE_ID));

        familyUserNodeApplicationService.provisionAndBindNodeAccount(node);

        verify(familyUserNodeRepository, never()).save(any());
    }

    // ========== 真人标记回收自动账号 ==========

    @Test
    void bindMyNode_realUserMarksNode_reclaimsAutoAccount() {
        Long autoUserId = 90L;
        when(familyUserNodeRepository.findByFamilyIdAndUserId(FAMILY_ID, USER_ID)).thenReturn(null);
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node(NODE_ID, FAMILY_ID));
        when(familyUserNodeRepository.findByFamilyIdAndNodeId(FAMILY_ID, NODE_ID))
                .thenReturn(userBinding(autoUserId, NODE_ID));
        when(userRepository.findById(autoUserId))
                .thenReturn(user(autoUserId, AccountConsts.NODE_ACCOUNT_USERNAME_PREFIX + NODE_ID));

        familyUserNodeApplicationService.bindMyNode(FAMILY_ID, USER_ID, NODE_ID);

        verify(familyUserNodeRepository).removeByFamilyIdAndUserId(FAMILY_ID, autoUserId);
        verify(userRepository).deleteById(autoUserId);
        ArgumentCaptor<FamilyUserNode> captor = ArgumentCaptor.forClass(FamilyUserNode.class);
        verify(familyUserNodeRepository).save(captor.capture());
        assertEquals(USER_ID, captor.getValue().getUserId());
        assertEquals(NODE_ID, captor.getValue().getNodeId());
    }

    // ========== 辅助方法 ==========

    private FamilyNode node(Long id, Long familyId) {
        FamilyNode node = new FamilyNode();
        node.setId(id);
        node.setFamilyId(familyId);
        return node;
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private FamilyUserNode binding(Long id, Long nodeId) {
        FamilyUserNode binding = new FamilyUserNode();
        binding.setId(id);
        binding.setFamilyId(FAMILY_ID);
        binding.setUserId(USER_ID);
        binding.setNodeId(nodeId);
        return binding;
    }

    private FamilyUserNode userBinding(Long userId, Long nodeId) {
        FamilyUserNode binding = new FamilyUserNode();
        binding.setId(userId);
        binding.setFamilyId(FAMILY_ID);
        binding.setUserId(userId);
        binding.setNodeId(nodeId);
        return binding;
    }
}
