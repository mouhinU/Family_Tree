package com.mouhin.family.tree.domain.repository;

import com.mouhin.family.tree.domain.entity.CrossFamilyLinkProposalEvent;

import java.util.List;

/**
 * 跨族链接提案事件仓储接口
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
public interface CrossFamilyLinkProposalEventRepository {

    void save(CrossFamilyLinkProposalEvent event);

    List<CrossFamilyLinkProposalEvent> listByProposalId(Long proposalId);
}
