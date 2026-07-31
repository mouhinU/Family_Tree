-- 节点表增加同胞排次字段（MySQL）
-- V3__add_birth_order.sql

ALTER TABLE family_node ADD COLUMN birth_order INT DEFAULT NULL COMMENT '同胞排次（1=老大 2=老二 ...）';
