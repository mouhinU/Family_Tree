package com.mouhin.family.tree.domain.repository;

import com.mouhin.family.tree.domain.entity.OperationLog;

import java.time.LocalDateTime;
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

    /**
     * 平台级分页检索操作日志：跨家族，各条件为空时表示不过滤
     *
     * @param familyId      家族ID，null 表示全部家族
     * @param operationType 操作类型，空串或 null 表示全部
     * @param keyword       用户名 / 操作描述的右模糊前缀，可为空
     * @param startTime     操作时间下界（含），可为 null
     * @param endTime       操作时间上界（含），可为 null
     * @param offset        偏移量
     * @param limit         每页数量
     * @return 按操作时间倒序的日志列表
     */
    List<OperationLog> listByFilter(Long familyId, String operationType, String keyword,
                                    LocalDateTime startTime, LocalDateTime endTime,
                                    int offset, int limit);

    /**
     * 平台级统计操作日志数量，筛选条件与 {@link #listByFilter} 一致
     *
     * @param familyId      家族ID，null 表示全部家族
     * @param operationType 操作类型，空串或 null 表示全部
     * @param keyword       用户名 / 操作描述的右模糊前缀，可为空
     * @param startTime     操作时间下界（含），可为 null
     * @param endTime       操作时间上界（含），可为 null
     * @return 日志数量
     */
    long countByFilter(Long familyId, String operationType, String keyword,
                       LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 列出日志中实际出现过的操作类型，供检索表单提供候选项
     *
     * @return 去重并按字典序排列的操作类型编码
     */
    List<String> listDistinctOperationTypes();
}
