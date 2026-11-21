package com.mouhin.family.tree.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 跨族节点链接成链来源枚举
 *
 * <p>除账号自动（同一真人账号多族绑定）外，禁止任何未经人工确认的自动成链。</p>
 *
 * @author Family-Tree
 * @date 2026-11-20
 */
@Getter
@AllArgsConstructor
public enum NodeLinkSourceEnum {

    /**
     * 账号自动：同一真人账号在两族分别绑定节点时自动派生，解绑自动释放
     */
    ACCOUNT(1, "账号自动"),

    /**
     * 人工指认：族长在详情弹窗主动搜索他族节点建链
     */
    MANUAL(2, "人工指认"),

    /**
     * 候选确认：按姓名+生日等证据生成候选，管理员逐条确认后转链
     */
    SUGGESTED(3, "候选确认");

    /**
     * 来源编码
     */
    private final int code;

    /**
     * 来源描述
     */
    private final String description;

    public static NodeLinkSourceEnum fromCode(int code) {
        for (NodeLinkSourceEnum value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("无效的跨族链接来源编码: " + code);
    }
}
