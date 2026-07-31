-- 辈分（世代名称）表（H2）
-- V4__add_generation_name.sql

CREATE TABLE IF NOT EXISTS family_generation (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    generation  INT         NOT NULL,
    name        VARCHAR(50),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_generation ON family_generation(user_id, generation);
