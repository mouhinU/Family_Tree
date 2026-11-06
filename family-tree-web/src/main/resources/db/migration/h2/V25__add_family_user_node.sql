-- 用户与家族节点绑定表：把"标记为我"从 sys_user 全局字段改为按家族隔离的关系数据
-- 一个用户在一个家族内最多绑定一个族谱节点，切换家族后各自独立

CREATE TABLE IF NOT EXISTS family_user_node (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_id   BIGINT    NOT NULL,
    user_id     BIGINT    NOT NULL,
    node_id     BIGINT    NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_node_family_user ON family_user_node(family_id, user_id);
CREATE INDEX IF NOT EXISTS idx_user_node_node_id ON family_user_node(node_id);

-- 存量迁移：家族归属以节点所在家族为准，并要求用户确为该家族成员
-- （sys_user.current_family_id 只是"当前激活家族"，可能为空或与节点家族不一致）
INSERT INTO family_user_node (family_id, user_id, node_id, create_time, update_time)
SELECT fn.family_id, su.id, su.node_id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM sys_user su
         JOIN family_node fn ON fn.id = su.node_id
         JOIN family_member fm ON fm.family_id = fn.family_id AND fm.user_id = su.id
WHERE su.node_id IS NOT NULL
  AND fn.family_id IS NOT NULL;

ALTER TABLE sys_user DROP COLUMN node_id;
