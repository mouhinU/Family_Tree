package com.mouhin.family.tree.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 跨族链接提案投票状态枚举
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
@Getter
@RequiredArgsConstructor
public enum CrossFamilyLinkProposalVoteStatusEnum {

    /**
     * 有效同意
     */
    ACTIVE(1),

    /**
     * 已撤回
     */
    REVOKED(2);

    private final int code;

    /**
     * 按投票状态编码解析枚举
     *
     * @param code 状态编码
     * @return 投票状态枚举
     */
    public static CrossFamilyLinkProposalVoteStatusEnum fromCode(int code) {
        for (CrossFamilyLinkProposalVoteStatusEnum value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("无效的跨族链接提案投票状态: " + code);
    }
}
