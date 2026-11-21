package com.mouhin.family.tree.common.constant;

/**
 * 操作日志类型常量（跨族链接审计）
 *
 * <p>{@code operation_log.operation_type} 列长 30，以下取值均可容纳；
 * 平台日志页签的 log-types 从表数据动态派生，无需字典迁移。</p>
 *
 * @author Family-Tree
 * @date 2026-11-20
 */
public final class OperationLogConsts {

    /**
     * 操作类型：建立跨族节点链接
     */
    public static final String OP_NODE_LINK = "NODE_LINK";

    /**
     * 操作类型：解除跨族节点链接
     */
    public static final String OP_NODE_UNLINK = "NODE_UNLINK";

    /**
     * 操作对象类型：跨族节点链接
     */
    public static final String TARGET_NODE_LINK = "node_link";

    private OperationLogConsts() {
    }
}
