-- 角色字典表（MySQL）：应用菜单 RBAC 的角色定义
-- V42__add_role.sql
--
-- 设计：本表只字典化「有哪些角色」，不承载用户与角色的绑定关系。
-- 绑定真源保持现状，建 user_role 会形成双真源，故不建：
--   家族轴 —— family_member.role（V8 建表，取值见 FamilyMemberRoleEnum）
--   平台轴 —— 配置白名单 platformAdminUsernames 按用户名裁决（PlatformAdminApplicationService#isPlatformAdmin）
--   未入族 —— 查询时派生（当前家族无成员关系），进工作台即跳转建族/加入页
-- scope_type 决定服务端解析该角色时是否需要 familyId 上下文：FAMILY 需要，PLATFORM 不需要。
-- 两轴正交：同一用户可同时命中 FAMILY_OWNER 与 PLATFORM_ADMIN，可见菜单取并集。
-- 差异：H2 不支持列级 COMMENT 与内联 KEY，见同名 h2 迁移。

CREATE TABLE IF NOT EXISTS role (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    role_key    VARCHAR(32)     NOT NULL COMMENT '角色标识（对应 RoleKeyEnum）',
    role_name   VARCHAR(50)     NOT NULL COMMENT '角色显示名',
    scope_type  VARCHAR(20)     NOT NULL COMMENT '作用域：PLATFORM 平台级 / FAMILY 家族级 / ACCOUNT 账号派生',
    role_weight INT             NOT NULL DEFAULT 20 COMMENT '层级权重，大者权限更高，用于管理页稳定排序',
    remark      VARCHAR(200)    DEFAULT NULL COMMENT '角色边界说明，避免管理员误授权',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_key (role_key),
    KEY idx_role_scope (scope_type, role_weight)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色字典表';

-- 种子：5 个角色与现状一一对应，不设 is_builtin/is_locked —— 角色增删改无端点，内置行由迁移维护
INSERT INTO role (role_key, role_name, scope_type, role_weight, remark) VALUES
    ('PLATFORM_ADMIN', '平台管理员',  'PLATFORM', 100, '由配置白名单按用户名裁决，与家族角色正交叠加'),
    ('FAMILY_OWNER',   '族长',        'FAMILY',    40, '家族内最高角色，唯一可设置成员角色与转让族长'),
    ('FAMILY_ADMIN',   '家族管理员',  'FAMILY',    30, '可见菜单与族长相同，仅能移除普通成员'),
    ('FAMILY_MEMBER',  '家族成员',    'FAMILY',    20, '按节点可见集合编辑直系范围，无治理与导出类入口'),
    ('FAMILY_GUEST',   '未入族用户',  'ACCOUNT',   10, '当前家族无成员关系，仅可见账号与门户类入口');
