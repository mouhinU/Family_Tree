-- 搜索优化：复合索引加速家族内姓名检索 + 全文索引支持模糊搜索
ALTER TABLE family_node ADD INDEX idx_node_family_name (family_id, name);
ALTER TABLE family_node ADD FULLTEXT INDEX ft_node_search (name, zi, hao, hui) WITH PARSER ngram;
