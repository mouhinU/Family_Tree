-- 关系表增加离异标记字段（MySQL）
-- V2__add_divorced_flag.sql

ALTER TABLE family_relation ADD COLUMN is_divorced TINYINT(1) DEFAULT 0 COMMENT '是否离异：1是 0否';

-- 兼容旧数据：已有离异日期的记录标记为离异
UPDATE family_relation SET is_divorced = 1 WHERE divorce_date IS NOT NULL;
