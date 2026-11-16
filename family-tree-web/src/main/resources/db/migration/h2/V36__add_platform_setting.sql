-- 平台级配置表（H2）：平台管理员维护的全站默认值与平台独有开关
-- V36__add_platform_setting.sql
--
-- 设计：只落"被显式覆盖"的配置项，未覆盖的取值由代码内 PlatformSettingKeyEnum 的默认值提供。
-- 与 family_setting 构成三级生效链：家族覆盖 → 平台覆盖 → 代码默认，新增配置项无需回填数据、无需改表。
-- 差异：H2 不支持列级 COMMENT 与内联 KEY，索引改为独立 CREATE INDEX 语句。

CREATE TABLE IF NOT EXISTS platform_setting (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    setting_key   VARCHAR(64)  NOT NULL,
    setting_value VARCHAR(200) NOT NULL,
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_platform_setting ON platform_setting(setting_key);
