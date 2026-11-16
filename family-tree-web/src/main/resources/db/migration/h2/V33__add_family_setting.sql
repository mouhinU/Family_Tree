-- 家族级配置表（H2）：平台管理员按家族维护的开关与阈值
-- V33__add_family_setting.sql
--
-- 设计：只落"被显式覆盖"的配置项，未覆盖的取值由代码内 FamilySettingKeyEnum 的默认值提供。
-- 这样后续新增配置项只需在枚举中登记，无需回填数据、无需改表。
-- 差异：H2 不支持列级 COMMENT 与内联 KEY，索引改为独立 CREATE INDEX 语句。

CREATE TABLE IF NOT EXISTS family_setting (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_id     BIGINT       NOT NULL,
    setting_key   VARCHAR(64)  NOT NULL,
    setting_value VARCHAR(200) NOT NULL,
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_family_setting ON family_setting(family_id, setting_key);
CREATE INDEX IF NOT EXISTS idx_family_setting_family ON family_setting(family_id);
