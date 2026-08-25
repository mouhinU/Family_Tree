package com.mouhin.family.tree.domain.repository;

import com.mouhin.family.tree.domain.entity.FamilyGeneration;

import java.util.List;

/**
 * 家族字辈仓储接口
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
public interface FamilyGenerationRepository {

    /**
     * 保存字辈（新建）
     *
     * @param generation 字辈领域对象
     * @return 保存后的字辈（含ID）
     */
    FamilyGeneration save(FamilyGeneration generation);

    /**
     * 根据家族ID查询所有字辈
     *
     * @param familyId 家族ID
     * @return 字辈列表
     */
    List<FamilyGeneration> findByFamilyId(Long familyId);

    /**
     * 根据家族ID和世代查询字辈
     *
     * @param familyId   家族ID
     * @param generation 世代层级
     * @return 字辈领域对象，不存在返回null
     */
    FamilyGeneration findByFamilyIdAndGeneration(Long familyId, Integer generation);

    /**
     * 更新字辈
     *
     * @param generation 字辈领域对象
     */
    void update(FamilyGeneration generation);

    /**
     * 根据ID删除字辈
     *
     * @param id 字辈ID
     */
    void removeById(Long id);

    /**
     * 根据用户ID更新所属家族ID（数据迁移场景）
     *
     * @param userId      用户ID
     * @param newFamilyId 新家族ID
     */
    void updateFamilyIdByUserId(Long userId, Long newFamilyId);
}
