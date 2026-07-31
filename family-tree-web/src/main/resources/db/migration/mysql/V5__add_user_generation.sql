-- 用户表增加所属辈分（第几世）字段（MySQL）
-- V5__add_user_generation.sql

ALTER TABLE sys_user ADD COLUMN generation INT NULL COMMENT '所属辈分（第几世）';

-- admin 账号为昌字辈（第 14 世）
UPDATE sys_user SET generation = 14 WHERE username = 'admin';
