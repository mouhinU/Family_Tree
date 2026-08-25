-- 家族留言板
CREATE TABLE IF NOT EXISTS family_message (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_id   BIGINT          NOT NULL,
    user_id     BIGINT          NOT NULL,
    username    VARCHAR(50)     NOT NULL,
    content     VARCHAR(500)    NOT NULL,
    create_time TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_message_family_create ON family_message(family_id, create_time DESC);
