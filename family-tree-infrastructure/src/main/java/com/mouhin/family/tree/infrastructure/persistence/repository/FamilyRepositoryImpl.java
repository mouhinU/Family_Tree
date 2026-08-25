package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.family.tree.domain.entity.Family;
import com.mouhin.family.tree.domain.repository.FamilyRepository;
import com.mouhin.family.tree.infrastructure.converter.FamilyConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.FamilyMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Family 仓储实现
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Repository
public class FamilyRepositoryImpl implements FamilyRepository {

    private final FamilyMapper mapper;

    public FamilyRepositoryImpl(FamilyMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Family save(Family entity) {
        FamilyDO doObj = FamilyConverter.toDO(entity);
        mapper.insert(doObj);
        entity.setId(doObj.getId());
        return entity;
    }

    @Override
    public Family findById(Long id) {
        FamilyDO doObj = mapper.selectById(id);
        return FamilyConverter.toDomain(doObj);
    }

    @Override
    public Family findByInviteCode(String inviteCode) {
        LambdaQueryWrapper<FamilyDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyDO::getInviteCode, inviteCode)
                .last("LIMIT 1");
        FamilyDO doObj = mapper.selectOne(wrapper);
        return FamilyConverter.toDomain(doObj);
    }

    @Override
    public List<Family> findByIds(List<Long> ids) {
        List<FamilyDO> doList = mapper.selectBatchIds(ids);
        return FamilyConverter.toDomainList(doList);
    }

    @Override
    public void update(Family entity) {
        FamilyDO doObj = FamilyConverter.toDO(entity);
        mapper.updateById(doObj);
    }
}
