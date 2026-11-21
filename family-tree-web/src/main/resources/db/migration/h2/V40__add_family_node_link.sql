-- 跨族节点链接表：族间身份引用，表达"本族节点 X 与他族节点 Y 是同一人（或姻亲对应人）"
-- 与 family_relation 正交：link 永不参与建树/世代修复/级联/亲属路径等族内算法，族内算法禁止读取本表
-- 两端按节点ID规范化存放（src_node_id < dst_node_id），配合唯一键实现双向去重

CREATE TABLE IF NOT EXISTS family_node_link (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    src_family_id BIGINT    NOT NULL,
    src_node_id   BIGINT    NOT NULL,
    dst_family_id BIGINT    NOT NULL,
    dst_node_id   BIGINT    NOT NULL,
    link_type     INT       NOT NULL,
    source        INT       NOT NULL,
    confirmed_by  BIGINT,
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_node_link ON family_node_link(src_node_id, dst_node_id);
CREATE INDEX IF NOT EXISTS idx_node_link_dst ON family_node_link(dst_family_id, dst_node_id);
CREATE INDEX IF NOT EXISTS idx_node_link_src_family ON family_node_link(src_family_id);
