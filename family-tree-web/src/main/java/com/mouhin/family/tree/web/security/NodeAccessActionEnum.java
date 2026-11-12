package com.mouhin.family.tree.web.security;

/**
 * 节点访问动作类型，用于 {@code @RequireNodeAccess} 区分"查看详情"与"发起操作"两种收口语义。
 *
 * @author Family-Tree
 * @date 2026-11-12
 */
public enum NodeAccessActionEnum {

    /**
     * 查看详情：非管理员仅可查看直系可见集合内的节点详情
     */
    VIEW,

    /**
     * 发起操作：非管理员仅可对其直系可见集合内的节点执行增删改等写操作
     */
    OPERATE
}
