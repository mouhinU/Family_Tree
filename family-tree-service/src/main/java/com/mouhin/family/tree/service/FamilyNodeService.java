package com.mouhin.family.tree.service;

import com.mouhin.family.tree.common.dto.FamilyNodeDTO;
import com.mouhin.family.tree.common.dto.NodeCreateDTO;

import java.util.List;

/**
 * 族谱节点服务接口
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
public interface FamilyNodeService {

    /**
     * 创建节点（可同时建立与父节点或配偶的关系）
     *
     * @param familyId 家族ID
     * @param userId   操作者用户ID（记录创建者）
     * @param dto      创建请求
     * @return 新节点ID
     */
    Long createNode(Long familyId, Long userId, NodeCreateDTO dto);

    /**
     * 更新节点信息
     *
     * @param familyId 家族ID
     * @param dto      更新内容
     */
    void updateNode(Long familyId, FamilyNodeDTO dto);

    /**
     * 删除节点（同时删除关联关系）
     *
     * @param familyId 家族ID
     * @param nodeId   节点ID
     */
    void deleteNode(Long familyId, Long nodeId);

    /**
     * 获取节点详情
     *
     * @param familyId 家族ID
     * @param nodeId   节点ID
     * @return 节点信息
     */
    FamilyNodeDTO getNode(Long familyId, Long nodeId);

    /**
     * 获取家族所有节点列表
     *
     * @param familyId 家族ID
     * @return 节点列表
     */
    List<FamilyNodeDTO> listNodes(Long familyId);

    /**
     * 按姓名关键字搜索节点
     *
     * @param familyId 家族ID
     * @param keyword  搜索关键字
     * @return 匹配的节点列表（最多20条）
     */
    List<FamilyNodeDTO> searchNodes(Long familyId, String keyword);

    /**
     * 批量更新节点颜色
     *
     * @param familyId   家族ID
     * @param nodeIds    节点ID列表
     * @param colorLabel 颜色标签
     */
    void updateColor(Long familyId, List<Long> nodeIds, String colorLabel);

    /**
     * 递归同步指定节点所有后代的世代层级。
     * 子节点 = 父节点 + 1，配偶与节点同代。
     *
     * @param familyId   家族ID
     * @param nodeId     起始节点ID
     * @param generation 起始节点的目标世代
     */
    void syncDescendantGenerations(Long familyId, Long nodeId, Integer generation);
}
