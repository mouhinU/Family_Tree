package com.mouhin.family.tree.application.listener;

import com.mouhin.family.tree.application.service.UserAuthApplicationService;
import com.mouhin.family.tree.common.constant.AccountConsts;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.event.NodeCreatedEvent;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 族谱节点账号开通监听器。
 * <p>
 * 监听节点新增事件，为在世节点自动开通登录账号（用户名为 fp_{节点ID}，初始密码见
 * {@link AccountConsts#NODE_ACCOUNT_INITIAL_PASSWORD}），确保在事务提交后执行，
 * 避免节点回滚后残留孤儿账号。已故节点不开通；账号不自动关联家族，也不占用节点绑定。
 *
 * @author Family-Tree
 * @date 2026-11-05
 */
@Component
public class NodeAccountProvisioningEventListener {

    private static final Logger logger = LoggerFactory.getLogger(NodeAccountProvisioningEventListener.class);

    private final FamilyNodeRepository familyNodeRepository;
    private final UserAuthApplicationService userAuthApplicationService;

    public NodeAccountProvisioningEventListener(FamilyNodeRepository familyNodeRepository,
                                                UserAuthApplicationService userAuthApplicationService) {
        this.familyNodeRepository = familyNodeRepository;
        this.userAuthApplicationService = userAuthApplicationService;
    }

    /**
     * 节点新增后为在世节点开通登录账号。
     *
     * @param event 节点创建事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNodeCreated(NodeCreatedEvent event) {
        try {
            FamilyNode node = familyNodeRepository.findById(event.nodeId());
            if (node == null) {
                logger.warn("Cannot provision account: node not found, familyId={}, nodeId={}",
                        event.familyId(), event.nodeId());
                return;
            }
            if (node.isDeceased()) {
                logger.info("Skip account provisioning for deceased node, familyId={}, nodeId={}",
                        event.familyId(), node.getId());
                return;
            }

            String username = AccountConsts.NODE_ACCOUNT_USERNAME_PREFIX + node.getId();
            Long userId = userAuthApplicationService.provisionAccount(
                    username, node.getName(), AccountConsts.NODE_ACCOUNT_INITIAL_PASSWORD);
            logger.info("Account provisioned for family node: userId={}, username={}, "
                            + "familyId={}, nodeId={}", userId, username, event.familyId(), node.getId());
        } catch (Exception e) {
            logger.error("Failed to provision account for NodeCreatedEvent, familyId={}, nodeId={}",
                    event.familyId(), event.nodeId(), e);
        }
    }
}
