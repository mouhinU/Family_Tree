package com.mouhin.family.tree.infrastructure.persistence.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * 巡检用配置行：平台与家族两级配置表的全量最小列，供 Java 侧按白名单过滤未知键。
 *
 * @author Family-Tree
 * @date 2026-11-25
 */
@Getter
@Setter
public class SettingRowDO {

    /**
     * 配置作用域：FAMILY=家族覆盖行，PLATFORM=平台覆盖行
     */
    private String scope;

    /**
     * 配置行主键
     */
    private Long id;

    /**
     * 家族 ID（平台行为 null）
     */
    private Long familyId;

    /**
     * 配置键
     */
    private String settingKey;

    /**
     * 配置值
     */
    private String settingValue;

    @Override
    public String toString() {
        return "SettingRowDO{scope='" + scope + '\'' + ", id=" + id + ", familyId=" + familyId
                + ", settingKey='" + settingKey + '\'' + ", settingValue='" + settingValue + '\'' + '}';
    }
}
