-- 平台级配置表（MySQL）：平台管理员维护的全站默认值与平台独有开关
-- V36__add_platform_setting.sql
--
-- 设计：只落"被显式覆盖"的配置项，未覆盖的取值由代码内 PlatformSettingKeyEnum 的默认值提供。
-- 与 family_setting 构成三级生效链：家族覆盖 → 平台覆盖 → 代码默认，新增配置项只需在枚举登记。
-- setting_key 不接受枚举白名单之外的值，校验在领域层 PlatformSettingDomainService 完成。

CREATE TABLE IF NOT EXISTS platform_setting (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    setting_key   VARCHAR(64)     NOT NULL COMMENT '配置键（白名单枚举）',
    setting_value VARCHAR(200)    NOT NULL COMMENT '配置值（按配置键类型校验后落库）',
    create_time   DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_setting (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台级配置表';
