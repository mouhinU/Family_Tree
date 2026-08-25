package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mouhin.family.tree.domain.entity.FamilyGeneration;
import com.mouhin.family.tree.domain.repository.FamilyGenerationRepository;
import com.mouhin.family.tree.infrastructure.converter.FamilyGenerationConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyGenerationDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.FamilyGenerationMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * FamilyGeneration 仓储实现
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Repository
public class FamilyGenerationRepositoryImpl implements FamilyGenerationRepository {

    private final FamilyGenerationMapper mapper;

    public FamilyGenerationRepositoryImpl(FamilyGenerationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public FamilyGeneration save(FamilyGeneration entity) {
        FamilyGenerationDO doObj = FamilyGenerationConverter.toDO(entity);
        mapper.insert(doObj);
        entity.setId(doObj.getId());
        return entity;
    }

    @Override
    public List<FamilyGeneration> findByFamilyId(Long familyId) {
        LambdaQueryWrapper<FamilyGenerationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyGenerationDO::getFamilyId, familyId)
                .orderByAsc(FamilyGenerationDO::getGeneration);
        List<FamilyGenerationDO> doList = mapper.selectList(wrapper);
        return FamilyGenerationConverter.toDomainList(doList);
    }

    @Override
    public FamilyGeneration findByFamilyIdAndGeneration(Long familyId, Integer generation) {
        LambdaQueryWrapper<FamilyGenerationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyGenerationDO::getFamilyId, familyId)
                .eq(FamilyGenerationDO::getGeneration, generation)
                .last("LIMIT 1");
        FamilyGenerationDO doObj = mapper.selectOne(wrapper);
        return FamilyGenerationConverter.toDomain(doObj);
    }

    @Override
    public void update(FamilyGeneration entity) {
        FamilyGenerationDO doObj = FamilyGenerationConverter.toDO(entity);
        mapper.updateById(doObj);
    }

    @Override
    public void removeById(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public void updateFamilyIdByUserId(Long userId, Long newFamilyId) {
        LambdaUpdateWrapper<FamilyGenerationDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.set(FamilyGenerationDO::getFamilyId, newFamilyId);
        wrapper.eq(FamilyGenerationDO::getUserId, userId);
        mapper.update(null, wrapper);
    }
}
