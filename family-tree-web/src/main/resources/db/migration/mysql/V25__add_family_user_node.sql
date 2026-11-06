-- 用户与家族节点绑定表（MySQL）：把"标记为我"从 sys_user 全局字段改为按家族隔离的关系数据
-- 一个用户在一个家族内最多绑定一个族谱节点，切换家族后各自独立

CREATE TABLE IF NOT EXISTS family_user_node (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    family_id   BIGINT UNSIGNED NOT NULL COMMENT '家族ID',
    user_id     BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    node_id     BIGINT UNSIGNED NOT NULL COMMENT '绑定的族谱节点ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_node_family_user (family_id, user_id),
    KEY idx_user_node_node_id (node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-家族-族谱节点绑定表';

-- 存量迁移：家族归属以节点所在家族为准，并要求用户确为该家族成员
-- （sys_user.current_family_id 只是"当前激活家族"，可能为空或与节点家族不一致）
-- sys_user.node_id 为历史遗留的有符号 BIGINT，转为 UNSIGNED 与主键类型一致，避免隐式转换使索引失效
INSERT INTO family_user_node (family_id, user_id, node_id)
SELECT fn.family_id, su.id, su.node_id
FROM sys_user su
         JOIN family_node fn ON fn.id = CAST(su.node_id AS UNSIGNED)
         JOIN family_member fm ON fm.family_id = fn.family_id AND fm.user_id = su.id
WHERE su.node_id IS NOT NULL
  AND fn.family_id IS NOT NULL;

ALTER TABLE sys_user DROP COLUMN node_id;
