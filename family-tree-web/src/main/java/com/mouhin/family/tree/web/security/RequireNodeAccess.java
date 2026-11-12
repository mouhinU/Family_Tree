package com.mouhin.family.tree.web.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注于按节点裁决的端点，声明该入口对目标节点所需的查看（{@link NodeAccessActionEnum#VIEW}）
 * 或操作（{@link NodeAccessActionEnum#OPERATE}）权限。
 *
 * <p>由 {@link EndpointAccessAspect} 在方法执行前拦截：以 {@link #node()} 的 SpEL 表达式从方法入参
 * 解析出目标节点ID（标量 {@code Long}，或 {@code Collection<Long>} / {@code Long[]} 表示多节点），
 * 结合当前会话的 familyId 与 userId 调用 {@code FamilyNodeVisibilityApplicationService} 对应断言：
 * VIEW 走 {@code assertDetailVisible}，OPERATE 单节点走 {@code assertOperableNode}、
 * 多节点走 {@code assertOperableNodes}。管理者不受限，非管理员越权抛出统一业务异常。</p>
 *
 * <p>SpEL 以方法参数名引用（编译需开启 {@code -parameters}），如 {@code "#nodeId"}、
 * {@code "#dto.nodeId"}、{@code "#dto.nodeIds"}。</p>
 *
 * @author Family-Tree
 * @date 2026-11-12
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireNodeAccess {

    /**
     * 解析目标节点ID的 SpEL 表达式：结果可为单个 {@code Long}，或 {@code Collection<Long>} /
     * {@code Long[]}（后者要求全部节点均可操作）。
     *
     * @return SpEL 表达式
     */
    String node();

    /**
     * 访问动作类型，默认操作（写）。
     *
     * @return 查看或操作
     */
    NodeAccessActionEnum action() default NodeAccessActionEnum.OPERATE;
}
