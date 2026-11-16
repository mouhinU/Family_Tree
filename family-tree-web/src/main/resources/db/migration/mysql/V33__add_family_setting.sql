-- 家族级配置表（MySQL）：平台管理员按家族维护的开关与阈值
-- V33__add_family_setting.sql
--
-- 设计：只落"被显式覆盖"的配置项，未覆盖的取值由代码内 FamilySettingKeyEnum 的默认值提供。
-- 这样后续新增配置项只需在枚举中登记，无需回填数据、无需改表。
-- setting_key 不接受枚举白名单之外的值，校验在领域层 FamilySettingDomainService 完成。

CREATE TABLE IF NOT EXISTS family_setting (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    family_id     BIGINT UNSIGNED NOT NULL COMMENT '家族ID',
    setting_key   VARCHAR(64)     NOT NULL COMMENT '配置键（白名单枚举）',
    setting_value VARCHAR(200)    NOT NULL COMMENT '配置值（按配置键类型校验后落库）',
    create_time   DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_family_setting (family_id, setting_key),
    KEY idx_family_setting_family (family_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='家族级配置表';
