-- 操作日志表：记录登录、注册、节点增删改等关键操作
CREATE TABLE IF NOT EXISTS operation_log (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT DEFAULT NULL COMMENT '操作用户ID',
    username VARCHAR(50) DEFAULT NULL COMMENT '操作用户名',
    operation_type VARCHAR(30) NOT NULL COMMENT '操作类型',
    operation_desc VARCHAR(200) DEFAULT NULL COMMENT '操作描述',
    target_type VARCHAR(30) DEFAULT NULL COMMENT '操作对象类型',
    target_id BIGINT DEFAULT NULL COMMENT '操作对象ID',
    family_id BIGINT DEFAULT NULL COMMENT '所属家族ID',
    ip_address VARCHAR(50) DEFAULT NULL COMMENT '客户端IP',
    create_time DATETIME NOT NULL COMMENT '操作时间',
    INDEX idx_op_log_user (user_id),
    INDEX idx_op_log_type (operation_type),
    INDEX idx_op_log_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';
