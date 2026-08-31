package com.mouhin.family.tree.application.listener;

import com.mouhin.family.tree.application.service.OperationLogApplicationService;
import com.mouhin.family.tree.common.enums.ColorLabelEnum;
import com.mouhin.family.tree.common.enums.RelationTypeEnum;
import com.mouhin.family.tree.domain.event.FamilyCreatedEvent;
import com.mouhin.family.tree.domain.event.FamilyMessageDeletedEvent;
import com.mouhin.family.tree.domain.event.FamilyMessagePostedEvent;
import com.mouhin.family.tree.domain.event.FamilyOfferingMadeEvent;
import com.mouhin.family.tree.domain.event.GedcomImportedEvent;
import com.mouhin.family.tree.domain.event.GenerationUpdatedEvent;
import com.mouhin.family.tree.domain.event.MemberJoinedEvent;
import com.mouhin.family.tree.domain.event.MemberRemovedEvent;
import com.mouhin.family.tree.domain.event.MemberRoleChangedEvent;
import com.mouhin.family.tree.domain.event.NodeColorUpdatedEvent;
import com.mouhin.family.tree.domain.event.NodeCreatedEvent;
import com.mouhin.family.tree.domain.event.NodeDeletedEvent;
import com.mouhin.family.tree.domain.event.NodeUpdatedEvent;
import com.mouhin.family.tree.domain.event.RelationCreatedEvent;
import com.mouhin.family.tree.domain.event.RelationDeletedEvent;
import com.mouhin.family.tree.domain.event.RelationUpdatedEvent;
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
                    event.username(),
                    "NODE_CREATE",
                    "新增族人: " + event.nodeName(),
                    "node",
                    event.nodeId(),
                    event.familyId(),
                    event.ipAddress()
            );
        } catch (Exception e) {
            logger.error("Failed to log NodeCreatedEvent for family={}, nodeId={}",
                    event.familyId(), event.nodeId(), e);
        }
    }

    /**
     * 节点更新时记录操作日志。
     *
     * @param event 节点更新事件
     */
    @TransactionalEventListener
    public void handleNodeUpdated(NodeUpdatedEvent event) {
        try {
            operationLogApplicationService.log(
                    event.userId(),
                    event.username(),
                    "NODE_UPDATE",
                    "修改族人信息: " + event.nodeName(),
                    "node",
                    event.nodeId(),
                    event.familyId(),
                    event.ipAddress()
            );
        } catch (Exception e) {
            logger.error("Failed to log NodeUpdatedEvent for family={}, nodeId={}",
                    event.familyId(), event.nodeId(), e);
        }
    }

    /**
     * 节点删除时记录操作日志。
     *
     * @param event 节点删除事件
     */
    @TransactionalEventListener
    public void handleNodeDeleted(NodeDeletedEvent event) {
        try {
            operationLogApplicationService.log(
                    event.userId(),
                    event.username(),
                    "NODE_DELETE",
                    "删除族人: " + event.nodeName(),
                    "node",
                    event.nodeId(),
                    event.familyId(),
                    event.ipAddress()
            );
        } catch (Exception e) {
            logger.error("Failed to log NodeDeletedEvent for family={}, nodeId={}",
                    event.familyId(), event.nodeId(), e);
        }
    }

    /**
     * 节点颜色批量标注时记录操作日志。
     *
     * @param event 节点颜色更新事件
     */
    @TransactionalEventListener
    public void handleNodeColorUpdated(NodeColorUpdatedEvent event) {
        try {
            String colorDesc = ColorLabelEnum.fromCode(event.colorLabel()).getDescription();
            operationLogApplicationService.log(
                    event.userId(),
                    event.username(),
                    "NODE_COLOR",
                    "批量颜色标注: " + event.nodeCount() + " 个节点设为" + colorDesc,
                    "node",
                    null,
                    event.familyId(),
                    event.ipAddress()
            );
        } catch (Exception e) {
            logger.error("Failed to log NodeColorUpdatedEvent for family={}",
                    event.familyId(), e);
        }
    }

    /**
     * 关系创建时记录操作日志。
     *
     * @param event 关系创建事件
     */
    @TransactionalEventListener
    public void handleRelationCreated(RelationCreatedEvent event) {
        try {
            operationLogApplicationService.log(
                    event.userId(),
                    event.username(),
                    "RELATION_CREATE",
                    "新增" + relationTypeDesc(event.relationType()) + "关系: "
                            + event.fromNodeName() + " 与 " + event.toNodeName(),
                    "relation",
                    event.relationId(),
                    event.familyId(),
                    event.ipAddress()
            );
        } catch (Exception e) {
            logger.error("Failed to log RelationCreatedEvent for family={}, relationId={}",
                    event.familyId(), event.relationId(), e);
        }
    }

    /**
     * 关系更新时记录操作日志。
     *
     * @param event 关系更新事件
     */
    @TransactionalEventListener
    public void handleRelationUpdated(RelationUpdatedEvent event) {
        try {
            operationLogApplicationService.log(
                    event.userId(),
                    event.username(),
                    "RELATION_UPDATE",
                    "修改" + relationTypeDesc(event.relationType()) + "关系: "
                            + event.fromNodeName() + " 与 " + event.toNodeName(),
                    "relation",
                    event.relationId(),
                    event.familyId(),
                    event.ipAddress()
            );
        } catch (Exception e) {
            logger.error("Failed to log RelationUpdatedEvent for family={}, relationId={}",
                    event.familyId(), event.relationId(), e);
        }
    }

    /**
     * 关系删除时记录操作日志。
     *
     * @param event 关系删除事件
     */
    @TransactionalEventListener
    public void handleRelationDeleted(RelationDeletedEvent event) {
        try {
            operationLogApplicationService.log(
                    event.userId(),
                    event.username(),
                    "RELATION_DELETE",
                    "删除" + relationTypeDesc(event.relationType()) + "关系: "
                            + event.fromNodeName() + " 与 " + event.toNodeName(),
                    "relation",
                    event.relationId(),
                    event.familyId(),
                    event.ipAddress()
            );
        } catch (Exception e) {
            logger.error("Failed to log RelationDeletedEvent for family={}, relationId={}",
                    event.familyId(), event.relationId(), e);
        }
    }

    /**
     * GEDCOM 导入时记录操作日志。
     *
     * @param event GEDCOM 导入事件
     */
    @TransactionalEventListener
    public void handleGedcomImported(GedcomImportedEvent event) {
        try {
            String mode = event.overwrite() ? "覆盖导入" : "追加导入";
            operationLogApplicationService.log(
                    event.userId(),
                    event.username(),
                    "GEDCOM_IMPORT",
                    mode + "GEDCOM: " + event.nodeCount() + " 个节点, "
                            + event.relationCount() + " 条关系",
                    "gedcom",
                    null,
                    event.familyId(),
                    event.ipAddress()
            );
        } catch (Exception e) {
            logger.error("Failed to log GedcomImportedEvent for family={}",
                    event.familyId(), e);
        }
    }

    /**
     * 成员被移除时记录操作日志。
     *
     * @param event 成员移除事件
     */
    @TransactionalEventListener
    public void handleMemberRemoved(MemberRemovedEvent event) {
        try {
            operationLogApplicationService.log(
                    event.userId(),
                    null,
                    "MEMBER_REMOVE",
                    "成员被移除家族",
                    "member",
                    event.userId(),
                    event.familyId(),
                    null
            );
        } catch (Exception e) {
            logger.error("Failed to log MemberRemovedEvent for family={}, userId={}",
                    event.familyId(), event.userId(), e);
        }
    }

    /**
     * 成员角色变更时记录操作日志。
     *
     * @param event 成员角色变更事件
     */
    @TransactionalEventListener
    public void handleMemberRoleChanged(MemberRoleChangedEvent event) {
        try {
            operationLogApplicationService.log(
                    event.userId(),
                    null,
                    "MEMBER_ROLE_CHANGE",
                    "成员角色变更为: " + event.newRole(),
                    "member",
                    event.userId(),
                    event.familyId(),
                    null
            );
        } catch (Exception e) {
            logger.error("Failed to log MemberRoleChangedEvent for family={}, userId={}",
                    event.familyId(), event.userId(), e);
        }
    }

    /**
     * 家族留言删除时记录操作日志。
     *
     * @param event 家族留言删除事件
     */
    @TransactionalEventListener
    public void handleFamilyMessageDeleted(FamilyMessageDeletedEvent event) {
        try {
            operationLogApplicationService.log(
                    event.userId(),
                    null,
                    "MESSAGE_DELETE",
                    "删除家族留言",
                    "message",
                    event.messageId(),
                    event.familyId(),
                    null
            );
        } catch (Exception e) {
            logger.error("Failed to log FamilyMessageDeletedEvent for family={}, messageId={}",
                    event.familyId(), event.messageId(), e);
        }
    }

    /**
     * 辈分排次更新时记录操作日志。
     *
     * @param event 辈分排次更新事件
     */
    @TransactionalEventListener
    public void handleGenerationUpdated(GenerationUpdatedEvent event) {
        try {
            operationLogApplicationService.log(
                    null,
                    null,
                    "GENERATION_UPDATE",
                    "更新辈分排次",
                    "generation",
                    null,
                    event.familyId(),
                    null
            );
        } catch (Exception e) {
            logger.error("Failed to log GenerationUpdatedEvent for family={}",
                    event.familyId(), e);
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

    /**
     * 安全解析关系类型中文描述，非法或空类型返回"未知"。
     *
     * @param relationType 关系类型编码，可为 null
     * @return 关系类型中文描述
     */
    private String relationTypeDesc(Integer relationType) {
        if (relationType == null) {
            return "未知";
        }
        try {
            return RelationTypeEnum.fromCode(relationType).getDescription();
        } catch (IllegalArgumentException e) {
            return "未知";
        }
    }
}
