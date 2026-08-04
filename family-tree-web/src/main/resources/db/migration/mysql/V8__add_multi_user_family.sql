-- 多用户共建家族树支持（MySQL）
-- V8__add_multi_user_family.sql

-- 1. 新建家族表
CREATE TABLE IF NOT EXISTS family (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name          VARCHAR(100)    NOT NULL COMMENT '家族名称',
    invite_code   VARCHAR(20)     NOT NULL COMMENT '邀请码（唯一随机码）',
    creator_id    BIGINT UNSIGNED NOT NULL COMMENT '创建者（族长）用户ID',
    create_time   DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_invite_code (invite_code),
    KEY idx_family_creator (creator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='家族表';

-- 2. 新建家族成员表
CREATE TABLE IF NOT EXISTS family_member (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    family_id   BIGINT UNSIGNED NOT NULL COMMENT '家族ID',
    user_id     BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    role        VARCHAR(20)     NOT NULL DEFAULT 'MEMBER' COMMENT '角色：OWNER/MEMBER',
    joined_time DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    create_time DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_family_user (family_id, user_id),
    KEY idx_member_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='家族成员表';

-- 3. 为现有领域表添加 family_id 列
ALTER TABLE family_node ADD COLUMN family_id BIGINT UNSIGNED NULL COMMENT '所属家族ID';
CREATE INDEX idx_node_family_id ON family_node (family_id);

ALTER TABLE family_relation ADD COLUMN family_id BIGINT UNSIGNED NULL COMMENT '所属家族ID';
CREATE INDEX idx_relation_family_id ON family_relation (family_id);

ALTER TABLE family_generation ADD COLUMN family_id BIGINT UNSIGNED NULL COMMENT '所属家族ID';

ALTER TABLE family_offering ADD COLUMN family_id BIGINT UNSIGNED NULL COMMENT '所属家族ID';
CREATE INDEX idx_offering_family ON family_offering (family_id);

-- 4. 数据迁移：为已有数据的用户自动创建家族并回填 family_id
-- 4.1 为每个有节点数据的用户创建家族
INSERT INTO family (name, invite_code, creator_id)
SELECT
    CONCAT(IFNULL(u.nickname, u.username), '的家族'),
    SUBSTRING(MD5(RAND()), 1, 8),
    u.id
FROM sys_user u
WHERE EXISTS (SELECT 1 FROM family_node fn WHERE fn.user_id = u.id);

-- 4.2 创建对应的家族成员记录（创建者即为族长）
INSERT INTO family_member (family_id, user_id, role)
SELECT f.id, f.creator_id, 'OWNER'
FROM family f;

-- 4.3 回填 family_id 到各 domain 表
UPDATE family_node fn
    JOIN family f ON fn.user_id = f.creator_id
    SET fn.family_id = f.id;

UPDATE family_relation fr
    JOIN family f ON fr.user_id = f.creator_id
    SET fr.family_id = f.id;

UPDATE family_generation fg
    JOIN family f ON fg.user_id = f.creator_id
    SET fg.family_id = f.id;

UPDATE family_offering fo
    JOIN family f ON fo.user_id = f.creator_id
    SET fo.family_id = f.id;
