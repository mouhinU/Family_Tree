package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.family.tree.common.enums.CrossFamilyLinkProposalVoteStatusEnum;
import com.mouhin.family.tree.domain.entity.CrossFamilyLinkProposalVote;
import com.mouhin.family.tree.domain.repository.CrossFamilyLinkProposalVoteRepository;
import com.mouhin.family.tree.infrastructure.converter.CrossFamilyLinkProposalConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyNodeLinkProposalVoteDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.FamilyNodeLinkProposalVoteMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 跨族链接提案投票仓储实现
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
@Repository
public class CrossFamilyLinkProposalVoteRepositoryImpl implements CrossFamilyLinkProposalVoteRepository {

    private final FamilyNodeLinkProposalVoteMapper mapper;

    public CrossFamilyLinkProposalVoteRepositoryImpl(FamilyNodeLinkProposalVoteMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public CrossFamilyLinkProposalVote save(CrossFamilyLinkProposalVote vote) {
        FamilyNodeLinkProposalVoteDO doObj = CrossFamilyLinkProposalConverter.toDO(vote);
        mapper.insert(doObj);
        vote.setId(doObj.getId());
        return vote;
    }

    @Override
    public void update(CrossFamilyLinkProposalVote vote) {
        mapper.updateById(CrossFamilyLinkProposalConverter.toDO(vote));
    }

    @Override
    public CrossFamilyLinkProposalVote findByProposalAndNode(Long proposalId, Long nodeId) {
        LambdaQueryWrapper<FamilyNodeLinkProposalVoteDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyNodeLinkProposalVoteDO::getProposalId, proposalId)
                .eq(FamilyNodeLinkProposalVoteDO::getNodeId, nodeId)
                .last("LIMIT 1");
        return CrossFamilyLinkProposalConverter.toDomain(mapper.selectOne(wrapper));
    }

    @Override
    public List<CrossFamilyLinkProposalVote> listByProposalId(Long proposalId) {
        LambdaQueryWrapper<FamilyNodeLinkProposalVoteDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyNodeLinkProposalVoteDO::getProposalId, proposalId)
                .orderByAsc(FamilyNodeLinkProposalVoteDO::getId);
        return CrossFamilyLinkProposalConverter.toVoteDomainList(mapper.selectList(wrapper));
    }

    @Override
    public long countActiveByProposalAndFamily(Long proposalId, Long familyId) {
        LambdaQueryWrapper<FamilyNodeLinkProposalVoteDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyNodeLinkProposalVoteDO::getProposalId, proposalId)
                .eq(FamilyNodeLinkProposalVoteDO::getFamilyId, familyId)
                .eq(FamilyNodeLinkProposalVoteDO::getStatus,
                        CrossFamilyLinkProposalVoteStatusEnum.ACTIVE.getCode());
        Long count = mapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    @Override
    public List<CrossFamilyLinkProposalVote> listActiveByUserId(Long userId) {
        LambdaQueryWrapper<FamilyNodeLinkProposalVoteDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyNodeLinkProposalVoteDO::getUserId, userId)
                .eq(FamilyNodeLinkProposalVoteDO::getStatus,
                        CrossFamilyLinkProposalVoteStatusEnum.ACTIVE.getCode());
        return CrossFamilyLinkProposalConverter.toVoteDomainList(mapper.selectList(wrapper));
    }
}
