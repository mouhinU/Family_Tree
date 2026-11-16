-- 操作日志家族时间索引（MySQL）：平台级日志检索与家族内日志翻页的共同入口
-- V35__idx_operation_log_family_time.sql
--
-- 设计：日志查询恒定按 family_id 过滤（平台检索时可不带，回落既有 idx_op_log_time），
--   并按 create_time 倒序分页。单列索引只能命中一个条件，回表后再排序会随日志量线性劣化，
--   因此建立 (family_id, create_time) 组合索引，让过滤与排序在同一次索引扫描内完成。
-- 索引列顺序遵循"等值在前、范围与排序在后"，同时可作为覆盖索引的前缀替代 idx_op_log_time 的家族场景。
-- 幂等：Flyway 单次执行 DDL；ADD INDEX 不加 IF NOT EXISTS 以保持与既有 MySQL 迁移一致的失败即停语义。

ALTER TABLE operation_log ADD INDEX idx_log_family_time (family_id, create_time);
