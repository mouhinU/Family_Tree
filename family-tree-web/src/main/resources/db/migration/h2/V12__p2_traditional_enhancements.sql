-- V12: P2 传统族谱增强 + 多家族 + 忌日提醒 + 隐私保护
-- P2-1: 农历日期字段
-- P2-2: 字/号/讳
-- P2-4: 堂号/籍贯/坟茔
-- P2-5: 外嫁女婚配记录
-- P2-7: 多家族（用户当前家族）
-- P2-6: 隐私模式

-- family_node 扩展字段
ALTER TABLE family_node ADD COLUMN lunar_birth_date VARCHAR(30) DEFAULT NULL;
ALTER TABLE family_node ADD COLUMN lunar_death_date VARCHAR(30) DEFAULT NULL;
ALTER TABLE family_node ADD COLUMN zi VARCHAR(50) DEFAULT NULL;
ALTER TABLE family_node ADD COLUMN hao VARCHAR(50) DEFAULT NULL;
ALTER TABLE family_node ADD COLUMN hui VARCHAR(50) DEFAULT NULL;
ALTER TABLE family_node ADD COLUMN grave_location VARCHAR(200) DEFAULT NULL;
ALTER TABLE family_node ADD COLUMN spouse_name VARCHAR(50) DEFAULT NULL;
ALTER TABLE family_node ADD COLUMN spouse_origin_family VARCHAR(100) DEFAULT NULL;

-- family 扩展字段（P2-4 堂号/籍贯）
ALTER TABLE family ADD COLUMN hall_name VARCHAR(100) DEFAULT NULL;
ALTER TABLE family ADD COLUMN ancestral_home VARCHAR(200) DEFAULT NULL;

-- sys_user 扩展字段（P2-7 多家族切换）
ALTER TABLE sys_user ADD COLUMN current_family_id BIGINT DEFAULT NULL;

-- family_member 扩展字段（P2-6 隐私模式）
ALTER TABLE family_member ADD COLUMN privacy_mode TINYINT DEFAULT 0;

-- family_relation 扩展字段（P1-8 婚姻次序与终止方式）
ALTER TABLE family_relation ADD COLUMN marriage_order INT DEFAULT NULL;
ALTER TABLE family_relation ADD COLUMN end_type VARCHAR(20) DEFAULT NULL;

CREATE INDEX IF NOT EXISTS idx_node_lunar_birth ON family_node(lunar_birth_date);
