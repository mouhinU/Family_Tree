-- 跨族链接提案、投票与状态事件；正式链接仅由平台审核通过后写入 family_node_link。

CREATE TABLE IF NOT EXISTS family_node_link_proposal (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    src_family_id        BIGINT UNSIGNED NOT NULL COMMENT '源家族ID（较小节点ID端所在家族）',
    src_node_id          BIGINT UNSIGNED NOT NULL COMMENT '源族谱节点ID（规范化为较小节点ID）',
    dst_family_id        BIGINT UNSIGNED NOT NULL COMMENT '目标家族ID',
    dst_node_id          BIGINT UNSIGNED NOT NULL COMMENT '目标族谱节点ID（规范化为较大节点ID）',
    link_type            INT NOT NULL COMMENT '链接类型：1血缘同人 2姻亲指认',
    status               VARCHAR(30) NOT NULL COMMENT '提案状态',
    src_requirement      INT NOT NULL COMMENT '源家族创建提案时的有效同意门槛',
    dst_requirement      INT NOT NULL COMMENT '目标家族创建提案时的有效同意门槛',
    active_src_node_id   BIGINT UNSIGNED DEFAULT NULL COMMENT '活跃提案源节点；终态置空以释放节点对',
    active_dst_node_id   BIGINT UNSIGNED DEFAULT NULL COMMENT '活跃提案目标节点；终态置空以释放节点对',
    initiator_id         BIGINT UNSIGNED NOT NULL COMMENT '发起人用户ID',
    initiator_family_id  BIGINT UNSIGNED NOT NULL COMMENT '发起人操作时所在家族ID',
    reason               VARCHAR(500) DEFAULT NULL COMMENT '发起说明',
    review_comment       VARCHAR(1000) DEFAULT NULL COMMENT '平台审核意见',
    reviewed_by          BIGINT UNSIGNED DEFAULT NULL COMMENT '平台审核人用户ID',
    reviewed_at          DATETIME DEFAULT NULL COMMENT '平台审核时间',
    expire_at            DATETIME NOT NULL COMMENT '提案到期时间',
    create_time          DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_node_link_proposal_active (active_src_node_id, active_dst_node_id),
    KEY idx_node_link_proposal_src_family (src_family_id, status),
    KEY idx_node_link_proposal_dst_family (dst_family_id, status),
    KEY idx_node_link_proposal_status_expire (status, expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='跨族节点链接提案表';

CREATE TABLE IF NOT EXISTS family_node_link_proposal_vote (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    proposal_id BIGINT UNSIGNED NOT NULL COMMENT '提案ID',
    family_id   BIGINT UNSIGNED NOT NULL COMMENT '投票所属家族ID',
    node_id     BIGINT UNSIGNED NOT NULL COMMENT '投票节点ID；同提案内唯一',
    user_id     BIGINT UNSIGNED NOT NULL COMMENT '投票账号ID',
    status      INT NOT NULL COMMENT '1有效 2已撤回',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_node_link_proposal_vote_node (proposal_id, node_id),
    KEY idx_node_link_proposal_vote_count (proposal_id, family_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='跨族链接提案同意投票表';

CREATE TABLE IF NOT EXISTS family_node_link_proposal_event (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    proposal_id         BIGINT UNSIGNED NOT NULL COMMENT '提案ID',
    event_type          VARCHAR(30) NOT NULL COMMENT '事件类型',
    from_status         VARCHAR(30) DEFAULT NULL COMMENT '变更前状态',
    to_status           VARCHAR(30) NOT NULL COMMENT '变更后状态',
    operator_id         BIGINT UNSIGNED DEFAULT NULL COMMENT '操作人用户ID',
    operator_family_id  BIGINT UNSIGNED DEFAULT NULL COMMENT '操作时家族ID',
    comment             VARCHAR(1000) DEFAULT NULL COMMENT '事件说明',
    ip_address          VARCHAR(64) DEFAULT NULL COMMENT '客户端IP',
    create_time         DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_node_link_proposal_event_proposal (proposal_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='跨族链接提案状态事件表';

ALTER TABLE family_node_link ADD COLUMN proposal_id BIGINT UNSIGNED DEFAULT NULL COMMENT '人工提案ID；账号自动成链为空';
ALTER TABLE family_node_link ADD UNIQUE KEY uk_node_link_proposal (proposal_id);
