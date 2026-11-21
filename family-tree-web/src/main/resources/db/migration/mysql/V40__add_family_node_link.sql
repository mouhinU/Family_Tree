-- 跨族节点链接表（MySQL）：族间身份引用，表达"本族节点 X 与他族节点 Y 是同一人（或姻亲对应人）"
-- 与 family_relation 正交：link 永不参与建树/世代修复/级联/亲属路径等族内算法，族内算法禁止读取本表
-- 两端按节点ID规范化存放（src_node_id < dst_node_id），配合唯一键实现双向去重
-- source=ACCOUNT 的链由 family_user_node 真人账号多族绑定自动派生，解绑时自动释放

CREATE TABLE IF NOT EXISTS family_node_link (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    src_family_id BIGINT UNSIGNED NOT NULL COMMENT '源家族ID（src_node_id 较小端所在家族）',
    src_node_id   BIGINT UNSIGNED NOT NULL COMMENT '源族谱节点ID（规范化为较小节点ID）',
    dst_family_id BIGINT UNSIGNED NOT NULL COMMENT '目标家族ID',
    dst_node_id   BIGINT UNSIGNED NOT NULL COMMENT '目标族谱节点ID（规范化为较大节点ID）',
    link_type     INT NOT NULL COMMENT '链接类型：1血缘同人（外嫁女/入赘男回指出身族） 2姻亲指认',
    source        INT NOT NULL COMMENT '成链来源：1账号自动 2人工指认 3候选确认',
    confirmed_by  BIGINT UNSIGNED DEFAULT NULL COMMENT '确认人用户ID（账号自动来源为空）',
    create_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_node_link (src_node_id, dst_node_id),
    KEY idx_node_link_dst (dst_family_id, dst_node_id),
    KEY idx_node_link_src_family (src_family_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='跨族节点链接表（族间身份引用，不参与族内算法）';
