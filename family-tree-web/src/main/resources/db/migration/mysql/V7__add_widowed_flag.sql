-- 关系表增加丧偶标记字段（MySQL）
-- V7__add_widowed_flag.sql

ALTER TABLE family_relation ADD COLUMN IF NOT EXISTS is_widowed TINYINT(1) DEFAULT 0 COMMENT '是否丧偶（1是 0否）';
