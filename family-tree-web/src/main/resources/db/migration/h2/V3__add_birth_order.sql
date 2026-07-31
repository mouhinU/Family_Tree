-- 节点表增加同胞排次字段（H2）
-- V3__add_birth_order.sql

ALTER TABLE family_node ADD COLUMN IF NOT EXISTS birth_order INT;
