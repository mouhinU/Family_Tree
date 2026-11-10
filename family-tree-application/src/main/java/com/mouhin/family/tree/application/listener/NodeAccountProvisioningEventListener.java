package com.mouhin.family.tree.application.listener;

import com.mouhin.family.tree.application.service.FamilyUserNodeApplicationService;
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
 * 监听节点新增事件，为在世节点自动开通登录账号（用户名优先取姓名、重名/脱敏兜底 fp_{节点ID}）并一并建立
 * 用户-家族-节点绑定关系，确保在事务提交后执行，避免节点回滚后残留孤儿账号。
 * 已故节点不开通；账号开通与绑定在同一事务内原子完成。
 *
 * @author Family-Tree
 * @date 2026-11-05
 */
@Component
public class NodeAccountProvisioningEventListener {

    private static final Logger logger = LoggerFactory.getLogger(NodeAccountProvisioningEventListener.class);

    private final FamilyNodeRepository familyNodeRepository;
    private final FamilyUserNodeApplicationService familyUserNodeApplicationService;

    public NodeAccountProvisioningEventListener(FamilyNodeRepository familyNodeRepository,
                                                FamilyUserNodeApplicationService familyUserNodeApplicationService) {
        this.familyNodeRepository = familyNodeRepository;
        this.familyUserNodeApplicationService = familyUserNodeApplicationService;
    }

    /**
     * 节点新增后为在世节点开通登录账号并建立绑定。
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

            Long userId = familyUserNodeApplicationService.provisionAndBindNodeAccount(node);
            logger.info("Account provisioned and bound for family node: userId={}, familyId={}, nodeId={}",
                    userId, event.familyId(), node.getId());
        } catch (Exception e) {
            logger.error("Failed to provision account for NodeCreatedEvent, familyId={}, nodeId={}",
                    event.familyId(), event.nodeId(), e);
        }
    }
}