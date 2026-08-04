-- 用户关联族谱节点：标记当前登录用户在族谱中的位置
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS node_id BIGINT DEFAULT NULL;
