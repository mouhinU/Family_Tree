-- 辈分（世代名称）表（MySQL）
-- V4__add_generation_name.sql

CREATE TABLE IF NOT EXISTS family_generation (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL,
    generation  INT             NOT NULL COMMENT '世代（从1开始）',
    name        VARCHAR(50)     COMMENT '辈分名称（字辈）',
    create_time DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_generation (user_id, generation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='族谱辈分名称表';
