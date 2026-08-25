package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.family.tree.domain.entity.OperationLog;
import com.mouhin.family.tree.domain.repository.OperationLogRepository;
import com.mouhin.family.tree.infrastructure.converter.OperationLogConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.OperationLogDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.OperationLogMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * OperationLog 仓储实现
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Repository
public class OperationLogRepositoryImpl implements OperationLogRepository {

    private final OperationLogMapper mapper;

    public OperationLogRepositoryImpl(OperationLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(OperationLog entity) {
        OperationLogDO doObj = OperationLogConverter.toDO(entity);
        mapper.insert(doObj);
        entity.setId(doObj.getId());
    }

    @Override
    public List<OperationLog> findByFamilyId(Long familyId, String operationType,
                                              int offset, int size) {
        LambdaQueryWrapper<OperationLogDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OperationLogDO::getFamilyId, familyId);
        if (operationType != null && !operationType.isEmpty()) {
            wrapper.eq(OperationLogDO::getOperationType, operationType);
        }
        wrapper.orderByDesc(OperationLogDO::getCreateTime)
                .last("LIMIT " + size + " OFFSET " + offset);
        List<OperationLogDO> doList = mapper.selectList(wrapper);
        return OperationLogConverter.toDomainList(doList);
    }

    @Override
    public long countByFamilyId(Long familyId, String operationType) {
        LambdaQueryWrapper<OperationLogDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OperationLogDO::getFamilyId, familyId);
        if (operationType != null && !operationType.isEmpty()) {
            wrapper.eq(OperationLogDO::getOperationType, operationType);
        }
        return mapper.selectCount(wrapper);
    }
}
