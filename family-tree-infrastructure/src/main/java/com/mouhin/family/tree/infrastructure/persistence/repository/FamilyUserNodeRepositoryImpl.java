package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.family.tree.domain.entity.FamilyUserNode;
import com.mouhin.family.tree.domain.repository.FamilyUserNodeRepository;
import com.mouhin.family.tree.infrastructure.converter.FamilyUserNodeConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyUserNodeDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.FamilyUserNodeMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * FamilyUserNode 仓储实现
 *
 * @author Family-Tree
 * @date 2026-11-05
 */
@Repository
public class FamilyUserNodeRepositoryImpl implements FamilyUserNodeRepository {

    private final FamilyUserNodeMapper mapper;

    public FamilyUserNodeRepositoryImpl(FamilyUserNodeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public FamilyUserNode save(FamilyUserNode entity) {
        FamilyUserNodeDO doObj = FamilyUserNodeConverter.toDO(entity);
        mapper.insert(doObj);
        entity.setId(doObj.getId());
        return entity;
    }

    @Override
    public void update(FamilyUserNode entity) {
        FamilyUserNodeDO doObj = FamilyUserNodeConverter.toDO(entity);
        mapper.updateById(doObj);
    }

    @Override
    public FamilyUserNode findByFamilyIdAndUserId(Long familyId, Long userId) {
        LambdaQueryWrapper<FamilyUserNodeDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyUserNodeDO::getFamilyId, familyId)
                .eq(FamilyUserNodeDO::getUserId, userId)
                .last("LIMIT 1");
        FamilyUserNodeDO doObj = mapper.selectOne(wrapper);
        return FamilyUserNodeConverter.toDomain(doObj);
    }

    @Override
    public FamilyUserNode findByFamilyIdAndNodeId(Long familyId, Long nodeId) {
        LambdaQueryWrapper<FamilyUserNodeDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyUserNodeDO::getFamilyId, familyId)
                .eq(FamilyUserNodeDO::getNodeId, nodeId)
                .last("LIMIT 1");
        FamilyUserNodeDO doObj = mapper.selectOne(wrapper);
        return FamilyUserNodeConverter.toDomain(doObj);
    }

    @Override
    public List<FamilyUserNode> findByFamilyId(Long familyId) {
        LambdaQueryWrapper<FamilyUserNodeDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyUserNodeDO::getFamilyId, familyId);
        List<FamilyUserNodeDO> doList = mapper.selectList(wrapper);
        return FamilyUserNodeConverter.toDomainList(doList);
    }

    @Override
    public void removeByFamilyIdAndUserId(Long familyId, Long userId) {
        LambdaQueryWrapper<FamilyUserNodeDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyUserNodeDO::getFamilyId, familyId)
                .eq(FamilyUserNodeDO::getUserId, userId);
        mapper.delete(wrapper);
    }

    @Override
    public void removeByFamilyId(Long familyId) {
        LambdaQueryWrapper<FamilyUserNodeDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyUserNodeDO::getFamilyId, familyId);
        mapper.delete(wrapper);
    }
}
