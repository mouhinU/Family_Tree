-- 家族时间线：记录出生、去世、结婚、家族活动等事件
-- 通过唯一约束 (source_type, source_id, event_type) 防止重复记录

CREATE TABLE IF NOT EXISTS family_timeline (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_id    BIGINT       NOT NULL,
    source_type  VARCHAR(20)  NOT NULL,
    source_id    BIGINT       NOT NULL,
    event_type   VARCHAR(20)  NOT NULL,
    event_date   DATE         NOT NULL,
    description  VARCHAR(200) NOT NULL,
    node_id      BIGINT,
    relation_id  BIGINT,
    is_new       TINYINT      NOT NULL DEFAULT 1,
    create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_timeline_source_event ON family_timeline(source_type, source_id, event_type);
CREATE INDEX IF NOT EXISTS idx_timeline_family_date ON family_timeline(family_id, event_date DESC);
