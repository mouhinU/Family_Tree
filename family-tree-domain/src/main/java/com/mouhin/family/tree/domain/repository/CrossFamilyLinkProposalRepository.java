package com.mouhin.family.tree.domain.repository;

import com.mouhin.family.tree.domain.entity.CrossFamilyLinkProposal;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 跨族链接提案仓储接口
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
public interface CrossFamilyLinkProposalRepository {

    CrossFamilyLinkProposal save(CrossFamilyLinkProposal proposal);

    void update(CrossFamilyLinkProposal proposal);

    CrossFamilyLinkProposal findById(Long id);

    CrossFamilyLinkProposal findByIdForUpdate(Long id);

    CrossFamilyLinkProposal findActiveByNodePair(Long nodeA, Long nodeB);

    List<CrossFamilyLinkProposal> listByFamilyId(Long familyId);

    List<CrossFamilyLinkProposal> listForAdmin(String status, int offset, int size);

    long countForAdmin(String status);

    List<CrossFamilyLinkProposal> listExpiredActive(LocalDateTime now, int limit);

    /**
     * 查询活跃提案，供定时任务批量校验到期与投票资格。
     *
     * @param limit 单批最大条数
     * @return 活跃提案列表
     */
    List<CrossFamilyLinkProposal> listActive(int limit);
}
