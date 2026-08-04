package com.mouhin.family.tree.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.family.tree.common.dto.OperationLogDTO;
import com.mouhin.family.tree.common.dto.PageResult;
import com.mouhin.family.tree.persistence.entity.OperationLogDO;
import com.mouhin.family.tree.persistence.mapper.OperationLogMapper;
import com.mouhin.family.tree.service.OperationLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 操作日志服务实现类
 *
 * @author Family-Tree
 * @date 2026-08-03
 */
@Service
public class OperationLogServiceImpl implements OperationLogService {

    private static final Logger logger = LoggerFactory.getLogger(OperationLogServiceImpl.class);

    private final OperationLogMapper operationLogMapper;

    public OperationLogServiceImpl(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Override
    public void log(Long userId, String username, String operationType, String operationDesc,
                    String targetType, Long targetId, Long familyId, String ipAddress) {
        try {
            OperationLogDO logDO = new OperationLogDO();
            logDO.setUserId(userId);
            logDO.setUsername(username);
            logDO.setOperationType(operationType);
            logDO.setOperationDesc(operationDesc);
            logDO.setTargetType(targetType);
            logDO.setTargetId(targetId);
            logDO.setFamilyId(familyId);
            logDO.setIpAddress(ipAddress);
            logDO.setCreateTime(LocalDateTime.now());
            operationLogMapper.insert(logDO);
        } catch (Exception e) {
            // 日志记录失败不应影响主业务流程
            logger.error("Failed to write operation log: type={} user={} desc={}", operationType, username, operationDesc, e);
        }
    }

    @Override
    public PageResult<OperationLogDTO> listLogs(Long familyId, String operationType, int page, int size) {
        // 构建基础条件（不含 ORDER BY，用于 count）
        LambdaQueryWrapper<OperationLogDO> baseQuery = new LambdaQueryWrapper<>();
        baseQuery.eq(OperationLogDO::getFamilyId, familyId);
        if (operationType != null && !operationType.isBlank()) {
            baseQuery.eq(OperationLogDO::getOperationType, operationType.trim());
        }

        long total = operationLogMapper.selectCount(baseQuery);

        // 查询分页数据（含 ORDER BY + LIMIT/OFFSET）
        LambdaQueryWrapper<OperationLogDO> dataQuery = new LambdaQueryWrapper<>();
        dataQuery.eq(OperationLogDO::getFamilyId, familyId);
        if (operationType != null && !operationType.isBlank()) {
            dataQuery.eq(OperationLogDO::getOperationType, operationType.trim());
        }
        dataQuery.orderByDesc(OperationLogDO::getCreateTime);
        int offset = (page - 1) * size;
        dataQuery.last("LIMIT " + size + " OFFSET " + offset);
        List<OperationLogDO> records = operationLogMapper.selectList(dataQuery);

        List<OperationLogDTO> dtoList = records.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return new PageResult<>(dtoList, total, page, size);
    }

    private OperationLogDTO toDTO(OperationLogDO logDO) {
        OperationLogDTO dto = new OperationLogDTO();
        dto.setId(logDO.getId());
        dto.setUserId(logDO.getUserId());
        dto.setUsername(logDO.getUsername());
        dto.setOperationType(logDO.getOperationType());
        dto.setOperationDesc(logDO.getOperationDesc());
        dto.setTargetType(logDO.getTargetType());
        dto.setTargetId(logDO.getTargetId());
        dto.setIpAddress(logDO.getIpAddress());
        dto.setCreateTime(logDO.getCreateTime());
        return dto;
    }
}
