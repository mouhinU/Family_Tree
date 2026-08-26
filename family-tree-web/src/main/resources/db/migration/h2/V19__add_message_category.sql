-- 留言表增加分类字段
ALTER TABLE family_message ADD COLUMN category VARCHAR(20) DEFAULT 'GENERAL';
