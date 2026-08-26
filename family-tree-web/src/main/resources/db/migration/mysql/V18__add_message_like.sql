-- 留言表增加点赞计数字段
ALTER TABLE family_message ADD COLUMN like_count BIGINT DEFAULT 0;

-- 创建留言点赞记录表
CREATE TABLE family_message_like (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id  BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    family_id   BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_message_user (message_id, user_id)
);

CREATE INDEX idx_message_like_message_id ON family_message_like(message_id);
CREATE INDEX idx_message_like_user_id ON family_message_like(user_id);
