-- 用户表增加所属辈分（第几世）字段（H2）
-- V5__add_user_generation.sql

ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS generation INT;

-- admin 账号为昌字辈（第 14 世）
UPDATE sys_user SET generation = 14 WHERE username = 'admin';
