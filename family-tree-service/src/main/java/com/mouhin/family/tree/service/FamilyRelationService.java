package com.mouhin.family.tree.service;

import com.mouhin.family.tree.common.dto.FamilyRelationDTO;

import java.util.List;

/**
 * 族谱关系服务接口
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
public interface FamilyRelationService {

    /**
     * 建立关系
     *
     * @param familyId 家族ID
     * @param userId   操作者用户ID（记录创建者）
     * @param dto      关系信息
     * @return 关系ID
     */
    Long createRelation(Long familyId, Long userId, FamilyRelationDTO dto);

    /**
     * 解除关系
     *
     * @param familyId   家族ID
     * @param relationId 关系ID
     */
    void deleteRelation(Long familyId, Long relationId);

    /**
     * 更新关系（如设置离异日期）
     *
     * @param familyId 家族ID
     * @param dto      关系更新信息（需含 id）
     */
    void updateRelation(Long familyId, FamilyRelationDTO dto);

    /**
     * 获取某节点的所有关系
     *
     * @param familyId 家族ID
     * @param nodeId   节点ID
     * @return 关系列表
     */
    List<FamilyRelationDTO> listRelationsByNode(Long familyId, Long nodeId);

    /**
     * 获取家族所有关系
     *
     * @param familyId 家族ID
     * @return 关系列表
     */
    List<FamilyRelationDTO> listAllRelations(Long familyId);

    /**
     * 夫妻关系合法性校验（禁止自身、重复、直系血亲、同胞）。
     * 供节点服务在创建配偶关系前调用。
     *
     * @param familyId   家族ID
     * @param fromNodeId 关系起点节点
     * @param toNodeId   关系终点节点
     */
    void validateSpouseRelation(Long familyId, Long fromNodeId, Long toNodeId);
}
