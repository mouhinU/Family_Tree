-- 账号表瘦身与更名（H2）：
-- 1) 辈分正式存储一直维护在节点层 family_node.generation，sys_user.generation 只是资料展示副本，
--    且绑定回填会用后绑家族的世代覆写账号字段（多家族串辈污染源），列直接删除；
-- 2) 表名 sys_user 更名为 account，与账号领域语义对齐。索引名随表保留，不改写历史迁移。
ALTER TABLE sys_user DROP COLUMN generation;
ALTER TABLE sys_user RENAME TO account;
