package com.mouhin.family.tree.domain.repository;

import com.mouhin.family.tree.domain.entity.Family;

import java.util.List;

/**
 * 家族仓储接口
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
public interface FamilyRepository {

    /**
     * 保存家族（新建）
     *
     * @param family 家族领域对象
     * @return 保存后的家族（含ID）
     */
    Family save(Family family);

    /**
     * 根据ID查询家族
     *
     * @param id 家族ID
     * @return 家族领域对象，不存在返回null
     */
    Family findById(Long id);

    /**
     * 根据邀请码查询家族
     *
     * @param inviteCode 邀请码
     * @return 家族领域对象，不存在返回null
     */
    Family findByInviteCode(String inviteCode);

    /**
     * 根据ID列表批量查询家族
     *
     * @param ids 家族ID列表
     * @return 家族领域对象列表
     */
    List<Family> findByIds(List<Long> ids);

    /**
     * 更新家族
     *
     * @param family 家族领域对象
     */
    void update(Family family);
}
