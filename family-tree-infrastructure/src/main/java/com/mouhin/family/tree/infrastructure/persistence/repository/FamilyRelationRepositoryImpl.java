package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mouhin.family.tree.domain.entity.FamilyRelation;
import com.mouhin.family.tree.domain.repository.FamilyRelationRepository;
import com.mouhin.family.tree.infrastructure.converter.FamilyRelationConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyRelationDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.FamilyRelationMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * FamilyRelation 仓储实现
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Repository
public class FamilyRelationRepositoryImpl implements FamilyRelationRepository {

    private final FamilyRelationMapper mapper;

    public FamilyRelationRepositoryImpl(FamilyRelationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public FamilyRelation save(FamilyRelation entity) {
        FamilyRelationDO doObj = FamilyRelationConverter.toDO(entity);
        mapper.insert(doObj);
        entity.setId(doObj.getId());
        return entity;
    }

    @Override
    public void update(FamilyRelation entity) {
        FamilyRelationDO doObj = FamilyRelationConverter.toDO(entity);
        mapper.updateById(doObj);
    }

    @Override
    public FamilyRelation findById(Long id) {
        FamilyRelationDO doObj = mapper.selectById(id);
        return FamilyRelationConverter.toDomain(doObj);
    }

    @Override
    public List<FamilyRelation> findByFamilyId(Long familyId) {
        LambdaQueryWrapper<FamilyRelationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyRelationDO::getFamilyId, familyId);
        List<FamilyRelationDO> doList = mapper.selectList(wrapper);
        return FamilyRelationConverter.toDomainList(doList);
    }

    @Override
    public List<FamilyRelation> findByNodeId(Long familyId, Long nodeId) {
        LambdaQueryWrapper<FamilyRelationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyRelationDO::getFamilyId, familyId)
                .and(w -> w.eq(FamilyRelationDO::getFromNodeId, nodeId)
                        .or().eq(FamilyRelationDO::getToNodeId, nodeId));
        List<FamilyRelationDO> doList = mapper.selectList(wrapper);
        return FamilyRelationConverter.toDomainList(doList);
    }

    @Override
    public void removeById(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public void removeByNodeId(Long familyId, Long nodeId) {
        LambdaQueryWrapper<FamilyRelationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyRelationDO::getFamilyId, familyId)
                .and(w -> w.eq(FamilyRelationDO::getFromNodeId, nodeId)
                        .or().eq(FamilyRelationDO::getToNodeId, nodeId));
        mapper.delete(wrapper);
    }

    @Override
    public List<FamilyRelation> findByFromAndToAndType(Long familyId, Long fromNodeId,
                                                        Long toNodeId, Integer relationType) {
        LambdaQueryWrapper<FamilyRelationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyRelationDO::getFamilyId, familyId)
                .eq(FamilyRelationDO::getFromNodeId, fromNodeId)
                .eq(FamilyRelationDO::getToNodeId, toNodeId)
                .eq(FamilyRelationDO::getRelationType, relationType);
        List<FamilyRelationDO> doList = mapper.selectList(wrapper);
        return FamilyRelationConverter.toDomainList(doList);
    }

    @Override
    public void updateFamilyIdByUserId(Long userId, Long newFamilyId) {
        LambdaUpdateWrapper<FamilyRelationDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.set(FamilyRelationDO::getFamilyId, newFamilyId);
        wrapper.eq(FamilyRelationDO::getUserId, userId);
        mapper.update(null, wrapper);
    }

    @Override
    public List<FamilyRelation> findSpouseRelations(Long familyId, Long nodeId) {
        LambdaQueryWrapper<FamilyRelationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyRelationDO::getFamilyId, familyId);
        wrapper.eq(FamilyRelationDO::getRelationType, 2);
        wrapper.and(w -> w.eq(FamilyRelationDO::getFromNodeId, nodeId)
                .or()
                .eq(FamilyRelationDO::getToNodeId, nodeId));
        List<FamilyRelationDO> doList = mapper.selectList(wrapper);
        return FamilyRelationConverter.toDomainList(doList);
    }

    @Override
    public long countChildren(Long familyId, Long nodeId) {
        LambdaQueryWrapper<FamilyRelationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyRelationDO::getFamilyId, familyId);
        wrapper.eq(FamilyRelationDO::getFromNodeId, nodeId);
        wrapper.eq(FamilyRelationDO::getRelationType, 1);
        Long count = mapper.selectCount(wrapper);
        return count != null ? count : 0L;
    }

    @Override
    public boolean existsRelation(Long familyId, Long fromNodeId, Long toNodeId,
                                   Integer relationType) {
        LambdaQueryWrapper<FamilyRelationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyRelationDO::getFamilyId, familyId);
        wrapper.eq(FamilyRelationDO::getFromNodeId, fromNodeId);
        wrapper.eq(FamilyRelationDO::getToNodeId, toNodeId);
        wrapper.eq(FamilyRelationDO::getRelationType, relationType);
        Long count = mapper.selectCount(wrapper);
        return count != null && count > 0;
    }
}
