package com.mouhin.family.tree.infrastructure.persistence.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * 用户维度分组计数结果对象
 * <p>
 * 承载"按 user_id 分组 COUNT"的窄查询结果，替代 {@code Map} 输出以符合 ORM 映射规约。
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Getter
@Setter
public class UserCountDO {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 该用户的记录数
     */
    private Long total;

    @Override
    public String toString() {
        return "UserCountDO{"
                + "userId=" + userId
                + ", total=" + total
                + '}';
    }
}
