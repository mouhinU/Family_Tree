-- 家族时间线：记录出生、去世、结婚、家族活动等事件
-- 通过唯一约束 (source_type, source_id, event_type) 防止重复记录

CREATE TABLE IF NOT EXISTS family_timeline (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    family_id    BIGINT UNSIGNED NOT NULL              COMMENT '家族ID',
    source_type  VARCHAR(20)     NOT NULL              COMMENT '来源类型：NODE/RELATION/EVENT',
    source_id    BIGINT UNSIGNED NOT NULL              COMMENT '来源ID（节点ID/关系ID/活动ID）',
    event_type   VARCHAR(20)     NOT NULL              COMMENT '事件类型：BIRTH/DEATH/MARRIAGE/ACTIVITY',
    event_date   DATE            NOT NULL              COMMENT '事件日期',
    description  VARCHAR(200)    NOT NULL              COMMENT '事件描述',
    node_id      BIGINT UNSIGNED DEFAULT NULL          COMMENT '关联节点ID（出生/去世时填写）',
    relation_id  BIGINT UNSIGNED DEFAULT NULL          COMMENT '关联关系ID（结婚时填写）',
    is_new       TINYINT         NOT NULL DEFAULT 1    COMMENT '是否新增数据：1新增 0修改',
    create_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_timeline_source_event (source_type, source_id, event_type),
    INDEX idx_timeline_family_date (family_id, event_date DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='家族时间线表';
