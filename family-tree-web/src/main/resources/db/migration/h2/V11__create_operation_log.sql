-- 操作日志表：记录登录、注册、节点增删改等关键操作
CREATE TABLE IF NOT EXISTS operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(50),
    operation_type VARCHAR(30) NOT NULL,
    operation_desc VARCHAR(200),
    target_type VARCHAR(30),
    target_id BIGINT,
    family_id BIGINT,
    ip_address VARCHAR(50),
    create_time DATETIME NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_op_log_user ON operation_log(user_id);
CREATE INDEX IF NOT EXISTS idx_op_log_type ON operation_log(operation_type);
CREATE INDEX IF NOT EXISTS idx_op_log_time ON operation_log(create_time);
