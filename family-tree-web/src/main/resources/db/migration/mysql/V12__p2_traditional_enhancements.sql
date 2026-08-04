-- V12: P2 传统族谱增强 + 多家族 + 忌日提醒 + 隐私保护
-- P2-1: 农历日期字段
-- P2-2: 字/号/讳
-- P2-4: 堂号/籍贯/坟茔
-- P2-5: 外嫁女婚配记录
-- P2-7: 多家族（用户当前家族）
-- P2-6: 隐私模式

-- family_node 扩展字段
ALTER TABLE family_node ADD COLUMN lunar_birth_date VARCHAR(30) DEFAULT NULL COMMENT '农历出生日期';
ALTER TABLE family_node ADD COLUMN lunar_death_date VARCHAR(30) DEFAULT NULL COMMENT '农历去世日期';
ALTER TABLE family_node ADD COLUMN zi VARCHAR(50) DEFAULT NULL COMMENT '字';
ALTER TABLE family_node ADD COLUMN hao VARCHAR(50) DEFAULT NULL COMMENT '号';
ALTER TABLE family_node ADD COLUMN hui VARCHAR(50) DEFAULT NULL COMMENT '讳';
ALTER TABLE family_node ADD COLUMN grave_location VARCHAR(200) DEFAULT NULL COMMENT '坟茔位置';
ALTER TABLE family_node ADD COLUMN spouse_name VARCHAR(50) DEFAULT NULL COMMENT '配偶姓名（外嫁女）';
ALTER TABLE family_node ADD COLUMN spouse_origin_family VARCHAR(100) DEFAULT NULL COMMENT '婚配方家族（外嫁女）';

-- family 扩展字段（P2-4 堂号/籍贯）
ALTER TABLE family ADD COLUMN hall_name VARCHAR(100) DEFAULT NULL COMMENT '堂号';
ALTER TABLE family ADD COLUMN ancestral_home VARCHAR(200) DEFAULT NULL COMMENT '籍贯';

-- sys_user 扩展字段（P2-7 多家族切换）
ALTER TABLE sys_user ADD COLUMN current_family_id BIGINT DEFAULT NULL COMMENT '当前激活家族ID';

-- family_member 扩展字段（P2-6 隐私模式）
ALTER TABLE family_member ADD COLUMN privacy_mode TINYINT DEFAULT 0 COMMENT '隐私模式：0关闭 1开启';

-- family_relation 扩展字段（P1-8 婚姻次序与终止方式）
ALTER TABLE family_relation ADD COLUMN marriage_order INT DEFAULT NULL COMMENT '婚姻次序（第几任配偶）';
ALTER TABLE family_relation ADD COLUMN end_type VARCHAR(20) DEFAULT NULL COMMENT '婚姻终止方式：DIVORCED/WIDOWED/ALIVE';

CREATE INDEX idx_node_lunar_birth ON family_node(lunar_birth_date);
