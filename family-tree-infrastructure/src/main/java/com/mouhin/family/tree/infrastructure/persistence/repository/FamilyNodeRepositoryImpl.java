package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.infrastructure.converter.FamilyNodeConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyNodeDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.FamilyNodeMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * FamilyNode 仓储实现
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Repository
public class FamilyNodeRepositoryImpl implements FamilyNodeRepository {

    private final FamilyNodeMapper mapper;

    public FamilyNodeRepositoryImpl(FamilyNodeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public FamilyNode save(FamilyNode entity) {
        FamilyNodeDO doObj = FamilyNodeConverter.toDO(entity);
        mapper.insert(doObj);
        entity.setId(doObj.getId());
        return entity;
    }

    @Override
    public FamilyNode findById(Long id) {
        FamilyNodeDO doObj = mapper.selectById(id);
        return FamilyNodeConverter.toDomain(doObj);
    }

    @Override
    public List<FamilyNode> findByFamilyId(Long familyId) {
        LambdaQueryWrapper<FamilyNodeDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyNodeDO::getFamilyId, familyId);
        List<FamilyNodeDO> doList = mapper.selectList(wrapper);
        return FamilyNodeConverter.toDomainList(doList);
    }

    @Override
    public List<FamilyNode> findByFamilyIdAndNameContaining(Long familyId, String keyword) {
        LambdaQueryWrapper<FamilyNodeDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyNodeDO::getFamilyId, familyId)
                .and(w -> w.like(FamilyNodeDO::getName, keyword)
                        .or().like(FamilyNodeDO::getZi, keyword)
                        .or().like(FamilyNodeDO::getHao, keyword)
                        .or().like(FamilyNodeDO::getHui, keyword));
        List<FamilyNodeDO> doList = mapper.selectList(wrapper);
        return FamilyNodeConverter.toDomainList(doList);
    }

    @Override
    public void update(FamilyNode entity) {
        FamilyNodeDO doObj = FamilyNodeConverter.toDO(entity);
        mapper.updateById(doObj);
    }

    @Override
    public void removeById(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public void removeByFamilyId(Long familyId) {
        LambdaQueryWrapper<FamilyNodeDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyNodeDO::getFamilyId, familyId);
        mapper.delete(wrapper);
    }

    @Override
    public List<FamilyNode> findByIds(List<Long> ids) {
        List<FamilyNodeDO> doList = mapper.selectBatchIds(ids);
        return FamilyNodeConverter.toDomainList(doList);
    }

    @Override
    public void batchUpdateGeneration(List<FamilyNode> nodes) {
        for (FamilyNode node : nodes) {
            LambdaUpdateWrapper<FamilyNodeDO> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(FamilyNodeDO::getId, node.getId())
                    .set(FamilyNodeDO::getGeneration, node.getGeneration());
            mapper.update(null, wrapper);
        }
    }

    @Override
    public List<FamilyNode> findDeceasedByFamilyId(Long familyId) {
        LambdaQueryWrapper<FamilyNodeDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyNodeDO::getFamilyId, familyId)
                .isNotNull(FamilyNodeDO::getDeathDate);
        List<FamilyNodeDO> doList = mapper.selectList(wrapper);
        return FamilyNodeConverter.toDomainList(doList);
    }

    @Override
    public long countByFamilyId(Long familyId) {
        LambdaQueryWrapper<FamilyNodeDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyNodeDO::getFamilyId, familyId);
        return mapper.selectCount(wrapper);
    }

    @Override
    public void updateFamilyIdByUserId(Long userId, Long newFamilyId) {
        LambdaUpdateWrapper<FamilyNodeDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.set(FamilyNodeDO::getFamilyId, newFamilyId);
        wrapper.eq(FamilyNodeDO::getUserId, userId);
        mapper.update(null, wrapper);
    }

    @Override
    public void updateColorLabel(Long familyId, List<Long> nodeIds, String colorLabel) {
        LambdaUpdateWrapper<FamilyNodeDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.set(FamilyNodeDO::getColorLabel, colorLabel);
        wrapper.eq(FamilyNodeDO::getFamilyId, familyId);
        wrapper.in(FamilyNodeDO::getId, nodeIds);
        mapper.update(null, wrapper);
    }
}
