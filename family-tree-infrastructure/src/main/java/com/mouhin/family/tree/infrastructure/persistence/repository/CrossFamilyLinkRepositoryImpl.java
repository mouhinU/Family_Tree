package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.family.tree.domain.entity.CrossFamilyLink;
import com.mouhin.family.tree.domain.repository.CrossFamilyLinkRepository;
import com.mouhin.family.tree.infrastructure.converter.CrossFamilyLinkConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyNodeLinkDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.FamilyNodeLinkMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * CrossFamilyLink 仓储实现
 *
 * @author Family-Tree
 * @date 2026-11-20
 */
@Repository
public class CrossFamilyLinkRepositoryImpl implements CrossFamilyLinkRepository {

    private final FamilyNodeLinkMapper mapper;

    public CrossFamilyLinkRepositoryImpl(FamilyNodeLinkMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public CrossFamilyLink save(CrossFamilyLink entity) {
        FamilyNodeLinkDO doObj = CrossFamilyLinkConverter.toDO(entity);
        mapper.insert(doObj);
        entity.setId(doObj.getId());
        return entity;
    }

    @Override
    public void update(CrossFamilyLink entity) {
        FamilyNodeLinkDO doObj = CrossFamilyLinkConverter.toDO(entity);
        mapper.updateById(doObj);
    }

    @Override
    public CrossFamilyLink findById(Long id) {
        return CrossFamilyLinkConverter.toDomain(mapper.selectById(id));
    }

    @Override
    public List<CrossFamilyLink> findByNodeId(Long nodeId) {
        LambdaQueryWrapper<FamilyNodeLinkDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(FamilyNodeLinkDO::getSrcNodeId, nodeId)
                .or().eq(FamilyNodeLinkDO::getDstNodeId, nodeId));
        return CrossFamilyLinkConverter.toDomainList(mapper.selectList(wrapper));
    }

    @Override
    public CrossFamilyLink findByNodePair(Long nodeA, Long nodeB) {
        if (nodeA == null || nodeB == null) {
            return null;
        }
        long srcId = Math.min(nodeA, nodeB);
        long dstId = Math.max(nodeA, nodeB);
        LambdaQueryWrapper<FamilyNodeLinkDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyNodeLinkDO::getSrcNodeId, srcId)
                .eq(FamilyNodeLinkDO::getDstNodeId, dstId)
                .last("LIMIT 1");
        return CrossFamilyLinkConverter.toDomain(mapper.selectOne(wrapper));
    }

    @Override
    public List<CrossFamilyLink> findByFamilyId(Long familyId) {
        LambdaQueryWrapper<FamilyNodeLinkDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(FamilyNodeLinkDO::getSrcFamilyId, familyId)
                .or().eq(FamilyNodeLinkDO::getDstFamilyId, familyId));
        return CrossFamilyLinkConverter.toDomainList(mapper.selectList(wrapper));
    }

    @Override
    public List<CrossFamilyLink> listAll() {
        LambdaQueryWrapper<FamilyNodeLinkDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(FamilyNodeLinkDO::getId);
        return CrossFamilyLinkConverter.toDomainList(mapper.selectList(wrapper));
    }

    @Override
    public void removeById(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public void removeByNodeId(Long nodeId) {
        LambdaQueryWrapper<FamilyNodeLinkDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(FamilyNodeLinkDO::getSrcNodeId, nodeId)
                .or().eq(FamilyNodeLinkDO::getDstNodeId, nodeId));
        mapper.delete(wrapper);
    }

    @Override
    public void removeByFamilyId(Long familyId) {
        LambdaQueryWrapper<FamilyNodeLinkDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(FamilyNodeLinkDO::getSrcFamilyId, familyId)
                .or().eq(FamilyNodeLinkDO::getDstFamilyId, familyId));
        mapper.delete(wrapper);
    }
}
