package com.mouhin.family.tree.domain.repository;

import com.mouhin.family.tree.domain.entity.FamilyOffering;

import java.util.List;

/**
 * 祭奠仓储接口
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
public interface FamilyOfferingRepository {

    /**
     * 保存祭奠记录（新建）
     *
     * @param offering 祭奠领域对象
     * @return 保存后的祭奠（含ID）
     */
    FamilyOffering save(FamilyOffering offering);

    /**
     * 根据节点ID查询祭奠记录
     *
     * @param familyId 家族ID
     * @param nodeId   节点ID
     * @return 祭奠列表
     */
    List<FamilyOffering> findByNodeId(Long familyId, Long nodeId);

    /**
     * 统计家族祭奠记录数量
     *
     * @param familyId 家族ID
     * @return 祭奠记录数量
     */
    long countByFamilyId(Long familyId);

    /**
     * 根据用户ID更新所属家族ID（数据迁移场景）
     *
     * @param userId      用户ID
     * @param newFamilyId 新家族ID
     */
    void updateFamilyIdByUserId(Long userId, Long newFamilyId);
}
