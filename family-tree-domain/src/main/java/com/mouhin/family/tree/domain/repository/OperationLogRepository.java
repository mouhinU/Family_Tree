package com.mouhin.family.tree.domain.repository;

import com.mouhin.family.tree.domain.entity.OperationLog;

import java.util.List;

/**
 * 操作日志仓储接口
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
public interface OperationLogRepository {

    /**
     * 保存操作日志
     *
     * @param log 日志领域对象
     */
    void save(OperationLog log);

    /**
     * 根据家族ID和操作类型分页查询日志
     *
     * @param familyId      家族ID
     * @param operationType 操作类型（可为null表示查全部）
     * @param offset        偏移量
     * @param limit         每页数量
     * @return 日志列表
     */
    List<OperationLog> findByFamilyId(Long familyId, String operationType,
                                       int offset, int limit);

    /**
     * 统计家族操作日志数量
     *
     * @param familyId      家族ID
     * @param operationType 操作类型（可为null表示统计全部）
     * @return 日志数量
     */
    long countByFamilyId(Long familyId, String operationType);
}
