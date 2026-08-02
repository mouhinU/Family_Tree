-- 关系表增加丧偶标记字段（H2）
-- V7__add_widowed_flag.sql

ALTER TABLE family_relation ADD COLUMN IF NOT EXISTS is_widowed BOOLEAN DEFAULT FALSE;
