-- 用户表增加出生日期字段（MySQL）
-- V9__add_user_birth_date.sql

ALTER TABLE sys_user ADD COLUMN birth_date VARCHAR(10) DEFAULT NULL COMMENT '出生日期';
