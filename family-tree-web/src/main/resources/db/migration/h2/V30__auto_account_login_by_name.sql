-- 自动账号改用真实姓名登录（H2）
-- V30__auto_account_login_by_name.sql
--
-- 与 MySQL 版本同义：新增自动账号标记列、标记存量自动账号、姓名可用者把 username 对换为 nickname。
-- 差异：H2 用 BOOLEAN 列；UPDATE 不支持表别名，故以表名 sys_user 关联 correlated 子查询。
-- nickname 不变，前台展示不受影响；密码哈希不变；唯一键 uk_username 由 NOT EXISTS 保护。

ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS is_auto_account BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE sys_user
SET is_auto_account = TRUE
WHERE EXISTS(SELECT 1
             FROM family_user_node b
             WHERE b.user_id = sys_user.id
               AND sys_user.username = CONCAT('fp_', b.node_id));

UPDATE sys_user
SET username = nickname
WHERE is_auto_account = TRUE
  AND nickname IS NOT NULL
  AND nickname <> ''
  AND nickname NOT LIKE '%*%'
  AND nickname NOT LIKE '%?%'
  AND NOT EXISTS(SELECT 1
                 FROM (SELECT username FROM sys_user) x
                 WHERE x.username = sys_user.nickname);