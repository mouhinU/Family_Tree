package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.constant.AccountConsts;
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
import java.util.Locale;
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

    /**
     * 登录标识判定符：含 @ 视为邮箱
     */
    private static final String EMAIL_AT_SYMBOL = "@";

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
        if (dto.getPassword() == null || dto.getPassword().length() < AccountConsts.MIN_PASSWORD_LENGTH) {
            throw new BusinessException("密码长度不能少于" + AccountConsts.MIN_PASSWORD_LENGTH + "位");
        }

        String username = dto.getUsername().trim();
        User user = buildUser(username, dto.getPassword(), username);
        user.setEmail(normalizeEmail(dto.getEmail()));
        user.setPhone(normalizePhone(dto.getPhone()));
        return persistNewUser(user).getId();
    }

    /**
     * 为族谱节点自动开通登录账号，并标记为自动账号（区别于真人注册）
     *
     * <p>由节点账号开通编排调用：用户名优先取节点姓名（重名/脱敏时兜底 fp_{节点ID}），
     * 昵称取节点姓名，密码为给定的初始密码。家族-节点绑定由调用方在同一事务内建立。</p>
     *
     * @param username        用户名（节点姓名或 fp_{节点ID} 兜底）
     * @param nickname        昵称（展示名），为空时取用户名
     * @param initialPassword 初始密码明文
     * @return 新用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long provisionAccount(String username, String nickname, String initialPassword) {
        if (username == null || username.isBlank()) {
            throw new BusinessException("用户名不能为空");
        }
        if (initialPassword == null || initialPassword.length() < AccountConsts.MIN_PASSWORD_LENGTH) {
            throw new BusinessException("密码长度不能少于" + AccountConsts.MIN_PASSWORD_LENGTH + "位");
        }

        String trimmedUsername = username.trim();
        String displayNickname = nickname == null || nickname.isBlank()
                ? trimmedUsername : nickname.trim();
        User user = buildUser(trimmedUsername, initialPassword, displayNickname);
        user.setAutoAccount(true);
        return persistNewUser(user).getId();
    }

    /**
     * 用户登录（登录标识支持用户名、手机号、邮箱）
     *
     * @param dto 登录请求
     * @return 登录成功的用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long login(LoginDTO dto) {
        if (dto.getUsername() == null || dto.getPassword() == null) {
            throw new BusinessException("用户名和密码不能为空");
        }

        String identifier = dto.getUsername().trim();

        // 防暴力破解：锁定期间直接拒绝，不落库、不透露账号是否存在
        if (loginAttemptService.isLocked(identifier)) {
            logger.warn("Login blocked due to lockout: identifier={}", identifier);
            throw new BusinessException("登录失败次数过多，请 "
                    + LoginSecurityConsts.LOCK_MINUTES + " 分钟后再试");
        }

        User user = findByIdentifier(identifier);

        if (user == null || !verifyPassword(dto.getPassword(), user.getPasswordHash())) {
            loginAttemptService.recordFailure(identifier);
            throw new BusinessException("用户名或密码错误");
        }

        // 校验通过，清除失败计数
        loginAttemptService.recordSuccess(identifier);

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
     * <p>族谱节点绑定按家族隔离，不在此返回，需由
     * {@link FamilyUserNodeApplicationService#getBoundNodeId} 按当前家族查询。</p>
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
        // 联系方式仅用于本人查看与编辑，不进入成员列表等他人视图
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        return dto;
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

        // 邮箱/手机号留空视为不修改（不支持清空：更新语句按非空字段拼装）
        String email = normalizeEmail(dto.getEmail());
        if (email != null && !email.equals(user.getEmail())) {
            assertContactUnique(user.getUsername(), email, user.getPhone(), userId);
            user.setEmail(email);
            changed = true;
        }
        String phone = normalizePhone(dto.getPhone());
        if (phone != null && !phone.equals(user.getPhone())) {
            assertContactUnique(user.getUsername(), user.getEmail(), phone, userId);
            user.setPhone(phone);
            changed = true;
        }

        if (changed) {
            user.setUpdateTime(LocalDateTime.now());
            userRepository.update(user);
            logger.info("User profile updated: userId={}", userId);
        }
    }

    /**
     * 组装待落库的用户领域对象（密码在此处完成 BCrypt 哈希）
     *
     * @param username    用户名（已去除首尾空格）
     * @param rawPassword 密码明文
     * @param nickname    展示昵称
     * @return 未落库的用户领域对象
     */
    private User buildUser(String username, String rawPassword, String nickname) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(hashPassword(rawPassword));
        user.setNickname(nickname);
        user.setAutoAccount(false);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        return user;
    }

    /**
     * 落库创建用户账号：先校验用户名/邮箱/手机号全局唯一
     *
     * @param user 待保存的用户领域对象
     * @return 已保存的用户领域对象（含ID）
     */
    private User persistNewUser(User user) {
        assertContactUnique(user.getUsername(), user.getEmail(), user.getPhone(), null);
        userRepository.save(user);

        logger.info("User account created: id={} username={} nickname={}",
                user.getId(), user.getUsername(), user.getNickname());
        return user;
    }

    /**
     * 校验用户名、邮箱、手机号的全局唯一性；空白值不参与校验
     *
     * @param username 用户名
     * @param email    邮箱（已归一化，可为 null）
     * @param phone    手机号（已归一化，可为 null）
     * @param selfId   需要排除的用户ID，更新场景传自身ID，注册场景传 null
     */
    private void assertContactUnique(String username, String email, String phone, Long selfId) {
        User byUsername = userRepository.findByUsername(username);
        if (byUsername != null && !Objects.equals(byUsername.getId(), selfId)) {
            throw new BusinessException("用户名已存在");
        }
        if (email != null) {
            User byEmail = userRepository.findByEmail(email);
            if (byEmail != null && !Objects.equals(byEmail.getId(), selfId)) {
                throw new BusinessException("该邮箱已被其他账号使用");
            }
        }
        if (phone != null) {
            User byPhone = userRepository.findByPhone(phone);
            if (byPhone != null && !Objects.equals(byPhone.getId(), selfId)) {
                throw new BusinessException("该手机号已被其他账号使用");
            }
        }
    }

    /**
     * 按登录标识查找用户：含 @ 按邮箱查，11 位纯数字按手机号查，其余按用户名查。
     * <p>
     * 邮箱与纯数字都可能同时是用户的登录名，因此未命中联系方式时回落到用户名查询。
     *
     * @param identifier 登录标识（已去除首尾空格）
     * @return 用户领域对象，不存在返回 null
     */
    private User findByIdentifier(String identifier) {
        if (identifier.contains(EMAIL_AT_SYMBOL)) {
            User byEmail = userRepository.findByEmail(normalizeEmail(identifier));
            return byEmail != null ? byEmail : userRepository.findByUsername(identifier);
        }
        if (isPhoneLike(identifier)) {
            User byPhone = userRepository.findByPhone(identifier);
            return byPhone != null ? byPhone : userRepository.findByUsername(identifier);
        }
        return userRepository.findByUsername(identifier);
    }

    private boolean isPhoneLike(String identifier) {
        if (identifier.length() != AccountConsts.PHONE_DIGIT_LENGTH) {
            return false;
        }
        for (int i = 0; i < identifier.length(); i++) {
            if (!Character.isDigit(identifier.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 邮箱归一化：去空格并转小写，与登录查询保持同一口径
     */
    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 手机号归一化：仅去空格，空串视为未填写
     */
    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return phone.trim();
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
