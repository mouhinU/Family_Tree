package com.mouhin.family.tree.domain.repository;

import com.mouhin.family.tree.domain.entity.CrossFamilyLink;

import java.util.List;

/**
 * 跨族节点链接仓储接口
 *
 * <p>仅供跨族解析服务与链接管理用例读取，族内算法（建树/世代修复/亲属路径）禁止调用。</p>
 *
 * @author Family-Tree
 * @date 2026-11-20
 */
public interface CrossFamilyLinkRepository {

    /**
     * 保存链接（新建）
     *
     * @param link 链接领域对象
     * @return 保存后的链接（含ID）
     */
    CrossFamilyLink save(CrossFamilyLink link);

    /**
     * 更新链接
     *
     * @param link 链接领域对象
     */
    void update(CrossFamilyLink link);

    /**
     * 按ID查询链接
     *
     * @param id 链接ID
     * @return 链接领域对象，不存在返回null
     */
    CrossFamilyLink findById(Long id);

    /**
     * 查询指向指定节点的全部链接（两端任一方向）
     *
     * @param nodeId 族谱节点ID
     * @return 链接列表，无链接返回空列表
     */
    List<CrossFamilyLink> findByNodeId(Long nodeId);

    /**
     * 查询指定两个节点之间的链接（双向规范化后比对）
     *
     * @param nodeA 节点A的ID
     * @param nodeB 节点B的ID
     * @return 链接领域对象，不存在返回null
     */
    CrossFamilyLink findByNodePair(Long nodeA, Long nodeB);

    /**
     * 查询涉及指定家族的全部链接（两端任一方向）
     *
     * @param familyId 家族ID
     * @return 链接列表
     */
    List<CrossFamilyLink> findByFamilyId(Long familyId);

    /**
     * 查询全平台跨族链接（按ID升序）
     *
     * @return 链接列表
     */
    List<CrossFamilyLink> listAll();

    /**
     * 按ID删除链接
     *
     * @param id 链接ID
     */
    void removeById(Long id);

    /**
     * 删除指向指定节点的全部链接（节点删除级联清理）
     *
     * @param nodeId 族谱节点ID
     */
    void removeByNodeId(Long nodeId);

    /**
     * 删除涉及指定家族的全部链接（家族删除级联清理）
     *
     * @param familyId 家族ID
     */
    void removeByFamilyId(Long familyId);
}
