package com.mouhin.family.tree.domain.repository;

import com.mouhin.family.tree.domain.entity.FamilyNode;

import java.util.List;

/**
 * 族谱节点仓储接口
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
public interface FamilyNodeRepository {

    /**
     * 保存节点（新建）
     *
     * @param node 节点领域对象
     * @return 保存后的节点（含ID）
     */
    FamilyNode save(FamilyNode node);

    /**
     * 根据ID查询节点
     *
     * @param id 节点ID
     * @return 节点领域对象，不存在返回null
     */
    FamilyNode findById(Long id);

    /**
     * 根据家族ID查询所有节点
     *
     * @param familyId 家族ID
     * @return 节点列表
     */
    List<FamilyNode> findByFamilyId(Long familyId);

    /**
     * 根据家族ID和名称关键字模糊查询节点
     *
     * @param familyId 家族ID
     * @param keyword  名称关键字
     * @return 节点列表
     */
    List<FamilyNode> findByFamilyIdAndNameContaining(Long familyId, String keyword);

    /**
     * 更新节点
     *
     * @param node 节点领域对象
     */
    void update(FamilyNode node);

    /**
     * 根据ID删除节点
     *
     * @param id 节点ID
     */
    void removeById(Long id);

    /**
     * 根据家族ID删除所有节点
     *
     * @param familyId 家族ID
     */
    void removeByFamilyId(Long familyId);

    /**
     * 根据ID列表批量查询节点
     *
     * @param ids 节点ID列表
     * @return 节点列表
     */
    List<FamilyNode> findByIds(List<Long> ids);

    /**
     * 批量更新节点世代
     *
     * @param nodes 需要更新世代的节点列表
     */
    void batchUpdateGeneration(List<FamilyNode> nodes);

    /**
     * 查询家族中已去世的节点
     *
     * @param familyId 家族ID
     * @return 已去世节点列表
     */
    List<FamilyNode> findDeceasedByFamilyId(Long familyId);

    /**
     * 统计家族节点数量
     *
     * @param familyId 家族ID
     * @return 节点数量
     */
    long countByFamilyId(Long familyId);

    /**
     * 根据用户ID更新所属家族ID（数据迁移场景）
     *
     * @param userId      用户ID
     * @param newFamilyId 新家族ID
     */
    void updateFamilyIdByUserId(Long userId, Long newFamilyId);

    /**
     * 批量更新节点颜色标签
     *
     * @param familyId   家族ID
     * @param nodeIds    节点ID列表
     * @param colorLabel 颜色标签
     */
    void updateColorLabel(Long familyId, List<Long> nodeIds, String colorLabel);
}
