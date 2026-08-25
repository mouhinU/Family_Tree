package com.mouhin.family.tree.domain.event;

import java.time.LocalDateTime;

/**
 * 领域事件标记接口
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
public interface DomainEvent {

    /**
     * 事件发生时间
     *
     * @return 事件发生时间
     */
    LocalDateTime occurredOn();
}
