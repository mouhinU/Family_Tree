-- 自动账号改用真实姓名登录（MySQL）
-- V30__auto_account_login_by_name.sql
--
-- 背景：自动账号原用户名恒为 fp_{节点ID}，族人需记编号才能登录。改为姓名优先登录：
--   1) 新增 is_auto_account 标记列，替代原先靠 username=fp_{节点ID} 识别自动账号的方式
--      （删除回收、真人标记回收据此区分自动账号与真人账号）。
--   2) 标记存量自动账号：用户名恰为 fp_{其绑定节点ID} 者（V29 已为全部 fp_ 账号补建绑定）。
--   3) 姓名可用（非空、非脱敏、未被任何账号占用）的自动账号，把 username 对换为 nickname（真实姓名）；
--      其余（脱敏名/空名/与真人账号重名）保留 fp_{节点ID} 兜底登录。
--   nickname 保持不变，前台展示（走 nickname/会话昵称）不受影响；密码哈希不变。
--
-- 唯一键安全：username 有 uk_username 唯一约束。存量数据中干净姓名在自动账号间无重复，
--   仅 3 例与真人账号用户名重名，由 NOT EXISTS 排除，故对换不会触发唯一键冲突。
--   派生表 (SELECT username FROM sys_user) 规避 MySQL "不能更新子查询中的目标表"(1093)。
-- 幂等：Flyway 单次执行 DDL；数据 UPDATE 可重跑——标记按绑定精确匹配，对换后姓名已存在于用户名快照（自身），
--   NOT EXISTS 不成立而跳过，无副作用。

ALTER TABLE sys_user ADD COLUMN is_auto_account TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否族谱节点自动开通账号：1是 0否';

UPDATE sys_user u
SET u.is_auto_account = 1
WHERE EXISTS(SELECT 1
             FROM family_user_node b
             WHERE b.user_id = u.id
               AND u.username = CONCAT('fp_', b.node_id));

UPDATE sys_user u
SET u.username = u.nickname
WHERE u.is_auto_account = 1
  AND u.nickname IS NOT NULL
  AND u.nickname <> ''
  AND u.nickname NOT LIKE '%*%'
  AND u.nickname NOT LIKE '%?%'
  AND NOT EXISTS(SELECT 1
                 FROM (SELECT username FROM sys_user) x
                 WHERE x.username = u.nickname);