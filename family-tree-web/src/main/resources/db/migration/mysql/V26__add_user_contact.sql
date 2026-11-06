-- sys_user 扩展联系方式（MySQL）：手机号与邮箱均为选填，非空时全局唯一
-- NULL 值不参与唯一判定，因此存量账号可以留空

ALTER TABLE sys_user
    ADD COLUMN email VARCHAR(100) DEFAULT NULL COMMENT '邮箱（选填，非空时全局唯一，可用于登录）',
    ADD COLUMN phone VARCHAR(20) DEFAULT NULL COMMENT '手机号（选填，非空时全局唯一，可用于登录）';

ALTER TABLE sys_user ADD UNIQUE KEY uk_user_email (email);
ALTER TABLE sys_user ADD UNIQUE KEY uk_user_phone (phone);
