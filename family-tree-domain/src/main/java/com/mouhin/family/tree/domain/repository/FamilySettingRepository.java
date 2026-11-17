package com.mouhin.family.tree.domain.repository;

import com.mouhin.family.tree.domain.entity.FamilySetting;

import java.util.List;

/**
 * 家族配置仓储接口
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
public interface FamilySettingRepository {

    /**
     * 查询指定家族的全部覆盖配置
     *
     * @param familyId 家族ID
     * @return 配置列表，无覆盖时返回空列表
     */
    List<FamilySetting> findByFamilyId(Long familyId);

    /**
     * 查询指定家族的单个配置
     *
     * @param familyId 家族ID
     * @param key      配置键
     * @return 配置实体，未覆盖时返回 null
     */
    FamilySetting findByFamilyIdAndKey(Long familyId, String key);

    /**
     * 保存配置（新建）
     *
     * @param setting 配置领域对象
     * @return 保存后的配置（含ID）
     */
    FamilySetting save(FamilySetting setting);

    /**
     * 更新配置
     *
     * @param setting 配置领域对象
     */
    void update(FamilySetting setting);

    /**
     * 删除配置覆盖行，使该配置键在该家族回落到上层默认值
     *
     * @param familyId 家族ID
     * @param key      配置键
     */
    void removeByKey(Long familyId, String key);
}
