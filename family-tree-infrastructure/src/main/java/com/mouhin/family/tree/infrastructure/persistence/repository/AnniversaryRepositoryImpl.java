package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.family.tree.domain.entity.FamilyAnniversary;
import com.mouhin.family.tree.domain.repository.AnniversaryRepository;
import com.mouhin.family.tree.infrastructure.converter.FamilyAnniversaryConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyAnniversaryDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.FamilyAnniversaryMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Anniversary 仓储实现
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Repository
public class AnniversaryRepositoryImpl implements AnniversaryRepository {

    private final FamilyAnniversaryMapper mapper;

    public AnniversaryRepositoryImpl(FamilyAnniversaryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public FamilyAnniversary save(FamilyAnniversary anniversary) {
        FamilyAnniversaryDO doObj = FamilyAnniversaryConverter.toDO(anniversary);
        mapper.insert(doObj);
        anniversary.setId(doObj.getId());
        return anniversary;
    }

    @Override
    public FamilyAnniversary findById(Long id) {
        FamilyAnniversaryDO doObj = mapper.selectById(id);
        return FamilyAnniversaryConverter.toDomain(doObj);
    }

    @Override
    public List<FamilyAnniversary> findByFamilyId(Long familyId) {
        LambdaQueryWrapper<FamilyAnniversaryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyAnniversaryDO::getFamilyId, familyId)
                .orderByAsc(FamilyAnniversaryDO::getAnniversaryDate);
        List<FamilyAnniversaryDO> doList = mapper.selectList(wrapper);
        return FamilyAnniversaryConverter.toDomainList(doList);
    }

    @Override
    public void update(FamilyAnniversary anniversary) {
        FamilyAnniversaryDO doObj = FamilyAnniversaryConverter.toDO(anniversary);
        mapper.updateById(doObj);
    }

    @Override
    public void removeById(Long id) {
        mapper.deleteById(id);
    }
}
