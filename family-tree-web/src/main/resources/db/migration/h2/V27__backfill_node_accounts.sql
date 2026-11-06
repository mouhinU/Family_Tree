-- 为存量在世节点补建登录账号（H2）
-- 规则与节点新增事件监听器完全一致：用户名 fp_{节点ID}、昵称取节点姓名、已故节点不建、账号不关联家族。
-- NOT EXISTS 保证幂等：与监听器已开通的账号、以及重复执行都不会产生冲突行。
-- password_hash 是初始密码（AccountConsts.NODE_ACCOUNT_INITIAL_PASSWORD = 123456）的 BCrypt 值；
-- SQL 无法逐行加盐，本批账号共用同一哈希，用户改密后即分化，不影响登录校验。
-- H2 不支持 INSERT ... SELECT 尾部的 ORDER BY，故与 MySQL 版本的唯一差异是省略行序控制

INSERT INTO sys_user (username, password_hash, nickname, create_time, update_time)
SELECT CONCAT('fp_', n.id),
       '$2a$10$6TJ2qtfNgsIZ5oI4f.sSoeuyuhFoBvmMn6AjRGjPVLpLQxA4CCmDS',
       n.name,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM family_node n
WHERE n.death_date IS NULL
  AND NOT EXISTS(SELECT 1
                 FROM sys_user u
                 WHERE u.username = CONCAT('fp_', n.id));
