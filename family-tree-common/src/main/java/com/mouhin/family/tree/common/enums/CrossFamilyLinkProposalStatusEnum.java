package com.mouhin.family.tree.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 跨族链接提案状态枚举
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
@Getter
@RequiredArgsConstructor
public enum CrossFamilyLinkProposalStatusEnum {

    /**
     * 等待两端节点同意
     */
    PENDING_VOTES("PENDING_VOTES", true),

    /**
     * 已满足门槛，等待平台审核
     */
    READY_FOR_REVIEW("READY_FOR_REVIEW", true),

    /**
     * 平台审核通过，正式链接已建立
     */
    APPROVED("APPROVED", false),

    /**
     * 平台审核驳回
     */
    REJECTED("REJECTED", false),

    /**
     * 发起人撤回
     */
    CANCELLED("CANCELLED", false),

    /**
     * 投票期到期
     */
    EXPIRED("EXPIRED", false),

    /**
     * 端点、绑定或成员关系失效
     */
    INVALIDATED("INVALIDATED", false);

    private final String code;

    private final boolean active;

    /**
     * 按状态编码解析枚举
     *
     * @param code 状态编码
     * @return 状态枚举
     */
    public static CrossFamilyLinkProposalStatusEnum fromCode(String code) {
        for (CrossFamilyLinkProposalStatusEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("无效的跨族链接提案状态: " + code);
    }
}
