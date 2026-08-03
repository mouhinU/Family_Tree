package com.mouhin.family.tree.common.constant;

/**
 * 登录安全常量（防暴力破解）
 *
 * @author Family-Tree
 * @date 2026-08-03
 */
public final class LoginSecurityConsts {

    private LoginSecurityConsts() {
    }

    /** 连续登录失败达到该次数后锁定账号 */
    public static final int MAX_FAILED_ATTEMPTS = 5;

    /** 锁定时长（分钟），期间禁止登录 */
    public static final int LOCK_MINUTES = 10;
}
