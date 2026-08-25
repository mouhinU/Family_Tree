package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.family.tree.domain.entity.FamilyMember;
import com.mouhin.family.tree.domain.repository.FamilyMemberRepository;
import com.mouhin.family.tree.infrastructure.converter.FamilyMemberConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyMemberDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.FamilyMemberMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * FamilyMember 仓储实现
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Repository
public class FamilyMemberRepositoryImpl implements FamilyMemberRepository {

    private final FamilyMemberMapper mapper;

    public FamilyMemberRepositoryImpl(FamilyMemberMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public FamilyMember save(FamilyMember entity) {
        FamilyMemberDO doObj = FamilyMemberConverter.toDO(entity);
        mapper.insert(doObj);
        entity.setId(doObj.getId());
        return entity;
    }

    @Override
    public FamilyMember findByFamilyIdAndUserId(Long familyId, Long userId) {
        LambdaQueryWrapper<FamilyMemberDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyMemberDO::getFamilyId, familyId)
                .eq(FamilyMemberDO::getUserId, userId)
                .last("LIMIT 1");
        FamilyMemberDO doObj = mapper.selectOne(wrapper);
        return FamilyMemberConverter.toDomain(doObj);
    }

    @Override
    public List<FamilyMember> findByFamilyId(Long familyId) {
        LambdaQueryWrapper<FamilyMemberDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyMemberDO::getFamilyId, familyId);
        List<FamilyMemberDO> doList = mapper.selectList(wrapper);
        return FamilyMemberConverter.toDomainList(doList);
    }

    @Override
    public List<FamilyMember> findByUserId(Long userId) {
        LambdaQueryWrapper<FamilyMemberDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyMemberDO::getUserId, userId);
        List<FamilyMemberDO> doList = mapper.selectList(wrapper);
        return FamilyMemberConverter.toDomainList(doList);
    }

    @Override
    public void removeById(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public long countByFamilyId(Long familyId) {
        LambdaQueryWrapper<FamilyMemberDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyMemberDO::getFamilyId, familyId);
        return mapper.selectCount(wrapper);
    }

    @Override
    public void update(FamilyMember entity) {
        FamilyMemberDO doObj = FamilyMemberConverter.toDO(entity);
        mapper.updateById(doObj);
    }
}
