-- 辈分表 user_id 改为可空（多用户改造后辈分归属改为 family_id）
-- V14__fix_generation_user_id.sql

ALTER TABLE family_generation MODIFY COLUMN user_id BIGINT NULL;
