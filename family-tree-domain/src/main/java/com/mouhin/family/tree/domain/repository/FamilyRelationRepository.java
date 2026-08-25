package com.mouhin.family.tree.domain.repository;

import com.mouhin.family.tree.domain.entity.FamilyRelation;

import java.util.List;

/**
 * 族谱关系仓储接口
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
public interface FamilyRelationRepository {

    /**
     * 保存关系（新建）
     *
     * @param relation 关系领域对象
     * @return 保存后的关系（含ID）
     */
    FamilyRelation save(FamilyRelation relation);

    /**
     * 根据ID查询关系
     *
     * @param id 关系ID
     * @return 关系领域对象，不存在返回null
     */
    FamilyRelation findById(Long id);

    /**
     * 根据家族ID查询所有关系
     *
     * @param familyId 家族ID
     * @return 关系列表
     */
    List<FamilyRelation> findByFamilyId(Long familyId);

    /**
     * 根据节点ID查询涉及该节点的所有关系
     *
     * @param familyId 家族ID
     * @param nodeId   节点ID
     * @return 关系列表
     */
    List<FamilyRelation> findByNodeId(Long familyId, Long nodeId);

    /**
     * 根据ID删除关系
     *
     * @param id 关系ID
     */
    void removeById(Long id);

    /**
     * 删除涉及指定节点的所有关系
     *
     * @param familyId 家族ID
     * @param nodeId   节点ID
     */
    void removeByNodeId(Long familyId, Long nodeId);

    /**
     * 查询指定的精确关系（用于重复校验）
     *
     * @param familyId     家族ID
     * @param fromNodeId   起始节点ID
     * @param toNodeId     目标节点ID
     * @param relationType 关系类型
     * @return 匹配的关系列表
     */
    List<FamilyRelation> findByFromAndToAndType(Long familyId, Long fromNodeId,
                                                 Long toNodeId, Integer relationType);

    /**
     * 根据用户ID更新所属家族ID（数据迁移场景）
     *
     * @param userId      用户ID
     * @param newFamilyId 新家族ID
     */
    void updateFamilyIdByUserId(Long userId, Long newFamilyId);

    /**
     * 查询指定节点的夫妻关系（作为 fromNodeId 或 toNodeId）
     *
     * @param familyId 家族ID
     * @param nodeId   节点ID
     * @return 夫妻关系列表
     */
    List<FamilyRelation> findSpouseRelations(Long familyId, Long nodeId);

    /**
     * 统计指定节点的子女数量（作为 fromNodeId 的 PARENT_CHILD 关系数）
     *
     * @param familyId 家族ID
     * @param nodeId   节点ID
     * @return 子女数量
     */
    long countChildren(Long familyId, Long nodeId);

    /**
     * 判断指定的精确关系是否存在
     *
     * @param familyId     家族ID
     * @param fromNodeId   起始节点ID
     * @param toNodeId     目标节点ID
     * @param relationType 关系类型
     * @return 是否存在
     */
    boolean existsRelation(Long familyId, Long fromNodeId, Long toNodeId, Integer relationType);
}
