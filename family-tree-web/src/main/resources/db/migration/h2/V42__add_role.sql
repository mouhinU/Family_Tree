-- 角色字典表（H2）：应用菜单 RBAC 的角色定义
-- V42__add_role.sql
--
-- 与 mysql 同名迁移等义，差异同 V36：H2 不支持列级 COMMENT 与内联 KEY，
-- 索引改为独立 CREATE INDEX 语句，DATETIME 用 TIMESTAMP，无 ON UPDATE（update_time 由应用层写入维护）。
-- 角色绑定真源说明见 mysql 版注释：家族轴取 family_member.role，平台轴取配置白名单，未入族为派生态。

CREATE TABLE IF NOT EXISTS role (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_key    VARCHAR(32)  NOT NULL,
    role_name   VARCHAR(50)  NOT NULL,
    scope_type  VARCHAR(20)  NOT NULL,
    role_weight INT          NOT NULL DEFAULT 20,
    remark      VARCHAR(200) DEFAULT NULL,
    create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_role_key ON role(role_key);
CREATE INDEX IF NOT EXISTS idx_role_scope ON role(scope_type, role_weight);

INSERT INTO role (role_key, role_name, scope_type, role_weight, remark) VALUES
    ('PLATFORM_ADMIN', '平台管理员',  'PLATFORM', 100, '由配置白名单按用户名裁决，与家族角色正交叠加'),
    ('FAMILY_OWNER',   '族长',        'FAMILY',    40, '家族内最高角色，唯一可设置成员角色与转让族长'),
    ('FAMILY_ADMIN',   '家族管理员',  'FAMILY',    30, '可见菜单与族长相同，仅能移除普通成员'),
    ('FAMILY_MEMBER',  '家族成员',    'FAMILY',    20, '按节点可见集合编辑直系范围，无治理与导出类入口'),
    ('FAMILY_GUEST',   '未入族用户',  'ACCOUNT',   10, '当前家族无成员关系，仅可见账号与门户类入口');
