-- 中优先级功能：社交互动增强 + 纪念功能扩展
-- 家族相册 / 家族论坛 / 私信系统 / 活动组织 / 纪念日管理 / 在线祭堂缅怀留言 / 人物传记

-- 家族相册
CREATE TABLE IF NOT EXISTS family_photo (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_id   BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    username    VARCHAR(50)  NOT NULL,
    title       VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    photo_url   VARCHAR(500) NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_photo_family_create ON family_photo(family_id, create_time DESC);

-- 照片人物标记
CREATE TABLE IF NOT EXISTS family_photo_tag (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    photo_id    BIGINT      NOT NULL,
    node_id     BIGINT      NOT NULL,
    node_name   VARCHAR(50) NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_photo_tag_photo ON family_photo_tag(photo_id);

-- 家族论坛主题（富文本内容，服务端已白名单清洗）
CREATE TABLE IF NOT EXISTS family_forum_topic (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_id   BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    username    VARCHAR(50)  NOT NULL,
    title       VARCHAR(100) NOT NULL,
    content     CLOB         NOT NULL,
    view_count  BIGINT    DEFAULT 0,
    reply_count BIGINT    DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_forum_topic_family_create ON family_forum_topic(family_id, create_time DESC);

-- 家族论坛回复
CREATE TABLE IF NOT EXISTS family_forum_reply (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    topic_id    BIGINT       NOT NULL,
    family_id   BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    username    VARCHAR(50)  NOT NULL,
    content     VARCHAR(500) NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_forum_reply_topic ON family_forum_reply(topic_id, create_time);

-- 私信消息
CREATE TABLE IF NOT EXISTS family_private_message (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_id   BIGINT       NOT NULL,
    sender_id   BIGINT       NOT NULL,
    sender_name VARCHAR(50)  NOT NULL,
    receiver_id BIGINT       NOT NULL,
    content     VARCHAR(500) NOT NULL,
    is_read     TINYINT   NOT NULL DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_private_msg_pair ON family_private_message(sender_id, receiver_id, create_time);
CREATE INDEX IF NOT EXISTS idx_private_msg_receiver ON family_private_message(receiver_id, is_read);

-- 家族活动
CREATE TABLE IF NOT EXISTS family_event (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_id    BIGINT        NOT NULL,
    user_id      BIGINT        NOT NULL,
    username     VARCHAR(50)   NOT NULL,
    title        VARCHAR(100)  NOT NULL,
    description  VARCHAR(1000),
    event_time   TIMESTAMP     NOT NULL,
    location     VARCHAR(200),
    total_cost   DECIMAL(12, 2) DEFAULT 0,
    status       VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_event_family_create ON family_event(family_id, create_time DESC);

-- 活动报名
CREATE TABLE IF NOT EXISTS family_event_signup (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id       BIGINT      NOT NULL,
    family_id      BIGINT      NOT NULL,
    user_id        BIGINT      NOT NULL,
    username       VARCHAR(50) NOT NULL,
    attendee_count INT         NOT NULL DEFAULT 1,
    remark         VARCHAR(200),
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_signup_event_user ON family_event_signup(event_id, user_id);

-- 自定义纪念日（结婚周年、入学等）
CREATE TABLE IF NOT EXISTS family_anniversary (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_id        BIGINT       NOT NULL,
    node_id          BIGINT,
    user_id          BIGINT       NOT NULL,
    title            VARCHAR(100) NOT NULL,
    category         VARCHAR(20)  NOT NULL,
    anniversary_date DATE         NOT NULL,
    remark           VARCHAR(500),
    create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_anniversary_family ON family_anniversary(family_id, anniversary_date);

-- 在线祭堂：缅怀留言
CREATE TABLE IF NOT EXISTS family_memorial_message (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_id   BIGINT       NOT NULL,
    node_id     BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    username    VARCHAR(50)  NOT NULL,
    content     VARCHAR(500) NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_memorial_msg_node ON family_memorial_message(node_id, create_time DESC);

-- 人物传记（富文本内容，服务端已白名单清洗）
ALTER TABLE family_node ADD COLUMN IF NOT EXISTS biography CLOB;
