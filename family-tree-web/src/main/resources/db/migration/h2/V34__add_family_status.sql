-- 家族状态列（H2）：平台管理员用于冻结 / 归档整个家族
-- V34__add_family_status.sql
--
-- 设计：家族状态是平台侧的治理标记，取值由代码内 FamilyStatusEnum 白名单约束。
--   NORMAL   正常，读写不受限
--   FROZEN   冻结，成员可浏览但所有写操作被拒绝
--   ARCHIVED 归档，家族整体下线，仅平台管理员可访问与恢复
-- 存量数据一律回落默认值 NORMAL，无需回填。
-- 差异：H2 不支持列级 COMMENT，ADD COLUMN 支持 IF NOT EXISTS。

ALTER TABLE family ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'NORMAL';
