package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mouhin.family.tree.common.enums.CrossFamilyLinkProposalStatusEnum;
import com.mouhin.family.tree.domain.entity.CrossFamilyLinkProposal;
import com.mouhin.family.tree.domain.repository.CrossFamilyLinkProposalRepository;
import com.mouhin.family.tree.infrastructure.converter.CrossFamilyLinkProposalConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyNodeLinkProposalDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.FamilyNodeLinkProposalMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 跨族链接提案仓储实现
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
@Repository
public class CrossFamilyLinkProposalRepositoryImpl implements CrossFamilyLinkProposalRepository {

    private final FamilyNodeLinkProposalMapper mapper;

    public CrossFamilyLinkProposalRepositoryImpl(FamilyNodeLinkProposalMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public CrossFamilyLinkProposal save(CrossFamilyLinkProposal proposal) {
        FamilyNodeLinkProposalDO doObj = CrossFamilyLinkProposalConverter.toDO(proposal);
        mapper.insert(doObj);
        proposal.setId(doObj.getId());
        return proposal;
    }

    @Override
    public void update(CrossFamilyLinkProposal proposal) {
        LambdaUpdateWrapper<FamilyNodeLinkProposalDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(FamilyNodeLinkProposalDO::getId, proposal.getId())
                .set(FamilyNodeLinkProposalDO::getStatus, proposal.getStatus())
                .set(FamilyNodeLinkProposalDO::getActiveSrcNodeId, proposal.getActiveSrcNodeId())
                .set(FamilyNodeLinkProposalDO::getActiveDstNodeId, proposal.getActiveDstNodeId())
                .set(FamilyNodeLinkProposalDO::getReviewComment, proposal.getReviewComment())
                .set(FamilyNodeLinkProposalDO::getReviewedBy, proposal.getReviewedBy())
                .set(FamilyNodeLinkProposalDO::getReviewedAt, proposal.getReviewedAt())
                .set(FamilyNodeLinkProposalDO::getUpdateTime, proposal.getUpdateTime());
        mapper.update(null, wrapper);
    }

    @Override
    public CrossFamilyLinkProposal findById(Long id) {
        return CrossFamilyLinkProposalConverter.toDomain(mapper.selectById(id));
    }

    @Override
    public CrossFamilyLinkProposal findByIdForUpdate(Long id) {
        LambdaQueryWrapper<FamilyNodeLinkProposalDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyNodeLinkProposalDO::getId, id).last("FOR UPDATE");
        return CrossFamilyLinkProposalConverter.toDomain(mapper.selectOne(wrapper));
    }

    @Override
    public CrossFamilyLinkProposal findActiveByNodePair(Long nodeA, Long nodeB) {
        if (nodeA == null || nodeB == null) {
            return null;
        }
        long srcId = Math.min(nodeA, nodeB);
        long dstId = Math.max(nodeA, nodeB);
        LambdaQueryWrapper<FamilyNodeLinkProposalDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyNodeLinkProposalDO::getActiveSrcNodeId, srcId)
                .eq(FamilyNodeLinkProposalDO::getActiveDstNodeId, dstId);
        return CrossFamilyLinkProposalConverter.toDomain(mapper.selectOne(wrapper));
    }

    @Override
    public List<CrossFamilyLinkProposal> listByFamilyId(Long familyId) {
        LambdaQueryWrapper<FamilyNodeLinkProposalDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(FamilyNodeLinkProposalDO::getSrcFamilyId, familyId)
                .or().eq(FamilyNodeLinkProposalDO::getDstFamilyId, familyId))
                .orderByDesc(FamilyNodeLinkProposalDO::getId);
        return CrossFamilyLinkProposalConverter.toDomainList(mapper.selectList(wrapper));
    }

    @Override
    public List<CrossFamilyLinkProposal> listForAdmin(String status, int offset, int size) {
        LambdaQueryWrapper<FamilyNodeLinkProposalDO> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(FamilyNodeLinkProposalDO::getStatus, status);
        }
        wrapper.orderByDesc(FamilyNodeLinkProposalDO::getId)
                .last("LIMIT " + size + " OFFSET " + offset);
        return CrossFamilyLinkProposalConverter.toDomainList(mapper.selectList(wrapper));
    }

    @Override
    public long countForAdmin(String status) {
        LambdaQueryWrapper<FamilyNodeLinkProposalDO> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(FamilyNodeLinkProposalDO::getStatus, status);
        }
        Long count = mapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    @Override
    public List<CrossFamilyLinkProposal> listExpiredActive(LocalDateTime now, int limit) {
        LambdaQueryWrapper<FamilyNodeLinkProposalDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(FamilyNodeLinkProposalDO::getStatus,
                        CrossFamilyLinkProposalStatusEnum.PENDING_VOTES.getCode(),
                        CrossFamilyLinkProposalStatusEnum.READY_FOR_REVIEW.getCode())
                .le(FamilyNodeLinkProposalDO::getExpireAt, now)
                .orderByAsc(FamilyNodeLinkProposalDO::getExpireAt)
                .last("LIMIT " + limit);
        List<FamilyNodeLinkProposalDO> doList = mapper.selectList(wrapper);
        return doList == null ? Collections.emptyList()
                : doList.stream().map(CrossFamilyLinkProposalConverter::toDomain).toList();
    }

    @Override
    public List<CrossFamilyLinkProposal> listActive(int limit) {
        LambdaQueryWrapper<FamilyNodeLinkProposalDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(FamilyNodeLinkProposalDO::getStatus,
                        CrossFamilyLinkProposalStatusEnum.PENDING_VOTES.getCode(),
                        CrossFamilyLinkProposalStatusEnum.READY_FOR_REVIEW.getCode())
                .orderByAsc(FamilyNodeLinkProposalDO::getId)
                .last("LIMIT " + limit);
        return CrossFamilyLinkProposalConverter.toDomainList(mapper.selectList(wrapper));
    }
}
