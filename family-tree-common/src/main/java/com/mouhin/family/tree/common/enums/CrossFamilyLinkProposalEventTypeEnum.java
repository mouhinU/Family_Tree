package com.mouhin.family.tree.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 跨族链接提案事件类型枚举
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
@Getter
@RequiredArgsConstructor
public enum CrossFamilyLinkProposalEventTypeEnum {

    CREATED("CREATED"),
    AGREED("AGREED"),
    WITHDRAWN("WITHDRAWN"),
    CANCELLED("CANCELLED"),
    READY_FOR_REVIEW("READY_FOR_REVIEW"),
    APPROVED("APPROVED"),
    REJECTED("REJECTED"),
    EXPIRED("EXPIRED"),
    INVALIDATED("INVALIDATED"),
    COMMENTED("COMMENTED");

    private final String code;

    /**
     * 按事件类型编码解析枚举
     *
     * @param code 事件编码
     * @return 事件类型枚举
     */
    public static CrossFamilyLinkProposalEventTypeEnum fromCode(String code) {
        for (CrossFamilyLinkProposalEventTypeEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("无效的跨族链接提案事件类型: " + code);
    }
}
