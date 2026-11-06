package com.mouhin.family.tree.domain.repository;

import com.mouhin.family.tree.domain.entity.FamilyUserNode;

import java.util.List;

/**
 * 用户-家族-族谱节点绑定仓储接口
 *
 * @author Family-Tree
 * @date 2026-11-05
 */
public interface FamilyUserNodeRepository {

    /**
     * 保存绑定（新建）
     *
     * @param binding 绑定领域对象
     * @return 保存后的绑定（含ID）
     */
    FamilyUserNode save(FamilyUserNode binding);

    /**
     * 更新绑定
     *
     * @param binding 绑定领域对象
     */
    void update(FamilyUserNode binding);

    /**
     * 查询某用户在某家族内绑定的节点
     *
     * @param familyId 家族ID
     * @param userId   用户ID
     * @return 绑定领域对象，未绑定返回null
     */
    FamilyUserNode findByFamilyIdAndUserId(Long familyId, Long userId);

    /**
     * 查询某家族内绑定了指定节点的用户
     *
     * @param familyId 家族ID
     * @param nodeId   族谱节点ID
     * @return 绑定领域对象，无绑定返回null
     */
    FamilyUserNode findByFamilyIdAndNodeId(Long familyId, Long nodeId);

    /**
     * 查询某家族的全部绑定关系
     *
     * @param familyId 家族ID
     * @return 绑定列表
     */
    List<FamilyUserNode> findByFamilyId(Long familyId);

    /**
     * 解除某用户在某家族内的绑定
     *
     * @param familyId 家族ID
     * @param userId   用户ID
     */
    void removeByFamilyIdAndUserId(Long familyId, Long userId);

    /**
     * 清空某家族的全部绑定关系（族谱数据整体重建时使用）
     *
     * @param familyId 家族ID
     */
    void removeByFamilyId(Long familyId);
}
