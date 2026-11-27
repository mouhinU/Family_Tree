package com.mouhin.family.tree.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 跨族节点链接数据质量问题枚举
 *
 * @author Family-Tree
 * @date 2026-11-26
 */
@Getter
@AllArgsConstructor
public enum CrossFamilyLinkIssueEnum {

    /**
     * 链接端点节点已删除
     */
    ORPHAN_NODE("ORPHAN_NODE", "端点节点已删除"),

    /**
     * 链接端点家族已删除
     */
    FAMILY_MISSING("FAMILY_MISSING", "端点家族已删除"),

    /**
     * 节点实际所属家族与链接记录不一致
     */
    FAMILY_MISMATCH("FAMILY_MISMATCH", "节点实际家族与链记录不一致"),

    /**
     * 链接两端实际属于同一家族
     */
    SAME_FAMILY_ENDPOINT("SAME_FAMILY_ENDPOINT", "两端实际属于同一家族"),

    /**
     * 链接类型编码非法
     */
    INVALID_LINK_TYPE("INVALID_LINK_TYPE", "链接类型非法"),

    /**
     * 成链来源编码非法
     */
    INVALID_SOURCE("INVALID_SOURCE", "成链来源非法"),

    /**
     * 端点未按节点ID升序规范化存放
     */
    UNORDERED_ENDPOINT("UNORDERED_ENDPOINT", "端点未按节点ID升序存放"),

    /**
     * 非账号自动来源缺少确认人
     */
    MISSING_CONFIRMER("MISSING_CONFIRMER", "非账号自动来源缺少确认人");

    /**
     * 问题编码
     */
    private final String code;

    /**
     * 问题描述
     */
    private final String description;
}
