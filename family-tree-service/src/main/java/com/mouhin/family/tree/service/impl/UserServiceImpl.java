package com.mouhin.family.tree.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.family.tree.common.dto.LoginDTO;
import com.mouhin.family.tree.common.dto.RegisterDTO;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.persistence.entity.SysUserDO;
import com.mouhin.family.tree.persistence.mapper.SysUserMapper;
import com.mouhin.family.tree.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 用户服务实现类
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@Service
public class UserServiceImpl implements UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    /** 旧版 MD5 哈希固定为 32 位十六进制；BCrypt 为 60 位 $2x$ 前缀，据此区分新旧格式 */
    private static final int LEGACY_MD5_HASH_LENGTH = 32;

    private final SysUserMapper sysUserMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserServiceImpl(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public Long register(RegisterDTO dto) {
        if (dto.getUsername() == null || dto.getUsername().isBlank()) {
            throw new BusinessException("用户名不能为空");
        }
        if (dto.getPassword() == null || dto.getPassword().length() < 6) {
            throw new BusinessException("密码长度不能少于6位");
        }

        // 检查用户名唯一性
        LambdaQueryWrapper<SysUserDO> query = new LambdaQueryWrapper<>();
        query.eq(SysUserDO::getUsername, dto.getUsername().trim());
        if (sysUserMapper.selectCount(query) > 0) {
            throw new BusinessException("用户名已存在");
        }

        SysUserDO user = new SysUserDO();
        user.setUsername(dto.getUsername().trim());
        user.setPasswordHash(hashPassword(dto.getPassword()));
        user.setNickname(dto.getNickname() != null ? dto.getNickname().trim() : dto.getUsername().trim());
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        sysUserMapper.insert(user);

        logger.info("User registered: id={} username={}", user.getId(), user.getUsername());
        return user.getId();
    }

    @Override
    public Long login(LoginDTO dto) {
        if (dto.getUsername() == null || dto.getPassword() == null) {
            throw new BusinessException("用户名和密码不能为空");
        }

        LambdaQueryWrapper<SysUserDO> query = new LambdaQueryWrapper<>();
        query.eq(SysUserDO::getUsername, dto.getUsername().trim());
        SysUserDO user = sysUserMapper.selectOne(query);

        if (user == null || !verifyPassword(dto.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("用户名或密码错误");
        }

        // 存量 MD5 哈希校验通过后，透明升级为 BCrypt（平滑迁移，不影响用户登录）
        if (isLegacyMd5Hash(user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
            user.setUpdateTime(LocalDateTime.now());
            sysUserMapper.updateById(user);
            logger.info("Upgraded legacy MD5 password hash to BCrypt for user id={}", user.getId());
        }

        logger.info("User logged in: id={} username={}", user.getId(), user.getUsername());
        return user.getId();
    }

    @Override
    public String getNickname(Long userId) {
        SysUserDO user = sysUserMapper.selectById(userId);
        return user != null ? user.getNickname() : null;
    }

    @Override
    public Integer getGeneration(Long userId) {
        SysUserDO user = sysUserMapper.selectById(userId);
        return user != null ? user.getGeneration() : null;
    }

    /**
     * 注册/重置密码时使用 BCrypt 加盐哈希。
     */
    private String hashPassword(String password) {
        return passwordEncoder.encode(password);
    }

    /**
     * 校验密码：存量数据为 32 位 MD5 十六进制，新数据为 BCrypt。
     */
    private boolean verifyPassword(String rawPassword, String storedHash) {
        if (storedHash == null) {
            return false;
        }
        if (isLegacyMd5Hash(storedHash)) {
            return Objects.equals(storedHash, DigestUtils.md5DigestAsHex(rawPassword.getBytes(StandardCharsets.UTF_8)));
        }
        return passwordEncoder.matches(rawPassword, storedHash);
    }

    private boolean isLegacyMd5Hash(String hash) {
        return hash.length() == LEGACY_MD5_HASH_LENGTH;
    }
}
