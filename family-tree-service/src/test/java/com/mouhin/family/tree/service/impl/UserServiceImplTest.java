package com.mouhin.family.tree.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.mouhin.family.tree.common.constant.LoginSecurityConsts;
import com.mouhin.family.tree.common.dto.LoginDTO;
import com.mouhin.family.tree.common.dto.RegisterDTO;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.persistence.entity.SysUserDO;
import com.mouhin.family.tree.persistence.mapper.SysUserMapper;
import com.mouhin.family.tree.service.LoginAttemptService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户服务密码哈希与平滑迁移逻辑单元测试
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final String RAW_PASSWORD = "test123";

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private LoginAttemptService loginAttemptService;

    @InjectMocks
    private UserServiceImpl userService;

    private static String md5(String raw) {
        return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
    }

    private SysUserDO userWithHash(String hash) {
        SysUserDO user = new SysUserDO();
        user.setId(1L);
        user.setUsername("botest");
        user.setPasswordHash(hash);
        return user;
    }

    private LoginDTO loginDto(String password) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("botest");
        dto.setPassword(password);
        return dto;
    }

    @Test
    void loginWithLegacyMd5HashSucceedsAndMigratesToBcrypt() {
        SysUserDO user = userWithHash(md5(RAW_PASSWORD));
        when(sysUserMapper.selectOne(any(Wrapper.class))).thenReturn(user);

        Long userId = userService.login(loginDto(RAW_PASSWORD));

        assertEquals(1L, userId);
        // 校验通过后应透明升级为 BCrypt
        ArgumentCaptor<SysUserDO> captor = ArgumentCaptor.forClass(SysUserDO.class);
        verify(sysUserMapper).updateById(captor.capture());
        String upgraded = captor.getValue().getPasswordHash();
        assertNotEquals(md5(RAW_PASSWORD), upgraded);
        assertTrue(ENCODER.matches(RAW_PASSWORD, upgraded), "升级后的哈希应能被 BCrypt 校验通过");
    }

    @Test
    void loginWithBcryptHashSucceedsWithoutMigration() {
        SysUserDO user = userWithHash(ENCODER.encode(RAW_PASSWORD));
        when(sysUserMapper.selectOne(any(Wrapper.class))).thenReturn(user);

        Long userId = userService.login(loginDto(RAW_PASSWORD));

        assertEquals(1L, userId);
        // 已是 BCrypt，不应触发迁移更新
        verify(sysUserMapper, never()).updateById(any(SysUserDO.class));
    }

    @Test
    void loginWithWrongPasswordAgainstMd5HashThrows() {
        SysUserDO user = userWithHash(md5(RAW_PASSWORD));
        when(sysUserMapper.selectOne(any(Wrapper.class))).thenReturn(user);

        assertThrows(BusinessException.class, () -> userService.login(loginDto("wrongPwd")));
        verify(sysUserMapper, never()).updateById(any(SysUserDO.class));
    }

    @Test
    void loginWithWrongPasswordAgainstBcryptHashThrows() {
        SysUserDO user = userWithHash(ENCODER.encode(RAW_PASSWORD));
        when(sysUserMapper.selectOne(any(Wrapper.class))).thenReturn(user);

        assertThrows(BusinessException.class, () -> userService.login(loginDto("wrongPwd")));
    }

    @Test
    void loginWithUnknownUsernameThrows() {
        when(sysUserMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThrows(BusinessException.class, () -> userService.login(loginDto(RAW_PASSWORD)));
    }

    @Test
    void registerStoresBcryptHashNotPlaintextOrMd5() {
        when(sysUserMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("newuser");
        dto.setPassword(RAW_PASSWORD);
        userService.register(dto);

        ArgumentCaptor<SysUserDO> captor = ArgumentCaptor.forClass(SysUserDO.class);
        verify(sysUserMapper).insert(captor.capture());
        String stored = captor.getValue().getPasswordHash();
        assertNotEquals(RAW_PASSWORD, stored);
        assertNotEquals(md5(RAW_PASSWORD), stored);
        assertTrue(ENCODER.matches(RAW_PASSWORD, stored), "注册应存储 BCrypt 哈希");
    }

    @Test
    void loginLockedAfterMaxFailedAttempts() {
        // 使用真实计数器实现，验证完整锁定链路
        UserServiceImpl service = new UserServiceImpl(sysUserMapper, new LoginAttemptServiceImpl());
        SysUserDO user = userWithHash(ENCODER.encode(RAW_PASSWORD));
        when(sysUserMapper.selectOne(any(Wrapper.class))).thenReturn(user);

        for (int i = 0; i < LoginSecurityConsts.MAX_FAILED_ATTEMPTS; i++) {
            assertThrows(BusinessException.class, () -> service.login(loginDto("wrongPwd")));
        }

        // 达到上限后即使密码正确也应拒绝登录
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.login(loginDto(RAW_PASSWORD)));
        assertTrue(ex.getMessage().contains("登录失败次数过多"), "锁定提示应告知用户等待时长");
    }

    @Test
    void successfulLoginResetsFailureCount() {
        UserServiceImpl service = new UserServiceImpl(sysUserMapper, new LoginAttemptServiceImpl());
        SysUserDO user = userWithHash(ENCODER.encode(RAW_PASSWORD));
        when(sysUserMapper.selectOne(any(Wrapper.class))).thenReturn(user);

        // 累计若干次失败但未达上限
        for (int i = 0; i < LoginSecurityConsts.MAX_FAILED_ATTEMPTS - 1; i++) {
            assertThrows(BusinessException.class, () -> service.login(loginDto("wrongPwd")));
        }

        // 成功登录应清空失败计数
        assertEquals(1L, service.login(loginDto(RAW_PASSWORD)));

        // 重新从 0 计数：再次失败 MAX-1 次仍不应被锁定
        for (int i = 0; i < LoginSecurityConsts.MAX_FAILED_ATTEMPTS - 1; i++) {
            assertThrows(BusinessException.class, () -> service.login(loginDto("wrongPwd")));
        }
        assertEquals(1L, service.login(loginDto(RAW_PASSWORD)));
    }
}
