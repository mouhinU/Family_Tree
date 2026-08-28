-- 节点修改历史表
CREATE TABLE IF NOT EXISTS family_node_history (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    node_id BIGINT UNSIGNED NOT NULL,
    family_id BIGINT UNSIGNED NOT NULL,
    operation_type VARCHAR(20) NOT NULL COMMENT '操作类型: CREATE/UPDATE/DELETE',
    operator_id BIGINT UNSIGNED NOT NULL COMMENT '操作人ID',
    operator_name VARCHAR(100) COMMENT '操作人姓名',
    before_data TEXT COMMENT '修改前的数据(JSON格式)',
    after_data TEXT COMMENT '修改后的数据(JSON格式)',
    change_summary VARCHAR(500) COMMENT '变更摘要',
    ip_address VARCHAR(45) COMMENT '操作IP地址',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version_number INT UNSIGNED NOT NULL COMMENT '版本号(从1开始递增)',
    INDEX idx_node_history_node_id (node_id),
    INDEX idx_node_history_family_id (family_id),
    INDEX idx_node_history_operator (operator_id),
    INDEX idx_node_history_create_time (create_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='节点修改历史表';

-- 关系修改历史表
CREATE TABLE IF NOT EXISTS family_relation_history (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    relation_id BIGINT UNSIGNED NOT NULL,
    family_id BIGINT UNSIGNED NOT NULL,
    operation_type VARCHAR(20) NOT NULL COMMENT '操作类型: CREATE/UPDATE/DELETE',
    operator_id BIGINT UNSIGNED NOT NULL COMMENT '操作人ID',
    operator_name VARCHAR(100) COMMENT '操作人姓名',
    before_data TEXT COMMENT '修改前的数据(JSON格式)',
    after_data TEXT COMMENT '修改后的数据(JSON格式)',
    change_summary VARCHAR(500) COMMENT '变更摘要',
    ip_address VARCHAR(45) COMMENT '操作IP地址',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version_number INT UNSIGNED NOT NULL COMMENT '版本号(从1开始递增)',
    INDEX idx_relation_history_relation_id (relation_id),
    INDEX idx_relation_history_family_id (family_id),
    INDEX idx_relation_history_operator (operator_id),
    INDEX idx_relation_history_create_time (create_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关系修改历史表';

-- 家族快照表
CREATE TABLE IF NOT EXISTS family_snapshot (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    family_id BIGINT UNSIGNED NOT NULL,
    snapshot_name VARCHAR(200) NOT NULL COMMENT '快照名称',
    description VARCHAR(1000) COMMENT '快照描述',
    creator_id BIGINT UNSIGNED NOT NULL COMMENT '创建人ID',
    creator_name VARCHAR(100) COMMENT '创建人姓名',
    node_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '节点数量',
    relation_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '关系数量',
    snapshot_data MEDIUMTEXT NOT NULL COMMENT '快照数据(JSON格式,包含所有节点和关系)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_snapshot_family_id (family_id),
    INDEX idx_snapshot_creator (creator_id),
    INDEX idx_snapshot_create_time (create_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家族快照表';
