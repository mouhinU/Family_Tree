package com.mouhin.family.tree.common.constant;

/**
 * 登录安全常量（防暴力破解）
 *
 * @author Family-Tree
 * @date 2026-08-03
 */
public final class LoginSecurityConsts {

    /**
     * 连续登录失败达到该次数后锁定账号
     */
    public static final int MAX_FAILED_ATTEMPTS = 5;
    /**
     * 锁定时长（分钟），期间禁止登录
     */
    public static final int LOCK_MINUTES = 10;

    /**
     * 登录/注册按来源 IP 的短窗口长度（秒）
     */
    public static final int LOGIN_IP_WINDOW_SECONDS = 60;
    /**
     * 短窗口内同一 IP 允许的登录/注册请求数。按「请求次数」而非「失败次数」计：
     * 节点自动账号共用初始口令，一次成功的口令遍历不会留下任何失败计数。
     * 阈值按家庭/运营商大出口 NAT 放宽，正常用户一天登录几次远达不到。
     */
    public static final int LOGIN_IP_MAX_ATTEMPTS = 30;
    /**
     * 登录/注册按来源 IP 的长窗口长度（秒），用于压住刻意放慢的遍历
     */
    public static final int LOGIN_IP_LONG_WINDOW_SECONDS = 600;
    /**
     * 长窗口内同一 IP 允许的登录/注册请求数（折合平均每分钟 12 次）
     */
    public static final int LOGIN_IP_MAX_LONG_ATTEMPTS = 120;

    private LoginSecurityConsts() {
    }
}
