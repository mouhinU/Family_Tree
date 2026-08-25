package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mouhin.family.tree.domain.entity.FamilyOffering;
import com.mouhin.family.tree.domain.repository.FamilyOfferingRepository;
import com.mouhin.family.tree.infrastructure.converter.FamilyOfferingConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyOfferingDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.FamilyOfferingMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * FamilyOffering 仓储实现
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Repository
public class FamilyOfferingRepositoryImpl implements FamilyOfferingRepository {

    private final FamilyOfferingMapper mapper;

    public FamilyOfferingRepositoryImpl(FamilyOfferingMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public FamilyOffering save(FamilyOffering entity) {
        FamilyOfferingDO doObj = FamilyOfferingConverter.toDO(entity);
        mapper.insert(doObj);
        entity.setId(doObj.getId());
        return entity;
    }

    @Override
    public List<FamilyOffering> findByNodeId(Long familyId, Long nodeId) {
        LambdaQueryWrapper<FamilyOfferingDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyOfferingDO::getFamilyId, familyId)
                .eq(FamilyOfferingDO::getNodeId, nodeId);
        List<FamilyOfferingDO> doList = mapper.selectList(wrapper);
        return FamilyOfferingConverter.toDomainList(doList);
    }

    @Override
    public long countByFamilyId(Long familyId) {
        LambdaQueryWrapper<FamilyOfferingDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyOfferingDO::getFamilyId, familyId);
        return mapper.selectCount(wrapper);
    }

    @Override
    public void updateFamilyIdByUserId(Long userId, Long newFamilyId) {
        LambdaUpdateWrapper<FamilyOfferingDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.set(FamilyOfferingDO::getFamilyId, newFamilyId);
        wrapper.eq(FamilyOfferingDO::getUserId, userId);
        mapper.update(null, wrapper);
    }
}
