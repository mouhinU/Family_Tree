-- 一次性初始口令首登强制改密（MySQL）
-- V31__must_change_password.sql
--
-- 背景：族谱节点自动开通的账号此前使用固定初始口令，长期有效存在被冒登风险。
-- 新增 must_change_password 标记：自动开通账号首登强制修改口令后清除。
--   1) 新增 TINYINT(1) 列，默认 0（真人注册账号无需强制改密）。
--   2) 存量自动账号回填为 1，其下次登录即被要求改密。
-- 幂等：Flyway 单次执行 DDL；UPDATE 按 is_auto_account 精确回填，可重跑无副作用。

ALTER TABLE sys_user ADD COLUMN must_change_password TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否需要首登强制改密：1是 0否';

UPDATE sys_user
SET must_change_password = 1
WHERE is_auto_account = 1;
