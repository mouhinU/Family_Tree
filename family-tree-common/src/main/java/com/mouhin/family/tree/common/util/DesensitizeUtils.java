package com.mouhin.family.tree.common.util;

/**
 * 敏感信息脱敏工具
 *
 * <p>平台侧（跨家族）视图不暴露成员完整联系方式，仅家族内按权限展示原文。</p>
 *
 * @author Family-Tree
 * @date 2026-11-15
 */
public final class DesensitizeUtils {

    /**
     * 手机号脱敏后保留的前缀位数
     */
    private static final int PHONE_PREFIX_KEEP = 3;

    /**
     * 手机号脱敏后保留的后缀位数
     */
    private static final int PHONE_SUFFIX_KEEP = 4;

    /**
     * 邮箱脱敏后本地部分保留的首字符位数
     */
    private static final int EMAIL_PREFIX_KEEP = 1;

    /**
     * 掩码字符
     */
    private static final char MASK_CHAR = '*';

    /**
     * 整体掩码（长度不足以保留前后缀时使用）
     */
    private static final String FULL_MASK = "****";

    private DesensitizeUtils() {
    }

    /**
     * 手机号脱敏：保留前 3 位与后 4 位，中间以掩码替代
     *
     * @param phone 手机号原文，可为空
     * @return 脱敏结果；入参为空时返回原值
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        int keep = PHONE_PREFIX_KEEP + PHONE_SUFFIX_KEEP;
        if (phone.length() <= keep) {
            return FULL_MASK;
        }
        return mask(phone, PHONE_PREFIX_KEEP, PHONE_SUFFIX_KEEP);
    }

    /**
     * 邮箱脱敏：本地部分仅保留首字符，域名保持明文
     *
     * @param email 邮箱原文，可为空
     * @return 脱敏结果；入参为空或不含 @ 时返回原值
     */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return FULL_MASK;
        }
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (local.length() <= EMAIL_PREFIX_KEEP) {
            return FULL_MASK + domain;
        }
        StringBuilder builder = new StringBuilder(email.length());
        builder.append(local, 0, EMAIL_PREFIX_KEEP);
        builder.append(FULL_MASK);
        builder.append(domain);
        return builder.toString();
    }

    /**
     * 保留首尾字符、中间以固定长度掩码替代
     */
    private static String mask(String value, int prefixKeep, int suffixKeep) {
        StringBuilder builder = new StringBuilder(prefixKeep + FULL_MASK.length() + suffixKeep);
        builder.append(value, 0, prefixKeep);
        builder.append(FULL_MASK);
        builder.append(value, value.length() - suffixKeep, value.length());
        return builder.toString();
    }
}
