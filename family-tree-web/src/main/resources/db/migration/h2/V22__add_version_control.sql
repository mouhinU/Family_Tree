-- 节点修改历史表
CREATE TABLE IF NOT EXISTS family_node_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    node_id BIGINT NOT NULL,
    family_id BIGINT NOT NULL,
    operation_type VARCHAR(20) NOT NULL COMMENT '操作类型: CREATE/UPDATE/DELETE',
    operator_id BIGINT NOT NULL COMMENT '操作人ID',
    operator_name VARCHAR(100) COMMENT '操作人姓名',
    before_data TEXT COMMENT '修改前的数据(JSON格式)',
    after_data TEXT COMMENT '修改后的数据(JSON格式)',
    change_summary VARCHAR(500) COMMENT '变更摘要',
    ip_address VARCHAR(45) COMMENT '操作IP地址',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version_number INT NOT NULL COMMENT '版本号(从1开始递增)'
);

CREATE INDEX IF NOT EXISTS idx_node_history_node_id ON family_node_history(node_id);
CREATE INDEX IF NOT EXISTS idx_node_history_family_id ON family_node_history(family_id);
CREATE INDEX IF NOT EXISTS idx_node_history_operator ON family_node_history(operator_id);
CREATE INDEX IF NOT EXISTS idx_node_history_create_time ON family_node_history(create_time DESC);

-- 关系修改历史表
CREATE TABLE IF NOT EXISTS family_relation_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    relation_id BIGINT NOT NULL,
    family_id BIGINT NOT NULL,
    operation_type VARCHAR(20) NOT NULL COMMENT '操作类型: CREATE/UPDATE/DELETE',
    operator_id BIGINT NOT NULL COMMENT '操作人ID',
    operator_name VARCHAR(100) COMMENT '操作人姓名',
    before_data TEXT COMMENT '修改前的数据(JSON格式)',
    after_data TEXT COMMENT '修改后的数据(JSON格式)',
    change_summary VARCHAR(500) COMMENT '变更摘要',
    ip_address VARCHAR(45) COMMENT '操作IP地址',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version_number INT NOT NULL COMMENT '版本号(从1开始递增)'
);

CREATE INDEX IF NOT EXISTS idx_relation_history_relation_id ON family_relation_history(relation_id);
CREATE INDEX IF NOT EXISTS idx_relation_history_family_id ON family_relation_history(family_id);
CREATE INDEX IF NOT EXISTS idx_relation_history_operator ON family_relation_history(operator_id);
CREATE INDEX IF NOT EXISTS idx_relation_history_create_time ON family_relation_history(create_time DESC);

-- 家族快照表
CREATE TABLE IF NOT EXISTS family_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_id BIGINT NOT NULL,
    snapshot_name VARCHAR(200) NOT NULL COMMENT '快照名称',
    description VARCHAR(1000) COMMENT '快照描述',
    creator_id BIGINT NOT NULL COMMENT '创建人ID',
    creator_name VARCHAR(100) COMMENT '创建人姓名',
    node_count INT NOT NULL DEFAULT 0 COMMENT '节点数量',
    relation_count INT NOT NULL DEFAULT 0 COMMENT '关系数量',
    snapshot_data MEDIUMTEXT NOT NULL COMMENT '快照数据(JSON格式,包含所有节点和关系)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_snapshot_family_id ON family_snapshot(family_id);
CREATE INDEX IF NOT EXISTS idx_snapshot_creator ON family_snapshot(creator_id);
CREATE INDEX IF NOT EXISTS idx_snapshot_create_time ON family_snapshot(create_time DESC);
