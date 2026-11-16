package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.family.tree.domain.entity.FamilySetting;
import com.mouhin.family.tree.domain.repository.FamilySettingRepository;
import com.mouhin.family.tree.infrastructure.converter.FamilySettingConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilySettingDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.FamilySettingMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * FamilySetting 仓储实现
 *
 * @author Family-Tree
 * @date 2026-11-15
 */
@Repository
public class FamilySettingRepositoryImpl implements FamilySettingRepository {

    private final FamilySettingMapper mapper;

    public FamilySettingRepositoryImpl(FamilySettingMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<FamilySetting> findByFamilyId(Long familyId) {
        LambdaQueryWrapper<FamilySettingDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilySettingDO::getFamilyId, familyId)
                .orderByAsc(FamilySettingDO::getSettingKey);
        List<FamilySettingDO> doList = mapper.selectList(wrapper);
        return FamilySettingConverter.toDomainList(doList);
    }

    @Override
    public FamilySetting findByFamilyIdAndKey(Long familyId, String key) {
        LambdaQueryWrapper<FamilySettingDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilySettingDO::getFamilyId, familyId)
                .eq(FamilySettingDO::getSettingKey, key)
                .last("LIMIT 1");
        FamilySettingDO doObj = mapper.selectOne(wrapper);
        return FamilySettingConverter.toDomain(doObj);
    }

    @Override
    public FamilySetting save(FamilySetting setting) {
        FamilySettingDO doObj = FamilySettingConverter.toDO(setting);
        mapper.insert(doObj);
        setting.setId(doObj.getId());
        return setting;
    }

    @Override
    public void update(FamilySetting setting) {
        FamilySettingDO doObj = FamilySettingConverter.toDO(setting);
        mapper.updateById(doObj);
    }
}
