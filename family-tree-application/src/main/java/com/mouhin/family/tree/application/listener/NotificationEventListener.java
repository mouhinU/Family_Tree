package com.mouhin.family.tree.application.listener;

import com.mouhin.family.tree.domain.entity.Family;
import com.mouhin.family.tree.domain.entity.Notification;
import com.mouhin.family.tree.domain.event.MemberJoinedEvent;
import com.mouhin.family.tree.domain.event.NodeCreatedEvent;
import com.mouhin.family.tree.domain.repository.FamilyRepository;
import com.mouhin.family.tree.domain.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

/**
 * 通知事件监听器。
 * <p>
 * 监听领域事件并为家族创建者生成通知，确保在事务提交后执行以避免回滚场景下产生无效通知。
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
@Component
public class NotificationEventListener {

    private static final Logger logger = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationRepository notificationRepository;
    private final FamilyRepository familyRepository;

    public NotificationEventListener(NotificationRepository notificationRepository,
                                     FamilyRepository familyRepository) {
        this.notificationRepository = notificationRepository;
        this.familyRepository = familyRepository;
    }

    /**
     * 成员加入时通知家族创建者。
     *
     * @param event 成员加入事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMemberJoined(MemberJoinedEvent event) {
        try {
            Family family = familyRepository.findById(event.familyId());
            if (family == null) {
                logger.warn("Cannot create notification: family not found, familyId={}", event.familyId());
                return;
            }

            Notification notification = new Notification();
            notification.setFamilyId(event.familyId());
            notification.setUserId(family.getCreatorId());
            notification.setTitle("新成员加入");
            notification.setContent("有新成员加入了您的家族");
            notification.setNotificationType("MEMBER_JOIN");
            notification.setRelatedId(event.userId());
            notification.setRead(false);
            notification.setCreateTime(LocalDateTime.now());
            notification.setUpdateTime(LocalDateTime.now());

            notificationRepository.save(notification);
            logger.info("Created MEMBER_JOIN notification for family={}, creator={}",
                    event.familyId(), family.getCreatorId());
        } catch (Exception e) {
            logger.error("Failed to create notification for MemberJoinedEvent, familyId={}",
                    event.familyId(), e);
        }
    }

    /**
     * 节点创建时通知家族创建者。
     *
     * @param event 节点创建事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNodeCreated(NodeCreatedEvent event) {
        try {
            Family family = familyRepository.findById(event.familyId());
            if (family == null) {
                logger.warn("Cannot create notification: family not found, familyId={}", event.familyId());
                return;
            }

            Notification notification = new Notification();
            notification.setFamilyId(event.familyId());
            notification.setUserId(family.getCreatorId());
            notification.setTitle("新增族人");
            notification.setContent("家族新增了成员: " + event.nodeName());
            notification.setNotificationType("NODE_CREATE");
            notification.setRelatedId(event.nodeId());
            notification.setRead(false);
            notification.setCreateTime(LocalDateTime.now());
            notification.setUpdateTime(LocalDateTime.now());

            notificationRepository.save(notification);
            logger.info("Created NODE_CREATE notification for family={}, creator={}",
                    event.familyId(), family.getCreatorId());
        } catch (Exception e) {
            logger.error("Failed to create notification for NodeCreatedEvent, familyId={}",
                    event.familyId(), e);
        }
    }
}
