package com.mouhin.family.tree.application.listener;

import com.mouhin.family.tree.application.service.FamilyTreeApplicationService;
import com.mouhin.family.tree.domain.event.FamilyTreeUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 族谱树缓存失效监听器。
 * <p>
 * 监听 {@link FamilyTreeUpdatedEvent}，在事务提交后失效对应家族的族谱树缓存。
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
@Component
public class FamilyTreeCacheEventListener {

    private static final Logger logger = LoggerFactory.getLogger(FamilyTreeCacheEventListener.class);

    private final FamilyTreeApplicationService familyTreeApplicationService;

    public FamilyTreeCacheEventListener(FamilyTreeApplicationService familyTreeApplicationService) {
        this.familyTreeApplicationService = familyTreeApplicationService;
    }

    /**
     * 族谱树变更后失效缓存。
     * <p>
     * 使用 {@link TransactionalEventListener} 确保在事务提交后执行，
     * 避免并发读在提交前用旧数据回填缓存。
     *
     * @param event 族谱树更新事件
     */
    @TransactionalEventListener
    public void handleFamilyTreeUpdated(FamilyTreeUpdatedEvent event) {
        logger.debug("Received FamilyTreeUpdatedEvent for family={}, evicting cache", event.familyId());
        familyTreeApplicationService.evictFamilyTree(event.familyId());
    }
}
