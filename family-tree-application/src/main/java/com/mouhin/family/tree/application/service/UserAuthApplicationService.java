package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.constant.LoginSecurityConsts;
import com.mouhin.family.tree.common.dto.LoginDTO;
import com.mouhin.family.tree.common.dto.ProfileUpdateDTO;
import com.mouhin.family.tree.common.dto.RegisterDTO;
import com.mouhin.family.tree.common.dto.UserProfileDTO;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.User;
import com.mouhin.family.tree.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 用户认证与个人信息应用服务
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Service
public class UserAuthApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(UserAuthApplicationService.class);

    /**
     * 旧版 MD5 哈希固定为 32 位十六进制；BCrypt 为 60 位 $2x$ 前缀，据此区分新旧格式
     */
    private static final int LEGACY_MD5_HASH_LENGTH = 32;

    private final UserRepository userRepository;
    private final LoginAttemptService loginAttemptService;

    public UserAuthApplicationService(UserRepository userRepository,
                                      LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.loginAttemptService = loginAttemptService;
    }

    /**
     * 用户注册
     *
     * @param dto 注册请求
     * @return 新用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long register(RegisterDTO dto) {
        if (dto.getUsername() == null || dto.getUsername().isBlank()) {
            throw new BusinessException("用户名不能为空");
        }
        if (dto.getPassword() == null || dto.getPassword().length() < 6) {
            throw new BusinessException("密码长度不能少于6位");
        }

        String username = dto.getUsername().trim();

        // 检查用户名唯一性
        User existing = userRepository.findByUsername(username);
        if (existing != null) {
            throw new BusinessException("用户名已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(hashPassword(dto.getPassword()));
        user.setNickname(username);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userRepository.save(user);

        logger.info("User registered: id={} username={}", user.getId(), user.getUsername());
        return user.getId();
    }

    /**
     * 用户登录
     *
     * @param dto 登录请求
     * @return 登录成功的用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long login(LoginDTO dto) {
        if (dto.getUsername() == null || dto.getPassword() == null) {
            throw new BusinessException("用户名和密码不能为空");
        }

        String username = dto.getUsername().trim();

        // 防暴力破解：锁定期间直接拒绝，不落库、不透露账号是否存在
        if (loginAttemptService.isLocked(username)) {
            logger.warn("Login blocked due to lockout: username={}", username);
            throw new BusinessException("登录失败次数过多，请 "
                    + LoginSecurityConsts.LOCK_MINUTES + " 分钟后再试");
        }

        User user = userRepository.findByUsername(username);

        if (user == null || !verifyPassword(dto.getPassword(), user.getPasswordHash())) {
            loginAttemptService.recordFailure(username);
            throw new BusinessException("用户名或密码错误");
        }

        // 校验通过，清除失败计数
        loginAttemptService.recordSuccess(username);

        // 存量 MD5 哈希校验通过后，透明升级为 BCrypt（平滑迁移，不影响用户登录）
        if (isLegacyMd5Hash(user.getPasswordHash())) {
            user.setPasswordHash(BCrypt.hashpw(dto.getPassword(), BCrypt.gensalt()));
            user.setUpdateTime(LocalDateTime.now());
            userRepository.update(user);
            logger.info("Upgraded legacy MD5 password hash to BCrypt for user id={}", user.getId());
        }

        logger.info("User logged in: id={} username={}", user.getId(), user.getUsername());
        return user.getId();
    }

    /**
     * 获取用户昵称
     *
     * @param userId 用户ID
     * @return 昵称，用户不存在返回 null
     */
    public String getNickname(Long userId) {
        User user = userRepository.findById(userId);
        return user != null ? user.getNickname() : null;
    }

    /**
     * 获取用户个人信息（一次查询返回所有字段，避免多次查库）
     *
     * @param userId 用户ID
     * @return 用户信息，用户不存在返回 null
     */
    public UserProfileDTO getUserProfile(Long userId) {
        User user = userRepository.findById(userId);
        if (user == null) {
            return null;
        }
        UserProfileDTO dto = new UserProfileDTO();
        dto.setNickname(user.getNickname());
        dto.setGeneration(user.getGeneration());
        dto.setBirthDate(user.getBirthDate());
        dto.setNodeId(user.getNodeId());
        return dto;
    }

    /**
     * 更新用户关联的族谱节点ID
     *
     * @param userId 用户ID
     * @param nodeId 节点ID（可为 null 表示解绑）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateNodeId(Long userId, Long nodeId) {
        User user = userRepository.findById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!Objects.equals(user.getNodeId(), nodeId)) {
            user.setNodeId(nodeId);
            user.setUpdateTime(LocalDateTime.now());
            userRepository.update(user);
            logger.info("User nodeId updated: userId={}, nodeId={}", userId, nodeId);
        }
    }

    /**
     * 更新用户个人信息
     *
     * @param userId 用户ID
     * @param dto    更新请求
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(Long userId, ProfileUpdateDTO dto) {
        User user = userRepository.findById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        boolean changed = false;
        if (dto.getNickname() != null) {
            String nickname = dto.getNickname().trim();
            if (!nickname.isEmpty() && !nickname.equals(user.getNickname())) {
                user.setNickname(nickname);
                changed = true;
            }
        }
        if (dto.getBirthDate() != null) {
            String birthDate = dto.getBirthDate().trim();
            if (birthDate.isEmpty()) {
                birthDate = null;
            }
            if (!Objects.equals(birthDate, user.getBirthDate())) {
                user.setBirthDate(birthDate);
                changed = true;
            }
        }
        if (dto.getGeneration() != null) {
            if (!Objects.equals(dto.getGeneration(), user.getGeneration())) {
                user.setGeneration(dto.getGeneration());
                changed = true;
            }
        }

        if (changed) {
            user.setUpdateTime(LocalDateTime.now());
            userRepository.update(user);
            logger.info("User profile updated: userId={}", userId);
        }
    }

    /**
     * 注册/重置密码时使用 BCrypt 加盐哈希
     */
    private String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    /**
     * 校验密码：存量数据为 32 位 MD5 十六进制，新数据为 BCrypt
     */
    private boolean verifyPassword(String rawPassword, String storedHash) {
        if (storedHash == null) {
            return false;
        }
        if (isLegacyMd5Hash(storedHash)) {
            return Objects.equals(storedHash, md5Hex(rawPassword));
        }
        return BCrypt.checkpw(rawPassword, storedHash);
    }

    private boolean isLegacyMd5Hash(String hash) {
        return hash.length() == LEGACY_MD5_HASH_LENGTH;
    }

    /**
     * 计算字符串的 MD5 十六进制摘要（兼容存量旧密码校验）
     */
    private String md5Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm not available", e);
        }
    }
}
