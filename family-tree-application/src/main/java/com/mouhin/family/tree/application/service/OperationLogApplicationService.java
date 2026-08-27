package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.OperationLogDTO;
import com.mouhin.family.tree.common.dto.PageResult;
import com.mouhin.family.tree.domain.entity.OperationLog;
import com.mouhin.family.tree.domain.repository.OperationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 操作日志应用服务
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Service
public class OperationLogApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(OperationLogApplicationService.class);

    private final OperationLogRepository operationLogRepository;

    public OperationLogApplicationService(OperationLogRepository operationLogRepository) {
        this.operationLogRepository = operationLogRepository;
    }

    /**
     * 记录操作日志。
     * 日志记录失败不应影响主业务流程，因此内部捕获异常并记录错误日志。
     *
     * @param userId        操作用户ID
     * @param username      操作用户名
     * @param operationType 操作类型
     * @param operationDesc 操作描述
     * @param targetType    操作对象类型
     * @param targetId      操作对象ID
     * @param familyId      家族ID
     * @param ipAddress     客户端IP
     */
    public void log(Long userId, String username, String operationType, String operationDesc,
                    String targetType, Long targetId, Long familyId, String ipAddress) {
        try {
            OperationLog logEntry = new OperationLog();
            logEntry.setUserId(userId);
            logEntry.setUsername(username);
            logEntry.setOperationType(operationType);
            logEntry.setOperationDesc(operationDesc);
            logEntry.setTargetType(targetType);
            logEntry.setTargetId(targetId);
            logEntry.setFamilyId(familyId);
            logEntry.setIpAddress(ipAddress);
            logEntry.setCreateTime(LocalDateTime.now());
            operationLogRepository.save(logEntry);
        } catch (Exception e) {
            // 日志记录失败不应影响主业务流程
            logger.error("Failed to write operation log: type={} user={} desc={}", operationType, username, operationDesc, e);
        }
    }

    /**
     * 分页查询操作日志
     *
     * @param familyId      家族ID
     * @param operationType 操作类型过滤（可选）
     * @param page          页码（从1开始）
     * @param size          每页大小
     * @return 分页结果
     */
    public PageResult<OperationLogDTO> listLogs(Long familyId, String operationType,
                                                int page, int size) {
        String trimmedType = (operationType != null && !operationType.isBlank())
                ? operationType.trim() : null;

        long total = operationLogRepository.countByFamilyId(familyId, trimmedType);

        if (total == 0) {
            return new PageResult<>(List.of(), 0L, page, size);
        }

        int offset = (page - 1) * size;
        List<OperationLog> records = operationLogRepository.findByFamilyId(
                familyId, trimmedType, offset, size);

        List<OperationLogDTO> dtoList = records.stream().map(this::toDTO).collect(Collectors.toList());

        return new PageResult<>(dtoList, total, page, size);
    }

    private OperationLogDTO toDTO(OperationLog logEntry) {
        OperationLogDTO dto = new OperationLogDTO();
        dto.setId(logEntry.getId());
        dto.setUserId(logEntry.getUserId());
        dto.setUsername(logEntry.getUsername());
        dto.setOperationType(logEntry.getOperationType());
        dto.setOperationDesc(logEntry.getOperationDesc());
        dto.setTargetType(logEntry.getTargetType());
        dto.setTargetId(logEntry.getTargetId());
        dto.setIpAddress(logEntry.getIpAddress());
        dto.setCreateTime(logEntry.getCreateTime());
        return dto;
    }
}
