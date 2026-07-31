-- 族谱管理系统初始化脚本（MySQL）
-- V1__init_schema.sql

CREATE TABLE IF NOT EXISTS sys_user (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)     NOT NULL,
    password_hash VARCHAR(128)    NOT NULL,
    nickname      VARCHAR(50),
    create_time   DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

CREATE TABLE IF NOT EXISTS family_node (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL,
    name        VARCHAR(50)     NOT NULL,
    gender      INT             DEFAULT 0 COMMENT '0-未知 1-男 2-女',
    birth_date  DATE,
    death_date  DATE,
    generation  INT             DEFAULT 1 COMMENT '世代层级',
    color_label VARCHAR(30)     DEFAULT 'default' COMMENT '颜色标注',
    avatar      VARCHAR(255),
    remark      VARCHAR(500),
    create_time DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_node_user_id (user_id),
    KEY idx_node_generation (user_id, generation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='族谱节点表';

CREATE TABLE IF NOT EXISTS family_relation (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id       BIGINT UNSIGNED NOT NULL,
    from_node_id  BIGINT UNSIGNED NOT NULL COMMENT '关系起点（亲子=父，夫妻=男方）',
    to_node_id    BIGINT UNSIGNED NOT NULL COMMENT '关系终点（亲子=子，夫妻=女方）',
    relation_type INT             NOT NULL COMMENT '1-亲子 2-夫妻',
    marriage_date DATE,
    divorce_date  DATE,
    create_time   DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_relation_user_id (user_id),
    KEY idx_relation_from (from_node_id),
    KEY idx_relation_to (to_node_id),
    UNIQUE KEY uk_relation (user_id, from_node_id, to_node_id, relation_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='族谱关系表';
