package com.mouhin.family.tree.application.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mouhin.family.tree.common.constant.LoginSecurityConsts;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 登录失败计数与锁定服务。
 * 基于 Caffeine 本地缓存（key=用户名，value=连续失败次数），条目在锁定窗口后自动过期清零；
 * Caffeine 内部为并发安全结构，无需额外加锁。容量上限防止恶意用户名撑爆内存。
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Service
public class LoginAttemptService {

    /** 失败计数器容量上限：远超正常用户名规模，仅作内存保护 */
    private static final int MAX_TRACKED_USERNAMES = 10_000;

    private final Cache<String, Integer> attemptsCache = Caffeine.newBuilder()
            .maximumSize(MAX_TRACKED_USERNAMES)
            .expireAfterWrite(Duration.ofMinutes(LoginSecurityConsts.LOCK_MINUTES))
            .build();

    /**
     * 记录一次登录失败
     *
     * @param username 用户名
     */
    public void recordFailure(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        attemptsCache.asMap().merge(username, 1, Integer::sum);
    }

    /**
     * 记录一次登录成功，清除失败计数
     *
     * @param username 用户名
     */
    public void recordSuccess(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        attemptsCache.invalidate(username);
    }

    /**
     * 判断用户是否因连续失败而被锁定
     *
     * @param username 用户名
     * @return true 表示已锁定，禁止登录
     */
    public boolean isLocked(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        Integer attempts = attemptsCache.getIfPresent(username);
        return attempts != null && attempts >= LoginSecurityConsts.MAX_FAILED_ATTEMPTS;
    }
}
