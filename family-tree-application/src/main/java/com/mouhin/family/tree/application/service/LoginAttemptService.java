package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.constant.LoginSecurityConsts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 登录失败计数与锁定服务。
 * <p>
 * 基于 Redis 分布式缓存（key=用户名，value=连续失败次数），条目在锁定窗口后自动过期清零；
 * Redis INCR 操作为原子指令，天然并发安全。
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Service
public class LoginAttemptService {

    private static final Logger logger = LoggerFactory.getLogger(LoginAttemptService.class);

    /**
     * Redis Key 前缀
     */
    private static final String LOGIN_ATTEMPT_KEY_PREFIX = "login:attempt:";

    private final StringRedisTemplate stringRedisTemplate;

    public LoginAttemptService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 记录一次登录失败
     *
     * @param username 用户名
     */
    public void recordFailure(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        String key = LOGIN_ATTEMPT_KEY_PREFIX + username;
        Long attempts = stringRedisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1L) {
            stringRedisTemplate.expire(key, Duration.ofMinutes(LoginSecurityConsts.LOCK_MINUTES));
        }
    }

    /**
     * 记录一次登录成功，清除失败计数
     *
     * @param username 用户名
     */
    public void recordSuccess(String username) {
        clearAttempts(username);
    }

    /**
     * 清除失败计数，解除因连续失败触发的登录锁定
     *
     * @param username 用户名
     */
    public void clearAttempts(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        stringRedisTemplate.delete(LOGIN_ATTEMPT_KEY_PREFIX + username);
    }

    /**
     * 查询当前累计的登录失败次数，用于判断账号是否处于锁定边缘
     *
     * @param username 用户名
     * @return 失败次数，无记录或计数异常时为 0
     */
    public int getFailedAttempts(String username) {
        if (username == null || username.isBlank()) {
            return 0;
        }
        String value = stringRedisTemplate.opsForValue().get(LOGIN_ATTEMPT_KEY_PREFIX + username);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warn("Invalid login attempt count for user={}: {}", username, value);
            return 0;
        }
    }

    /**
     * 判断用户是否因连续失败而被锁定
     *
     * @param username 用户名
     * @return true 表示已锁定，禁止登录
     */
    public boolean isLocked(String username) {
        return getFailedAttempts(username) >= LoginSecurityConsts.MAX_FAILED_ATTEMPTS;
    }
}
