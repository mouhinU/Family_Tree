package com.mouhin.family.tree.application.listener;

import com.mouhin.family.tree.application.service.FamilyUserNodeApplicationService;
import com.mouhin.family.tree.domain.event.UserNodeBoundEvent;
import com.mouhin.family.tree.domain.event.UserNodeUnboundEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 「标记为我」跨族亲族链接监听器。
 * <p>
 * 监听用户节点绑定/解绑事件，在绑定事务提交后按账号当前全部跨族绑定重算 source=ACCOUNT
 * 的跨族链接（自动成链/核销候选/释放失效链），避免事务回滚后残留脏链接；
 * 同步失败只记录日志，不影响绑定主流程。
 *
 * @author Family-Tree
 * @date 2026-11-21
 */
@Component
public class AccountCrossFamilyLinkEventListener {

    private static final Logger logger = LoggerFactory.getLogger(AccountCrossFamilyLinkEventListener.class);

    private final FamilyUserNodeApplicationService familyUserNodeApplicationService;

    public AccountCrossFamilyLinkEventListener(FamilyUserNodeApplicationService familyUserNodeApplicationService) {
        this.familyUserNodeApplicationService = familyUserNodeApplicationService;
    }

    /**
     * 绑定提交后同步该账号的跨族 ACCOUNT 链接。
     *
     * @param event 用户节点绑定事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserNodeBound(UserNodeBoundEvent event) {
        syncLinks(event.userId(), event.nodeId(), event.familyId(), "bound");
    }

    /**
     * 解绑提交后同步该账号的跨族 ACCOUNT 链接（失效链自动释放）。
     *
     * @param event 用户节点解绑事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserNodeUnbound(UserNodeUnboundEvent event) {
        syncLinks(event.userId(), event.nodeId(), event.familyId(), "unbound");
    }

    private void syncLinks(Long userId, Long nodeId, Long familyId, String action) {
        try {
            familyUserNodeApplicationService.syncAccountLinksForUser(userId, nodeId);
            logger.info("Account cross-family links synced after user node {}: userId={}, familyId={}, nodeId={}",
                    action, userId, familyId, nodeId);
        } catch (Exception e) {
            logger.error("Failed to sync account cross-family links after user node {}, userId={}, familyId={}, nodeId={}",
                    action, userId, familyId, nodeId, e);
        }
    }
}
