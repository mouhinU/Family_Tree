package com.mouhin.family.tree.domain.repository;

import com.mouhin.family.tree.domain.entity.CrossFamilyLinkProposalVote;

import java.util.List;

/**
 * 跨族链接提案投票仓储接口
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
public interface CrossFamilyLinkProposalVoteRepository {

    CrossFamilyLinkProposalVote save(CrossFamilyLinkProposalVote vote);

    void update(CrossFamilyLinkProposalVote vote);

    CrossFamilyLinkProposalVote findByProposalAndNode(Long proposalId, Long nodeId);

    List<CrossFamilyLinkProposalVote> listByProposalId(Long proposalId);

    long countActiveByProposalAndFamily(Long proposalId, Long familyId);

    List<CrossFamilyLinkProposalVote> listActiveByUserId(Long userId);
}
