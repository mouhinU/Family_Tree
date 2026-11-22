package com.mouhin.family.tree.application.listener;

import com.mouhin.family.tree.application.service.FamilyUserNodeApplicationService;
import com.mouhin.family.tree.domain.event.UserNodeBoundEvent;
import com.mouhin.family.tree.domain.event.UserNodeUnboundEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * 「标记为我」跨族亲族链接监听器单元测试。
 * 覆盖：绑定/解绑事件触发链接同步、同步异常仅记录不上抛。
 *
 * @author Family-Tree
 * @date 2026-11-21
 */
@ExtendWith(MockitoExtension.class)
class AccountCrossFamilyLinkEventListenerTest {

    private static final Long USER_ID = 1L;
    private static final Long FAMILY_ID = 100L;
    private static final Long NODE_ID = 7L;

    @Mock
    private FamilyUserNodeApplicationService familyUserNodeApplicationService;

    @InjectMocks
    private AccountCrossFamilyLinkEventListener listener;

    @Test
    void handleUserNodeBound_syncsAccountLinks() {
        listener.handleUserNodeBound(UserNodeBoundEvent.of(FAMILY_ID, USER_ID, NODE_ID));

        verify(familyUserNodeApplicationService).syncAccountLinksForUser(USER_ID, NODE_ID);
    }

    @Test
    void handleUserNodeUnbound_syncsAccountLinks() {
        listener.handleUserNodeUnbound(UserNodeUnboundEvent.of(FAMILY_ID, USER_ID, NODE_ID));

        verify(familyUserNodeApplicationService).syncAccountLinksForUser(USER_ID, NODE_ID);
    }

    @Test
    void handleUserNodeBound_syncFailure_swallowedWithoutThrow() {
        doThrow(new RuntimeException("db down"))
                .when(familyUserNodeApplicationService).syncAccountLinksForUser(USER_ID, NODE_ID);

        assertDoesNotThrow(() ->
                listener.handleUserNodeBound(UserNodeBoundEvent.of(FAMILY_ID, USER_ID, NODE_ID)));
    }

    @Test
    void handleUserNodeUnbound_syncFailure_swallowedWithoutThrow() {
        doThrow(new RuntimeException("db down"))
                .when(familyUserNodeApplicationService).syncAccountLinksForUser(USER_ID, NODE_ID);

        assertDoesNotThrow(() ->
                listener.handleUserNodeUnbound(UserNodeUnboundEvent.of(FAMILY_ID, USER_ID, NODE_ID)));
    }
}
