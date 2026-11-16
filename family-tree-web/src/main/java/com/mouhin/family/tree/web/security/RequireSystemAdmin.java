package com.mouhin.family.tree.web.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注于平台（系统）管理员端点，声明该入口仅平台白名单内的账号可访问。
 *
 * <p>由 {@link SystemAccessAspect} 在方法执行前拦截，读取当前会话的 userId，
 * 调用 {@code PlatformAdminApplicationService.isPlatformAdmin} 按配置白名单裁决。
 * 该权限轴与家族角色（族长 / 管理员 / 成员）完全独立：家族管理员不因此获得平台权限。</p>
 *
 * @author Family-Tree
 * @date 2026-11-15
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireSystemAdmin {
}
