-- 通知表
CREATE TABLE IF NOT EXISTS notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_id BIGINT NOT NULL COMMENT '家族ID',
    user_id BIGINT NOT NULL COMMENT '接收用户ID',
    title VARCHAR(200) NOT NULL COMMENT '通知标题',
    content VARCHAR(1000) COMMENT '通知内容',
    notification_type VARCHAR(50) NOT NULL COMMENT '通知类型',
    related_id BIGINT COMMENT '关联对象ID',
    is_read TINYINT(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否已读',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_notification_user_id (user_id),
    INDEX idx_notification_family_id (family_id),
    INDEX idx_notification_read (user_id, is_read)
);
