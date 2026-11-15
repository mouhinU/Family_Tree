-- 个人信息出生日期支持阴历/阳历双记录（MySQL）
-- V32__add_user_lunar_birth_date.sql
--
-- 背景：节点表 family_node 早已同时具备 birth_date（阳历）与 lunar_birth_date（农历），
-- 但用户表 sys_user 仅有 birth_date（阳历字符串）。为让"个人信息"也记录两个日期，
-- 新增农历出生日期列。存量用户农历留空，待其下次编辑个人信息时由后端按阳历自动补齐。

ALTER TABLE sys_user ADD COLUMN lunar_birth_date VARCHAR(30) DEFAULT NULL COMMENT '农历出生日期（由阳历推导）';
