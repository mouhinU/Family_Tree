package com.mouhin.family.tree.service;

import com.mouhin.family.tree.common.dto.TreeNodeVO;

import java.util.List;

/**
 * 族谱树形结构服务接口
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
public interface FamilyTreeService {

    /**
     * 获取完整族谱树（从根节点开始）
     *
     * @param familyId 家族ID
     * @return 树形结构列表（可能有多个根节点）
     */
    List<TreeNodeVO> getFullTree(Long familyId);

    /**
     * 获取某节点的子树
     *
     * @param familyId 家族ID
     * @param nodeId   起始节点ID
     * @return 子树结构
     */
    TreeNodeVO getSubTree(Long familyId, Long nodeId);

    /**
     * 失效指定家族的整棵族谱树缓存。
     * 所有会改变树结构的写操作（节点/关系增删改、批量改色等）成功后必须调用。
     *
     * @param familyId 家族ID
     */
    void evictFamilyTree(Long familyId);
}
