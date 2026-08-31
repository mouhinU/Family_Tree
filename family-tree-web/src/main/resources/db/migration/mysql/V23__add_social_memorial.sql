-- 中优先级功能：社交互动增强 + 纪念功能扩展
-- 家族相册 / 家族论坛 / 私信系统 / 活动组织 / 纪念日管理 / 在线祭堂缅怀留言 / 人物传记

-- 家族相册
CREATE TABLE IF NOT EXISTS family_photo (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    family_id   BIGINT UNSIGNED NOT NULL              COMMENT '家族ID',
    user_id     BIGINT UNSIGNED NOT NULL              COMMENT '上传用户ID',
    username    VARCHAR(50)     NOT NULL              COMMENT '上传用户名',
    title       VARCHAR(100)    NOT NULL              COMMENT '照片标题',
    description VARCHAR(500)    DEFAULT NULL          COMMENT '照片描述',
    photo_url   VARCHAR(500)    NOT NULL              COMMENT '照片访问地址',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_photo_family_create (family_id, create_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='家族相册表';

-- 照片人物标记
CREATE TABLE IF NOT EXISTS family_photo_tag (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    photo_id    BIGINT UNSIGNED NOT NULL              COMMENT '照片ID',
    node_id     BIGINT UNSIGNED NOT NULL              COMMENT '族谱节点ID',
    node_name   VARCHAR(50)     NOT NULL              COMMENT '节点姓名',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_photo_tag_photo (photo_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='照片人物标记表';

-- 家族论坛主题（富文本内容，服务端已白名单清洗）
CREATE TABLE IF NOT EXISTS family_forum_topic (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    family_id   BIGINT UNSIGNED NOT NULL              COMMENT '家族ID',
    user_id     BIGINT UNSIGNED NOT NULL              COMMENT '发帖用户ID',
    username    VARCHAR(50)     NOT NULL              COMMENT '发帖用户名',
    title       VARCHAR(100)    NOT NULL              COMMENT '主题标题',
    content     TEXT            NOT NULL              COMMENT '富文本内容',
    view_count  BIGINT          NOT NULL DEFAULT 0    COMMENT '浏览数',
    reply_count BIGINT          NOT NULL DEFAULT 0    COMMENT '回复数',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_forum_topic_family_create (family_id, create_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='家族论坛主题表';

-- 家族论坛回复
CREATE TABLE IF NOT EXISTS family_forum_reply (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    topic_id    BIGINT UNSIGNED NOT NULL              COMMENT '主题ID',
    family_id   BIGINT UNSIGNED NOT NULL              COMMENT '家族ID',
    user_id     BIGINT UNSIGNED NOT NULL              COMMENT '回复用户ID',
    username    VARCHAR(50)     NOT NULL              COMMENT '回复用户名',
    content     VARCHAR(500)    NOT NULL              COMMENT '回复内容',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_forum_reply_topic (topic_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='家族论坛回复表';

-- 私信消息
CREATE TABLE IF NOT EXISTS family_private_message (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    family_id   BIGINT UNSIGNED NOT NULL              COMMENT '家族ID',
    sender_id   BIGINT UNSIGNED NOT NULL              COMMENT '发送者ID',
    sender_name VARCHAR(50)     NOT NULL              COMMENT '发送者昵称',
    receiver_id BIGINT UNSIGNED NOT NULL              COMMENT '接收者ID',
    content     VARCHAR(500)    NOT NULL              COMMENT '消息内容',
    is_read     TINYINT         NOT NULL DEFAULT 0    COMMENT '是否已读：1是 0否',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_private_msg_pair (sender_id, receiver_id, create_time),
    INDEX idx_private_msg_receiver (receiver_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私信消息表';

-- 家族活动
CREATE TABLE IF NOT EXISTS family_event (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    family_id   BIGINT UNSIGNED NOT NULL              COMMENT '家族ID',
    user_id     BIGINT UNSIGNED NOT NULL              COMMENT '发起人ID',
    username    VARCHAR(50)     NOT NULL              COMMENT '发起人姓名',
    title       VARCHAR(100)    NOT NULL              COMMENT '活动标题',
    description VARCHAR(1000)   DEFAULT NULL          COMMENT '活动说明',
    event_time  DATETIME        NOT NULL              COMMENT '活动时间',
    location    VARCHAR(200)    DEFAULT NULL          COMMENT '活动地点',
    total_cost  DECIMAL(12,2)   NOT NULL DEFAULT 0    COMMENT '活动总费用（AA计算基数）',
    status      VARCHAR(20)     NOT NULL DEFAULT 'OPEN' COMMENT '状态：OPEN报名中/CLOSED已截止',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_event_family_create (family_id, create_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='家族活动表';

-- 活动报名
CREATE TABLE IF NOT EXISTS family_event_signup (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    event_id       BIGINT UNSIGNED NOT NULL           COMMENT '活动ID',
    family_id      BIGINT UNSIGNED NOT NULL           COMMENT '家族ID',
    user_id        BIGINT UNSIGNED NOT NULL           COMMENT '报名用户ID',
    username       VARCHAR(50)     NOT NULL           COMMENT '报名用户名',
    attendee_count INT             NOT NULL DEFAULT 1 COMMENT '参加人数',
    remark         VARCHAR(200)    DEFAULT NULL       COMMENT '报名备注',
    create_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_signup_event_user (event_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动报名表';

-- 自定义纪念日（结婚周年、入学等）
CREATE TABLE IF NOT EXISTS family_anniversary (
    id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    family_id        BIGINT UNSIGNED NOT NULL         COMMENT '家族ID',
    node_id          BIGINT UNSIGNED DEFAULT NULL     COMMENT '关联族谱节点ID（可空）',
    user_id          BIGINT UNSIGNED NOT NULL         COMMENT '创建用户ID',
    title            VARCHAR(100)    NOT NULL         COMMENT '纪念日标题',
    category         VARCHAR(20)     NOT NULL         COMMENT '分类：wedding/school/memorial/other',
    anniversary_date DATE            NOT NULL         COMMENT '纪念日日期',
    remark           VARCHAR(500)    DEFAULT NULL     COMMENT '备注',
    create_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_anniversary_family (family_id, anniversary_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='家族纪念日表';

-- 在线祭堂：缅怀留言
CREATE TABLE IF NOT EXISTS family_memorial_message (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    family_id   BIGINT UNSIGNED NOT NULL              COMMENT '家族ID',
    node_id     BIGINT UNSIGNED NOT NULL              COMMENT '已故节点ID',
    user_id     BIGINT UNSIGNED NOT NULL              COMMENT '留言用户ID',
    username    VARCHAR(50)     NOT NULL              COMMENT '留言用户名',
    content     VARCHAR(500)    NOT NULL              COMMENT '缅怀留言内容',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_memorial_msg_node (node_id, create_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='祭堂缅怀留言表';

-- 人物传记（富文本内容，服务端已白名单清洗）
ALTER TABLE family_node ADD COLUMN biography TEXT COMMENT '人物传记（富文本）';
