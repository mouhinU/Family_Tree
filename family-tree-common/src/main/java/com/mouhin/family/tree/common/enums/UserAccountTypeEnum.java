package com.mouhin.family.tree.common.enums;

import java.util.Optional;

/**
 * 平台用户列表的账号类型筛选项
 *
 * <p>与 {@code account.is_auto_account} 对应：AUTO 为族谱节点代开的账号，REAL 为真人注册账号。</p>
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
public enum UserAccountTypeEnum {

    /**
     * 不区分账号类型
     */
    ALL("ALL", "全部账号"),

    /**
     * 真人注册账号（含历史存量未标记的数据）
     */
    REAL("REAL", "真人注册"),

    /**
     * 族谱节点自动开通的账号
     */
    AUTO("AUTO", "系统代开");

    private final String code;
    private final String description;

    UserAccountTypeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据 code 忽略大小写查找枚举
     *
     * @param code 类型编码，可为空
     * @return 匹配的枚举；无匹配时返回空
     */
    public static Optional<UserAccountTypeEnum> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String trimmed = code.trim();
        for (UserAccountTypeEnum type : values()) {
            if (type.code.equalsIgnoreCase(trimmed)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
