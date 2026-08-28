package com.mouhin.family.tree.domain.repository;

import com.mouhin.family.tree.domain.entity.NodeHistory;
import com.mouhin.family.tree.domain.entity.RelationHistory;

import java.util.List;

/**
 * 历史记录仓储接口
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
public interface HistoryRepository {

    /**
     * 保存节点历史记录
     *
     * @param history 节点历史领域对象
     * @return 保存后的历史（含ID）
     */
    NodeHistory saveNodeHistory(NodeHistory history);

    /**
     * 保存关系历史记录
     *
     * @param history 关系历史领域对象
     * @return 保存后的历史（含ID）
     */
    RelationHistory saveRelationHistory(RelationHistory history);

    /**
     * 查询节点修改历史（分页）
     *
     * @param nodeId   节点ID
     * @param familyId 家族ID
     * @param offset   偏移量
     * @param limit    限制数量
     * @return 历史记录列表（按时间倒序）
     */
    List<NodeHistory> findNodeHistoryByNodeId(Long nodeId, Long familyId, int offset, int limit);

    /**
     * 统计节点历史记录数
     *
     * @param nodeId   节点ID
     * @param familyId 家族ID
     * @return 记录数
     */
    long countNodeHistoryByNodeId(Long nodeId, Long familyId);

    /**
     * 查询关系修改历史（分页）
     *
     * @param relationId 关系ID
     * @param familyId   家族ID
     * @param offset     偏移量
     * @param limit      限制数量
     * @return 历史记录列表（按时间倒序）
     */
    List<RelationHistory> findRelationHistoryByRelationId(Long relationId, Long familyId, int offset, int limit);

    /**
     * 统计关系历史记录数
     *
     * @param relationId 关系ID
     * @param familyId   家族ID
     * @return 记录数
     */
    long countRelationHistoryByRelationId(Long relationId, Long familyId);

    /**
     * 根据版本号查询节点历史
     *
     * @param nodeId        节点ID
     * @param versionNumber 版本号
     * @return 历史记录，不存在返回null
     */
    NodeHistory findNodeHistoryByVersion(Long nodeId, Integer versionNumber);

    /**
     * 根据版本号查询关系历史
     *
     * @param relationId    关系ID
     * @param versionNumber 版本号
     * @return 历史记录，不存在返回null
     */
    RelationHistory findRelationHistoryByVersion(Long relationId, Integer versionNumber);

    /**
     * 获取节点下一个版本号
     *
     * @param nodeId 节点ID
     * @return 下一个版本号（当前最大版本号+1）
     */
    int getNextNodeVersion(Long nodeId);

    /**
     * 获取关系下一个版本号
     *
     * @param relationId 关系ID
     * @return 下一个版本号（当前最大版本号+1）
     */
    int getNextRelationVersion(Long relationId);
}
