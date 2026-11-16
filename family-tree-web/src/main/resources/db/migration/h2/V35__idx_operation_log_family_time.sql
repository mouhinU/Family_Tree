-- 操作日志家族时间索引（H2）：平台级日志检索与家族内日志翻页的共同入口
-- V35__idx_operation_log_family_time.sql
--
-- 设计：日志查询恒定按 family_id 过滤（平台检索时可不带，回落既有 idx_op_log_time），
--   并按 create_time 倒序分页。单列索引只能命中一个条件，回表后再排序会随日志量线性劣化，
--   因此建立 (family_id, create_time) 组合索引，让过滤与排序在同一次索引扫描内完成。
-- 差异：H2 不支持表级 COMMENT 与 ADD INDEX 子句，改用独立的 CREATE INDEX IF NOT EXISTS。

CREATE INDEX IF NOT EXISTS idx_log_family_time ON operation_log (family_id, create_time);
