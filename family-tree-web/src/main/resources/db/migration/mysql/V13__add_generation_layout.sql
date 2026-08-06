-- 辈分管理行列布局持久化（MySQL）
-- V13__add_generation_layout.sql

ALTER TABLE family ADD COLUMN generation_cols INT DEFAULT 5 COMMENT '辈分管理列数';
ALTER TABLE family ADD COLUMN generation_rows INT DEFAULT 5 COMMENT '辈分管理行数';
