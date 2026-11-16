package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.family.tree.domain.entity.OperationLog;
import com.mouhin.family.tree.domain.repository.OperationLogRepository;
import com.mouhin.family.tree.infrastructure.converter.OperationLogConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.OperationLogDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.OperationLogMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

    @Override
    public List<OperationLog> listByFilter(Long familyId, String operationType, String keyword,
                                           LocalDateTime startTime, LocalDateTime endTime,
                                           int offset, int limit) {
        LambdaQueryWrapper<OperationLogDO> wrapper = filterWrapper(
                familyId, operationType, keyword, startTime, endTime);
        wrapper.orderByDesc(OperationLogDO::getCreateTime)
                .orderByDesc(OperationLogDO::getId)
                .last("LIMIT " + limit + " OFFSET " + offset);
        return OperationLogConverter.toDomainList(mapper.selectList(wrapper));
    }

    @Override
    public long countByFilter(Long familyId, String operationType, String keyword,
                              LocalDateTime startTime, LocalDateTime endTime) {
        return mapper.selectCount(filterWrapper(familyId, operationType, keyword, startTime, endTime));
    }

    @Override
    public List<String> listDistinctOperationTypes() {
        LambdaQueryWrapper<OperationLogDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(OperationLogDO::getOperationType)
                .groupBy(OperationLogDO::getOperationType)
                .orderByAsc(OperationLogDO::getOperationType);
        List<OperationLogDO> rows = mapper.selectList(wrapper);
        List<String> types = new ArrayList<>(rows.size());
        for (OperationLogDO row : rows) {
            if (row.getOperationType() != null) {
                types.add(row.getOperationType());
            }
        }
        return types;
    }

    /**
     * 组装平台日志检索条件：家族与类型为等值，文本走右模糊，时间走闭区间。
     * 排序只在列表查询里追加，计数必须保持无序以免 H2 严格模式报错。
     */
    private LambdaQueryWrapper<OperationLogDO> filterWrapper(Long familyId, String operationType, String keyword,
                                                             LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<OperationLogDO> wrapper = new LambdaQueryWrapper<>();
        if (familyId != null) {
            wrapper.eq(OperationLogDO::getFamilyId, familyId);
        }
        if (operationType != null && !operationType.isBlank()) {
            wrapper.eq(OperationLogDO::getOperationType, operationType.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            wrapper.and(w -> w.likeRight(OperationLogDO::getUsername, trimmed)
                    .or().likeRight(OperationLogDO::getOperationDesc, trimmed));
        }
        if (startTime != null) {
            wrapper.ge(OperationLogDO::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(OperationLogDO::getCreateTime, endTime);
        }
        return wrapper;
    }
}
