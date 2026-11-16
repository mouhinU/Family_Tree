package com.mouhin.family.tree.common.util;

import com.mouhin.family.tree.common.constant.AccountConsts;

import java.security.SecureRandom;

/**
 * 一次性口令生成工具
 *
 * <p>用于平台重置密码等无法自助取回口令的场景：口令只由操作人一次性获知并转达，
 * 账号随即置为强制改密，首登修改后该口令即失效。</p>
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
public final class OneTimePasswordUtils {

    /**
     * 口令字符池：剔除 0/O、1/l/I 等易混淆字符，便于口头或截图转达
     */
    private static final char[] CANDIDATE_CHARS =
            "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private static final SecureRandom RANDOM = new SecureRandom();

    private OneTimePasswordUtils() {
    }

    /**
     * 生成长度为 {@link AccountConsts#ONE_TIME_PASSWORD_LENGTH} 的随机口令
     *
     * @return 随机一次性口令
     */
    public static String generate() {
        StringBuilder builder = new StringBuilder(AccountConsts.ONE_TIME_PASSWORD_LENGTH);
        for (int i = 0; i < AccountConsts.ONE_TIME_PASSWORD_LENGTH; i++) {
            builder.append(CANDIDATE_CHARS[RANDOM.nextInt(CANDIDATE_CHARS.length)]);
        }
        return builder.toString();
    }
}
