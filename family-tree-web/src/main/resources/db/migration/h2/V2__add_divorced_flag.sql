-- 关系表增加离异标记字段（H2）
-- V2__add_divorced_flag.sql

ALTER TABLE family_relation ADD COLUMN IF NOT EXISTS is_divorced BOOLEAN DEFAULT FALSE;
