package com.mouhin.family.tree.domain.repository;

import com.mouhin.family.tree.domain.entity.PlatformSetting;

import java.util.List;

/**
 * 平台配置仓储接口
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
public interface PlatformSettingRepository {

    /**
     * 查询全部平台覆盖配置
     *
     * @return 配置列表，无覆盖时返回空列表
     */
    List<PlatformSetting> findAll();

    /**
     * 查询单个平台配置
     *
     * @param key 配置键
     * @return 配置实体，未覆盖时返回 null
     */
    PlatformSetting findByKey(String key);

    /**
     * 保存配置（新建）
     *
     * @param setting 配置领域对象
     * @return 保存后的配置（含ID）
     */
    PlatformSetting save(PlatformSetting setting);

    /**
     * 更新配置
     *
     * @param setting 配置领域对象
     */
    void update(PlatformSetting setting);

    /**
     * 删除配置覆盖行，使该配置键回落到代码默认值
     *
     * @param key 配置键
     */
    void removeByKey(String key);
}
