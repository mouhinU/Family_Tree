package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.family.tree.domain.entity.FamilyMessage;
import com.mouhin.family.tree.domain.repository.FamilyMessageRepository;
import com.mouhin.family.tree.infrastructure.converter.FamilyMessageConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyMessageDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.FamilyMessageMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * FamilyMessage 仓储实现
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Repository
public class FamilyMessageRepositoryImpl implements FamilyMessageRepository {

    private final FamilyMessageMapper mapper;

    public FamilyMessageRepositoryImpl(FamilyMessageMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public FamilyMessage save(FamilyMessage entity) {
        FamilyMessageDO doObj = FamilyMessageConverter.toDO(entity);
        mapper.insert(doObj);
        entity.setId(doObj.getId());
        return entity;
    }

    @Override
    public FamilyMessage findById(Long id) {
        FamilyMessageDO doObj = mapper.selectById(id);
        return FamilyMessageConverter.toDomain(doObj);
    }

    @Override
    public List<FamilyMessage> findByFamilyId(Long familyId, int offset, int size) {
        LambdaQueryWrapper<FamilyMessageDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyMessageDO::getFamilyId, familyId)
                .orderByDesc(FamilyMessageDO::getCreateTime)
                .last("LIMIT " + size + " OFFSET " + offset);
        List<FamilyMessageDO> doList = mapper.selectList(wrapper);
        return FamilyMessageConverter.toDomainList(doList);
    }

    @Override
    public long countByFamilyId(Long familyId) {
        LambdaQueryWrapper<FamilyMessageDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyMessageDO::getFamilyId, familyId);
        return mapper.selectCount(wrapper);
    }

    @Override
    public void removeById(Long id) {
        mapper.deleteById(id);
    }
}
