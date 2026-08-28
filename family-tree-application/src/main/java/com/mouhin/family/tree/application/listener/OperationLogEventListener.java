package com.mouhin.family.tree.application.listener;

import com.mouhin.family.tree.application.service.OperationLogApplicationService;
import com.mouhin.family.tree.domain.event.FamilyCreatedEvent;
import com.mouhin.family.tree.domain.event.FamilyMessagePostedEvent;
import com.mouhin.family.tree.domain.event.FamilyOfferingMadeEvent;
import com.mouhin.family.tree.domain.event.MemberJoinedEvent;
import com.mouhin.family.tree.domain.event.NodeCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 操作日志事件监听器。
 * <p>
 * 监听领域事件并将操作记录写入操作日志，日志记录失败不影响主业务流程。
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
@Component
public class OperationLogEventListener {

    private static final Logger logger = LoggerFactory.getLogger(OperationLogEventListener.class);

    private final OperationLogApplicationService operationLogApplicationService;

    public OperationLogEventListener(OperationLogApplicationService operationLogApplicationService) {
        this.operationLogApplicationService = operationLogApplicationService;
    }

    /**
     * 节点创建时记录操作日志。
     *
     * @param event 节点创建事件
     */
    @TransactionalEventListener
    public void handleNodeCreated(NodeCreatedEvent event) {
        try {
            operationLogApplicationService.log(
                    event.userId(),
                    null,
                    "NODE_CREATE",
                    "新增族人: " + event.nodeName(),
                    "node",
                    event.nodeId(),
                    event.familyId(),
                    null
            );
        } catch (Exception e) {
            logger.error("Failed to log NodeCreatedEvent for family={}, nodeId={}",
                    event.familyId(), event.nodeId(), e);
        }
    }

    /**
     * 成员加入时记录操作日志。
     *
     * @param event 成员加入事件
     */
    @TransactionalEventListener
    public void handleMemberJoined(MemberJoinedEvent event) {
        try {
            operationLogApplicationService.log(
                    event.userId(),
                    null,
                    "MEMBER_JOIN",
                    "新成员加入家族",
                    "member",
                    event.userId(),
                    event.familyId(),
                    null
            );
        } catch (Exception e) {
            logger.error("Failed to log MemberJoinedEvent for family={}, userId={}",
                    event.familyId(), event.userId(), e);
        }
    }

    /**
     * 家族创建时记录操作日志。
     *
     * @param event 家族创建事件
     */
    @TransactionalEventListener
    public void handleFamilyCreated(FamilyCreatedEvent event) {
        try {
            operationLogApplicationService.log(
                    event.creatorUserId(),
                    null,
                    "FAMILY_CREATE",
                    "创建家族: " + event.familyName(),
                    "family",
                    event.familyId(),
                    event.familyId(),
                    null
            );
        } catch (Exception e) {
            logger.error("Failed to log FamilyCreatedEvent for family={}",
                    event.familyId(), e);
        }
    }

    /**
     * 家族留言发布时记录操作日志。
     *
     * @param event 家族留言发布事件
     */
    @TransactionalEventListener
    public void handleFamilyMessagePosted(FamilyMessagePostedEvent event) {
        try {
            operationLogApplicationService.log(
                    event.userId(),
                    null,
                    "MESSAGE_POST",
                    "发布家族留言",
                    "message",
                    event.messageId(),
                    event.familyId(),
                    null
            );
        } catch (Exception e) {
            logger.error("Failed to log FamilyMessagePostedEvent for family={}, messageId={}",
                    event.familyId(), event.messageId(), e);
        }
    }

    /**
     * 家族祭奠时记录操作日志。
     *
     * @param event 家族祭奠事件
     */
    @TransactionalEventListener
    public void handleFamilyOfferingMade(FamilyOfferingMadeEvent event) {
        try {
            operationLogApplicationService.log(
                    event.userId(),
                    null,
                    "OFFERING",
                    "为族人敬献祭品",
                    "offering",
                    event.nodeId(),
                    event.familyId(),
                    null
            );
        } catch (Exception e) {
            logger.error("Failed to log FamilyOfferingMadeEvent for family={}, nodeId={}",
                    event.familyId(), event.nodeId(), e);
        }
    }
}
