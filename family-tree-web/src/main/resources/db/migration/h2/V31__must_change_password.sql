-- 一次性初始口令首登强制改密（H2）
-- V31__must_change_password.sql
--
-- 背景：族谱节点自动开通的账号此前使用固定初始口令，长期有效存在被冒登风险。
-- 新增 must_change_password 标记：自动开通账号首登强制修改口令后清除。
--   1) 新增布尔列，默认 FALSE（真人注册账号无需强制改密）。
--   2) 存量自动账号回填为 TRUE，其下次登录即被要求改密。
-- 差异：H2 用 BOOLEAN 列，ADD COLUMN 支持 IF NOT EXISTS。

ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE sys_user
SET must_change_password = TRUE
WHERE is_auto_account = TRUE;
