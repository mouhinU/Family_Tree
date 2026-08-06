-- 清理已废弃的香烛(1)和烧纸(2)祭奠记录，统一由敬献(4)替代
-- V15__cleanup_legacy_offerings.sql

DELETE FROM family_offering WHERE offering_type IN (1, 2);
