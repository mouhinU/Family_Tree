package com.mouhin.family.tree.web.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注于家族级 / 整棵树操作端点，声明该入口仅家族管理者（族长或管理员）可访问。
 *
 * <p>由 {@link EndpointAccessAspect} 在方法执行前拦截，读取当前会话的 familyId 与 userId，
 * 调用 {@code FamilyNodeVisibilityApplicationService.assertManager} 裁决；非管理者抛出
 * "仅管理员可执行此操作" 业务异常。用于替代散落在各控制器入口的内联鉴权调用，
 * 使权限要求在端点声明处一目了然。</p>
 *
 * @author Family-Tree
 * @date 2026-11-12
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireFamilyManager {
}
