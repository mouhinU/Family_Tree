-- 祭奠记录表：记录用户对已故长辈的上香烛 / 烧纸操作（H2）
-- V6__add_offering.sql

CREATE TABLE IF NOT EXISTS family_offering (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    node_id       BIGINT NOT NULL,
    offering_type INT    NOT NULL,
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_offering_node ON family_offering(node_id, offering_type);
CREATE INDEX IF NOT EXISTS idx_offering_user ON family_offering(user_id);
