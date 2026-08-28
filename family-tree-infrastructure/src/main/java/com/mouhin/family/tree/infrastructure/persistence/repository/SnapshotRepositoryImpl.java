package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.family.tree.domain.entity.FamilySnapshot;
import com.mouhin.family.tree.domain.repository.SnapshotRepository;
import com.mouhin.family.tree.infrastructure.converter.FamilySnapshotConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilySnapshotDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.FamilySnapshotMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 快照仓储实现
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
@Repository
public class SnapshotRepositoryImpl implements SnapshotRepository {

    private final FamilySnapshotMapper mapper;

    public SnapshotRepositoryImpl(FamilySnapshotMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public FamilySnapshot save(FamilySnapshot snapshot) {
        FamilySnapshotDO doObj = FamilySnapshotConverter.toDO(snapshot);
        mapper.insert(doObj);
        snapshot.setId(doObj.getId());
        return snapshot;
    }

    @Override
    public FamilySnapshot findById(Long id) {
        FamilySnapshotDO doObj = mapper.selectById(id);
        return FamilySnapshotConverter.toDomain(doObj);
    }

    @Override
    public List<FamilySnapshot> findByFamilyId(Long familyId) {
        LambdaQueryWrapper<FamilySnapshotDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilySnapshotDO::getFamilyId, familyId)
                .orderByDesc(FamilySnapshotDO::getCreateTime);
        List<FamilySnapshotDO> doList = mapper.selectList(wrapper);
        return FamilySnapshotConverter.toDomainList(doList);
    }

    @Override
    public void removeById(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public long countByFamilyId(Long familyId) {
        LambdaQueryWrapper<FamilySnapshotDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilySnapshotDO::getFamilyId, familyId);
        return mapper.selectCount(wrapper);
    }
}
