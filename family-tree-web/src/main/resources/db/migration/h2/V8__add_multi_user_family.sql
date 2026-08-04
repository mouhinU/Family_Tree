-- 多用户共建家族树支持（H2）
-- V8__add_multi_user_family.sql

-- 1. 新建家族表
CREATE TABLE IF NOT EXISTS family (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100)    NOT NULL,
    invite_code   VARCHAR(20)     NOT NULL,
    creator_id    BIGINT          NOT NULL,
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_invite_code ON family(invite_code);
CREATE INDEX IF NOT EXISTS idx_family_creator ON family(creator_id);

-- 2. 新建家族成员表
CREATE TABLE IF NOT EXISTS family_member (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_id   BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    role        VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    joined_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_family_user ON family_member(family_id, user_id);
CREATE INDEX IF NOT EXISTS idx_member_user ON family_member(user_id);

-- 3. 为现有领域表添加 family_id 列
ALTER TABLE family_node ADD COLUMN IF NOT EXISTS family_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_node_family_id ON family_node(family_id);

ALTER TABLE family_relation ADD COLUMN IF NOT EXISTS family_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_relation_family_id ON family_relation(family_id);

ALTER TABLE family_generation ADD COLUMN IF NOT EXISTS family_id BIGINT;

ALTER TABLE family_offering ADD COLUMN IF NOT EXISTS family_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_offering_family ON family_offering(family_id);

-- 4. 数据迁移：为已有数据的用户自动创建家族并回填 family_id
-- H2 不支持 MD5 函数，使用 UUID 生成邀请码
INSERT INTO family (name, invite_code, creator_id)
SELECT
    CONCAT(COALESCE(u.nickname, u.username), '的家族'),
    SUBSTRING(REPLACE(RANDOM_UUID(), '-', ''), 1, 8),
    u.id
FROM sys_user u
WHERE EXISTS (SELECT 1 FROM family_node fn WHERE fn.user_id = u.id);

INSERT INTO family_member (family_id, user_id, role)
SELECT f.id, f.creator_id, 'OWNER'
FROM family f;

UPDATE family_node fn
    SET fn.family_id = (SELECT f.id FROM family f WHERE fn.user_id = f.creator_id)
    WHERE fn.family_id IS NULL;

UPDATE family_relation fr
    SET fr.family_id = (SELECT f.id FROM family f WHERE fr.user_id = f.creator_id)
    WHERE fr.family_id IS NULL;

UPDATE family_generation fg
    SET fg.family_id = (SELECT f.id FROM family f WHERE fg.user_id = f.creator_id)
    WHERE fg.family_id IS NULL;

UPDATE family_offering fo
    SET fo.family_id = (SELECT f.id FROM family f WHERE fo.user_id = f.creator_id)
    WHERE fo.family_id IS NULL;
