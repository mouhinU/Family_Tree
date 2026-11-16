package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 平台总览视图对象（系统管理员首页统计条）
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Getter
@Setter
public class PlatformOverviewVO {

    /**
     * 家族总数
     */
    private Long familyCount;

    /**
     * 注册用户总数（含族谱节点自动开通的账号）
     */
    private Long userCount;

    /**
     * 族谱节点自动开通的账号数
     */
    private Long autoAccountCount;

    /**
     * 全平台族谱节点总数
     */
    private Long nodeCount;

    /**
     * 全平台亲缘关系总数
     */
    private Long relationCount;

    /**
     * 最近 {@code recentWindowDays} 天内新建的家族数
     */
    private Long recentFamilyCount;

    /**
     * {@code recentFamilyCount} 的统计窗口天数
     */
    private Integer recentWindowDays;
}
