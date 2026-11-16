package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.family.tree.domain.entity.PlatformSetting;
import com.mouhin.family.tree.domain.repository.PlatformSettingRepository;
import com.mouhin.family.tree.infrastructure.converter.PlatformSettingConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.PlatformSettingDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.PlatformSettingMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * PlatformSetting 仓储实现
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Repository
public class PlatformSettingRepositoryImpl implements PlatformSettingRepository {

    private final PlatformSettingMapper mapper;

    public PlatformSettingRepositoryImpl(PlatformSettingMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<PlatformSetting> findAll() {
        LambdaQueryWrapper<PlatformSettingDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(PlatformSettingDO::getSettingKey);
        return PlatformSettingConverter.toDomainList(mapper.selectList(wrapper));
    }

    @Override
    public PlatformSetting findByKey(String key) {
        LambdaQueryWrapper<PlatformSettingDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlatformSettingDO::getSettingKey, key)
                .last("LIMIT 1");
        return PlatformSettingConverter.toDomain(mapper.selectOne(wrapper));
    }

    @Override
    public PlatformSetting save(PlatformSetting setting) {
        PlatformSettingDO doObj = PlatformSettingConverter.toDO(setting);
        mapper.insert(doObj);
        setting.setId(doObj.getId());
        return setting;
    }

    @Override
    public void update(PlatformSetting setting) {
        PlatformSettingDO doObj = PlatformSettingConverter.toDO(setting);
        mapper.updateById(doObj);
    }

    @Override
    public void removeByKey(String key) {
        LambdaQueryWrapper<PlatformSettingDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlatformSettingDO::getSettingKey, key);
        mapper.delete(wrapper);
    }
}
