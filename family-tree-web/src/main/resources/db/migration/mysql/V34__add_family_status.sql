-- 家族状态列（MySQL）：平台管理员用于冻结 / 归档整个家族
-- V34__add_family_status.sql
--
-- 设计：家族状态是平台侧的治理标记，取值由代码内 FamilyStatusEnum 白名单约束。
--   NORMAL   正常，读写不受限
--   FROZEN   冻结，成员可浏览但所有写操作被拒绝
--   ARCHIVED 归档，家族整体下线，仅平台管理员可访问与恢复
-- 存量数据一律回落默认值 NORMAL，无需回填。
-- 幂等：Flyway 单次执行 DDL；ADD COLUMN 不加 IF NOT EXISTS 以保持与既有 MySQL 迁移一致的失败即停语义。

ALTER TABLE family ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '家族状态：NORMAL正常 FROZEN冻结 ARCHIVED归档';
