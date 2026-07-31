-- 族谱管理系统初始化脚本（H2）
-- V1__init_schema.sql

CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    nickname    VARCHAR(50),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_username ON sys_user(username);

CREATE TABLE IF NOT EXISTS family_node (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    name        VARCHAR(50)  NOT NULL,
    gender      INT          DEFAULT 0,
    birth_date  DATE,
    death_date  DATE,
    generation  INT          DEFAULT 1,
    color_label VARCHAR(30)  DEFAULT 'default',
    avatar      VARCHAR(255),
    remark      VARCHAR(500),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_node_user_id ON family_node(user_id);
CREATE INDEX IF NOT EXISTS idx_node_generation ON family_node(user_id, generation);

CREATE TABLE IF NOT EXISTS family_relation (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    from_node_id  BIGINT NOT NULL,
    to_node_id    BIGINT NOT NULL,
    relation_type INT    NOT NULL,
    marriage_date DATE,
    divorce_date  DATE,
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_relation_user_id ON family_relation(user_id);
CREATE INDEX IF NOT EXISTS idx_relation_from ON family_relation(from_node_id);
CREATE INDEX IF NOT EXISTS idx_relation_to ON family_relation(to_node_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_relation ON family_relation(user_id, from_node_id, to_node_id, relation_type);
