-- 收回重复账号（MySQL）：节点已被「标记为我」绑定时，绑定账号才是本人账号，fp_{节点ID} 属重复开通
-- 绑定关系只存在于 family_user_node，节点新增事件触发开通时还拿不到，因此只能在事后统一清理
-- id NOT IN 用于保护"自动账号自己后来绑定该节点"的情形，避免把正在使用的账号删掉

DELETE FROM sys_user
WHERE username IN(SELECT CONCAT('fp_', b.node_id)
                  FROM family_user_node b)
  AND id NOT IN(SELECT b.user_id
                FROM family_user_node b);
