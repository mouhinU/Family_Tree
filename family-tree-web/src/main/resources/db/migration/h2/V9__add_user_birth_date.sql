-- 用户表增加出生日期字段（H2）
-- V9__add_user_birth_date.sql

ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS birth_date VARCHAR(10);
