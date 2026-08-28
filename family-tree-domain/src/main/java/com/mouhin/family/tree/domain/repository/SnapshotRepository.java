package com.mouhin.family.tree.domain.repository;

import com.mouhin.family.tree.domain.entity.FamilySnapshot;

import java.util.List;

/**
 * 快照仓储接口
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
public interface SnapshotRepository {

    /**
     * 保存家族快照
     *
     * @param snapshot 快照领域对象
     * @return 保存后的快照（含ID）
     */
    FamilySnapshot save(FamilySnapshot snapshot);

    /**
     * 根据ID查询快照
     *
     * @param id 快照ID
     * @return 快照领域对象，不存在返回null
     */
    FamilySnapshot findById(Long id);

    /**
     * 查询家族的所有快照（按时间倒序）
     *
     * @param familyId 家族ID
     * @return 快照列表
     */
    List<FamilySnapshot> findByFamilyId(Long familyId);

    /**
     * 删除快照
     *
     * @param id 快照ID
     */
    void removeById(Long id);

    /**
     * 统计家族快照数量
     *
     * @param familyId 家族ID
     * @return 快照数量
     */
    long countByFamilyId(Long familyId);
}
