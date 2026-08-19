-- 搜索优化：为节点姓名添加复合索引，加速按家族筛选+姓名搜索
CREATE INDEX IF NOT EXISTS idx_node_family_name ON family_node(family_id, name);
