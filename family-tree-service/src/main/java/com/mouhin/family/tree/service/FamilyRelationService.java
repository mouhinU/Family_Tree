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
     * @param userId 当前用户ID
     * @param dto    关系信息
     * @return 关系ID
     */
    Long createRelation(Long userId, FamilyRelationDTO dto);

    /**
     * 解除关系
     *
     * @param userId     当前用户ID
     * @param relationId 关系ID
     */
    void deleteRelation(Long userId, Long relationId);

    /**
     * 更新关系（如设置离异日期）
     *
     * @param userId 当前用户ID
     * @param dto    关系更新信息（需含 id）
     */
    void updateRelation(Long userId, FamilyRelationDTO dto);

    /**
     * 获取某节点的所有关系
     *
     * @param userId 当前用户ID
     * @param nodeId 节点ID
     * @return 关系列表
     */
    List<FamilyRelationDTO> listRelationsByNode(Long userId, Long nodeId);

    /**
     * 获取用户所有关系
     *
     * @param userId 当前用户ID
     * @return 关系列表
     */
    List<FamilyRelationDTO> listAllRelations(Long userId);
}
