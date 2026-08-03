package com.mouhin.family.tree.service;

/**
 * 登录失败计数与锁定服务（防暴力破解）。
 * 同一用户名连续失败达到阈值后锁定若干分钟，成功登录即清零。
 *
 * @author Family-Tree
 * @date 2026-08-03
 */
public interface LoginAttemptService {

    /**
     * 记录一次登录失败，失败计数加一。
     *
     * @param username 用户名
     */
    void recordFailure(String username);

    /**
     * 登录成功后清除该用户的失败计数。
     *
     * @param username 用户名
     */
    void recordSuccess(String username);

    /**
     * 判断该用户当前是否处于锁定状态。
     *
     * @param username 用户名
     * @return 已锁定返回 true
     */
    boolean isLocked(String username);
}
