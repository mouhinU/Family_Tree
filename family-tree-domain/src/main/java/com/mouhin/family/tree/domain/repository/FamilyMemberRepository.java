package com.mouhin.family.tree.domain.repository;

import com.mouhin.family.tree.domain.entity.FamilyMember;

import java.util.List;

/**
 * 家族成员仓储接口
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
public interface FamilyMemberRepository {

    /**
     * 保存成员（新建）
     *
     * @param member 成员领域对象
     * @return 保存后的成员（含ID）
     */
    FamilyMember save(FamilyMember member);

    /**
     * 根据家族ID和用户ID查询成员
     *
     * @param familyId 家族ID
     * @param userId   用户ID
     * @return 成员领域对象，不存在返回null
     */
    FamilyMember findByFamilyIdAndUserId(Long familyId, Long userId);

    /**
     * 根据家族ID查询所有成员
     *
     * @param familyId 家族ID
     * @return 成员列表
     */
    List<FamilyMember> findByFamilyId(Long familyId);

    /**
     * 根据用户ID查询所属的所有家族成员记录
     *
     * @param userId 用户ID
     * @return 成员列表
     */
    List<FamilyMember> findByUserId(Long userId);

    /**
     * 根据ID删除成员
     *
     * @param id 成员ID
     */
    void removeById(Long id);

    /**
     * 统计家族成员数量
     *
     * @param familyId 家族ID
     * @return 成员数量
     */
    long countByFamilyId(Long familyId);

    /**
     * 更新成员
     *
     * @param member 成员领域对象
     */
    void update(FamilyMember member);
}
