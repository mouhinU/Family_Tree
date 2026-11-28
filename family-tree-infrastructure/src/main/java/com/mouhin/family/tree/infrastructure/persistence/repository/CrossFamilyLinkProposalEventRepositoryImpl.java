package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.family.tree.domain.entity.CrossFamilyLinkProposalEvent;
import com.mouhin.family.tree.domain.repository.CrossFamilyLinkProposalEventRepository;
import com.mouhin.family.tree.infrastructure.converter.CrossFamilyLinkProposalConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyNodeLinkProposalEventDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.FamilyNodeLinkProposalEventMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 跨族链接提案事件仓储实现
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
@Repository
public class CrossFamilyLinkProposalEventRepositoryImpl implements CrossFamilyLinkProposalEventRepository {

    private final FamilyNodeLinkProposalEventMapper mapper;

    public CrossFamilyLinkProposalEventRepositoryImpl(FamilyNodeLinkProposalEventMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(CrossFamilyLinkProposalEvent event) {
        mapper.insert(CrossFamilyLinkProposalConverter.toDO(event));
    }

    @Override
    public List<CrossFamilyLinkProposalEvent> listByProposalId(Long proposalId) {
        LambdaQueryWrapper<FamilyNodeLinkProposalEventDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyNodeLinkProposalEventDO::getProposalId, proposalId)
                .orderByAsc(FamilyNodeLinkProposalEventDO::getId);
        return CrossFamilyLinkProposalConverter.toEventDomainList(mapper.selectList(wrapper));
    }
}
