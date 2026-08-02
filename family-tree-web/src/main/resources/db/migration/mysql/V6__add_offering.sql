-- 祭奠记录表：记录用户对已故长辈的上香烛 / 烧纸操作（MySQL）
-- V6__add_offering.sql

CREATE TABLE IF NOT EXISTS family_offering (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id       BIGINT NOT NULL COMMENT '上香/烧纸的用户ID',
    node_id       BIGINT NOT NULL COMMENT '受祭的已故节点ID',
    offering_type TINYINT NOT NULL COMMENT '祭奠类型：1-香烛 2-烧纸',
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_offering_node (node_id, offering_type),
    KEY idx_offering_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='祭奠记录表';
