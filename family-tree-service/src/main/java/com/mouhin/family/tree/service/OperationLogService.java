package com.mouhin.family.tree.service;

import com.mouhin.family.tree.common.dto.OperationLogDTO;
import com.mouhin.family.tree.common.dto.PageResult;

/**
 * 操作日志服务接口
 *
 * @author Family-Tree
 * @date 2026-08-03
 */
public interface OperationLogService {

    /**
     * 记录操作日志
     *
     * @param userId       操作用户ID（可为 null，如登录失败时）
     * @param username     操作用户名
     * @param operationType 操作类型（LOGIN / REGISTER / LOGOUT / NODE_CREATE 等）
     * @param operationDesc 操作描述
     * @param targetType   操作对象类型（node / relation / family / user，可为 null）
     * @param targetId     操作对象ID（可为 null）
     * @param familyId     所属家族ID（可为 null）
     * @param ipAddress    客户端IP（可为 null）
     */
    void log(Long userId, String username, String operationType, String operationDesc,
             String targetType, Long targetId, Long familyId, String ipAddress);

    /**
     * 分页查询操作日志
     *
     * @param familyId      家族ID
     * @param operationType 操作类型筛选条件（可为 null 表示全部）
     * @param page          页码（从 1 开始）
     * @param size          每页大小
     * @return 分页结果
     */
    PageResult<OperationLogDTO> listLogs(Long familyId, String operationType, int page, int size);
}
