-- 辈分管理行列布局持久化（H2）
-- V13__add_generation_layout.sql

ALTER TABLE family ADD COLUMN IF NOT EXISTS generation_cols INT DEFAULT 5;
ALTER TABLE family ADD COLUMN IF NOT EXISTS generation_rows INT DEFAULT 5;
