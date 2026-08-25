-- 家族留言板
CREATE TABLE family_message (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    family_id   BIGINT UNSIGNED NOT NULL                COMMENT '家族ID',
    user_id     BIGINT UNSIGNED NOT NULL                COMMENT '留言用户ID',
    username    VARCHAR(50)     NOT NULL                COMMENT '留言用户名',
    content     VARCHAR(500)    NOT NULL                COMMENT '留言内容',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT UNSIGNED NOT NULL DEFAULT 0     COMMENT '逻辑删除（0 正常 1 已删除）',
    INDEX idx_message_family_create (family_id, create_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='家族留言表';
