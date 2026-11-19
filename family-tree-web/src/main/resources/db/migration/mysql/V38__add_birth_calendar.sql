-- 出生日期录入历法记录（MySQL）
-- V38__add_birth_calendar.sql
--
-- 背景：出生日期表单支持阳历/阴历两种录入模式，但落库后系统不知道当初是按哪种历法登记的。
-- 新增 birth_calendar 列（1=阳历 2=阴历）记录录入选择，详情弹窗据此决定农历行与阳历行的先后；
-- 存量数据留空，渲染回落到"2000 年前出生优先农历"的启发式规则。

ALTER TABLE family_node ADD COLUMN birth_calendar INT DEFAULT NULL COMMENT '出生日期录入历法：1阳历 2阴历，NULL未记录';
ALTER TABLE sys_user ADD COLUMN birth_calendar INT DEFAULT NULL COMMENT '出生日期录入历法：1阳历 2阴历，NULL未记录';
