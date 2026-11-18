-- 个人信息支持性别，并与绑定节点档案同步（H2）
-- V37__add_user_gender.sql
--
-- 背景：族谱节点 family_node 早已具备 gender（0未知/1男/2女），但用户表 sys_user 没有性别列，
-- 个人信息页也无从展示。新增该列后，绑定/开通账号时与出生日期、世代一并从节点回填，
-- 用户也可在个人信息中自行修改。存量用户留空，表示尚未填写。
-- 差异：H2 的 ADD COLUMN 支持 IF NOT EXISTS。

ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS gender INT DEFAULT NULL;
