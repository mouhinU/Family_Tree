-- 跨族链接提案、投票与状态事件；正式链接仅由平台审核通过后写入 family_node_link。

CREATE TABLE IF NOT EXISTS family_node_link_proposal (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    src_family_id        BIGINT       NOT NULL,
    src_node_id          BIGINT       NOT NULL,
    dst_family_id        BIGINT       NOT NULL,
    dst_node_id          BIGINT       NOT NULL,
    link_type            INT          NOT NULL,
    status               VARCHAR(30)  NOT NULL,
    src_requirement      INT          NOT NULL,
    dst_requirement      INT          NOT NULL,
    active_src_node_id   BIGINT,
    active_dst_node_id   BIGINT,
    initiator_id         BIGINT       NOT NULL,
    initiator_family_id  BIGINT       NOT NULL,
    reason               VARCHAR(500),
    review_comment       VARCHAR(1000),
    reviewed_by          BIGINT,
    reviewed_at          TIMESTAMP,
    expire_at            TIMESTAMP    NOT NULL,
    create_time          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_node_link_proposal_active UNIQUE (active_src_node_id, active_dst_node_id)
);

CREATE INDEX IF NOT EXISTS idx_node_link_proposal_src_family
    ON family_node_link_proposal(src_family_id, status);
CREATE INDEX IF NOT EXISTS idx_node_link_proposal_dst_family
    ON family_node_link_proposal(dst_family_id, status);
CREATE INDEX IF NOT EXISTS idx_node_link_proposal_status_expire
    ON family_node_link_proposal(status, expire_at);

CREATE TABLE IF NOT EXISTS family_node_link_proposal_vote (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    proposal_id BIGINT      NOT NULL,
    family_id   BIGINT      NOT NULL,
    node_id     BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    status      INT         NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_node_link_proposal_vote_node UNIQUE (proposal_id, node_id)
);

CREATE INDEX IF NOT EXISTS idx_node_link_proposal_vote_count
    ON family_node_link_proposal_vote(proposal_id, family_id, status);

CREATE TABLE IF NOT EXISTS family_node_link_proposal_event (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    proposal_id         BIGINT       NOT NULL,
    event_type          VARCHAR(30)  NOT NULL,
    from_status         VARCHAR(30),
    to_status           VARCHAR(30)  NOT NULL,
    operator_id         BIGINT,
    operator_family_id  BIGINT,
    comment             VARCHAR(1000),
    ip_address          VARCHAR(64),
    create_time         TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_node_link_proposal_event_proposal
    ON family_node_link_proposal_event(proposal_id, id);

ALTER TABLE family_node_link ADD COLUMN IF NOT EXISTS proposal_id BIGINT;
CREATE UNIQUE INDEX IF NOT EXISTS uk_node_link_proposal ON family_node_link(proposal_id);
