-- 关系表增加丧偶标记字段（MySQL）
-- V7__add_widowed_flag.sql

-- 不加 IF NOT EXISTS：那是 MariaDB 语法，MySQL 8 报 1064 会卡死空库从零迁移；
-- 裸 ADD COLUMN 与 V2/V34/V35 一致，失败即停（改这一行会让存量库的 Flyway 校验失败，需 flyway repair）。
ALTER TABLE family_relation ADD COLUMN is_widowed TINYINT(1) DEFAULT 0 COMMENT '是否丧偶（1是 0否）';
