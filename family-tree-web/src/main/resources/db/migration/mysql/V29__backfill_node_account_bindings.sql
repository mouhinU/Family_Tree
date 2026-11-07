-- 为存量在世节点的 fp_ 自动账号补建绑定关系（MySQL）
-- 与运行时 provisionAndBindNodeAccount 规则一致：用户名 fp_{节点ID}、已故节点不补、节点已被真人标记则不补。
-- 双重 NOT EXISTS 保证幂等：按 (family_id,user_id) 防重复绑定，按 (family_id,node_id) 防覆盖真人归属。
-- V28 已删除被真人绑定节点的重复 fp_ 账号，JOIN 自然排除这些节点；node_id 保护作纵深防御。
-- family_id IS NOT NULL 因 family_user_node.family_id 为 NOT NULL。

INSERT INTO family_user_node (family_id, user_id, node_id, create_time, update_time)
SELECT n.family_id, u.id, n.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM family_node n
         JOIN sys_user u ON u.username = CONCAT('fp_', n.id)
WHERE n.death_date IS NULL
  AND n.family_id IS NOT NULL
  AND NOT EXISTS(SELECT 1
                 FROM family_user_node b
                 WHERE b.family_id = n.family_id
                   AND b.user_id = u.id)
  AND NOT EXISTS(SELECT 1
                 FROM family_user_node b
                 WHERE b.family_id = n.family_id
                   AND b.node_id = n.id)
ORDER BY n.id;