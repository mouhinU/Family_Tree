-- sys_user 扩展联系方式（H2）：手机号与邮箱均为选填，非空时全局唯一
-- NULL 值不参与唯一判定，因此存量账号可以留空

ALTER TABLE sys_user ADD COLUMN email VARCHAR(100) DEFAULT NULL;
ALTER TABLE sys_user ADD COLUMN phone VARCHAR(20) DEFAULT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_email ON sys_user(email);
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_phone ON sys_user(phone);
