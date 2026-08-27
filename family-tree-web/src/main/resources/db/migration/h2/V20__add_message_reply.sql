-- 留言表增加回复支持
ALTER TABLE family_message ADD COLUMN IF NOT EXISTS parent_id BIGINT;
ALTER TABLE family_message ADD COLUMN IF NOT EXISTS reply_count BIGINT DEFAULT 0;
