package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.family.tree.domain.entity.Family;
import com.mouhin.family.tree.domain.repository.FamilyRepository;
import com.mouhin.family.tree.infrastructure.converter.FamilyConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.FamilyMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    @Override
    public List<Family> listAll() {
        LambdaQueryWrapper<FamilyDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(FamilyDO::getId);
        return FamilyConverter.toDomainList(mapper.selectList(wrapper));
    }

    @Override
    public List<Family> listPaged(String keyword, int offset, int size) {
        LambdaQueryWrapper<FamilyDO> wrapper = keywordWrapper(keyword)
                .orderByDesc(FamilyDO::getCreateTime)
                .orderByDesc(FamilyDO::getId)
                .last("LIMIT " + size + " OFFSET " + offset);
        List<FamilyDO> doList = mapper.selectList(wrapper);
        return FamilyConverter.toDomainList(doList);
    }

    @Override
    public long countByKeyword(String keyword) {
        // H2 严格模式下 selectCount 不允许携带 ORDER BY，计数使用无条件包装器
        return mapper.selectCount(keywordWrapper(keyword));
    }

    @Override
    public long countAll() {
        return mapper.selectCount(new LambdaQueryWrapper<>());
    }

    @Override
    public long countCreatedAfter(LocalDateTime since) {
        LambdaQueryWrapper<FamilyDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(FamilyDO::getCreateTime, since);
        return mapper.selectCount(wrapper);
    }

    /**
     * 构建关键字条件包装器：名称/堂号走前缀匹配（可用索引），邀请码走精确匹配
     *
     * @param keyword 关键字，空白时不加条件
     * @return 未含排序与分页条件的包装器
     */
    private LambdaQueryWrapper<FamilyDO> keywordWrapper(String keyword) {
        LambdaQueryWrapper<FamilyDO> wrapper = new LambdaQueryWrapper<>();
        if (keyword == null || keyword.isBlank()) {
            return wrapper;
        }
        String trimmed = keyword.trim();
        wrapper.and(w -> w.likeRight(FamilyDO::getName, trimmed)
                .or().likeRight(FamilyDO::getHallName, trimmed)
                .or().eq(FamilyDO::getInviteCode, trimmed));
        return wrapper;
    }
}
