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
     * @param userId 当前用户ID
     * @param dto    创建请求
     * @return 新节点ID
     */
    Long createNode(Long userId, NodeCreateDTO dto);

    /**
     * 更新节点信息
     *
     * @param userId 当前用户ID
     * @param dto    更新内容
     */
    void updateNode(Long userId, FamilyNodeDTO dto);

    /**
     * 删除节点（同时删除关联关系）
     *
     * @param userId 当前用户ID
     * @param nodeId 节点ID
     */
    void deleteNode(Long userId, Long nodeId);

    /**
     * 获取节点详情
     *
     * @param userId 当前用户ID
     * @param nodeId 节点ID
     * @return 节点信息
     */
    FamilyNodeDTO getNode(Long userId, Long nodeId);

    /**
     * 获取用户所有节点列表
     *
     * @param userId 当前用户ID
     * @return 节点列表
     */
    List<FamilyNodeDTO> listNodes(Long userId);

    /**
     * 批量更新节点颜色
     *
     * @param userId     当前用户ID
     * @param nodeIds    节点ID列表
     * @param colorLabel 颜色标签
     */
    void updateColor(Long userId, List<Long> nodeIds, String colorLabel);
}
