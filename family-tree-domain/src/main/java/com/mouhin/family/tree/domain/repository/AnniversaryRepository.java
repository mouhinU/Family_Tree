package com.mouhin.family.tree.domain.repository;

import com.mouhin.family.tree.domain.entity.FamilyAnniversary;

import java.util.List;

/**
 * 家族纪念日仓储接口
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
public interface AnniversaryRepository {

    /**
     * 保存纪念日
     *
     * @param anniversary 纪念日领域对象
     * @return 保存后的纪念日（含ID）
     */
    FamilyAnniversary save(FamilyAnniversary anniversary);

    /**
     * 根据ID查询纪念日
     *
     * @param id 纪念日ID
     * @return 纪念日领域对象，不存在返回null
     */
    FamilyAnniversary findById(Long id);

    /**
     * 查询家族纪念日列表（按日期升序）
     *
     * @param familyId 家族ID
     * @return 纪念日列表
     */
    List<FamilyAnniversary> findByFamilyId(Long familyId);

    /**
     * 更新纪念日
     *
     * @param anniversary 纪念日领域对象
     */
    void update(FamilyAnniversary anniversary);

    /**
     * 删除纪念日
     *
     * @param id 纪念日ID
     */
    void removeById(Long id);
}
