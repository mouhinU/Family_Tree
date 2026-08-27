-- 留言表增加回复支持
ALTER TABLE family_message ADD COLUMN parent_id BIGINT DEFAULT NULL;
ALTER TABLE family_message ADD COLUMN reply_count BIGINT DEFAULT 0;
