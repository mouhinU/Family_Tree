package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.constant.LoginSecurityConsts;
import com.mouhin.family.tree.common.dto.LoginDTO;
import com.mouhin.family.tree.common.dto.RegisterDTO;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.User;
import com.mouhin.family.tree.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户认证应用服务密码哈希与平滑迁移逻辑单元测试
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@ExtendWith(MockitoExtension.class)
class UserAuthApplicationServiceTest {

    private static final String RAW_PASSWORD = "test123";

    @Mock
    private UserRepository userRepository;

    private UserAuthApplicationService createService(LoginAttemptService loginAttemptService) {
        return new UserAuthApplicationService(userRepository, loginAttemptService);
    }

    private static String md5(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm not available", e);
        }
    }

    private User userWithHash(String hash) {
        User user = new User();
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
        User user = userWithHash(md5(RAW_PASSWORD));
        when(userRepository.findByUsername("botest")).thenReturn(user);

        UserAuthApplicationService service = createService(new LoginAttemptService());
        Long userId = service.login(loginDto(RAW_PASSWORD));

        assertEquals(1L, userId);
        // 校验通过后应透明升级为 BCrypt
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).update(captor.capture());
        String upgraded = captor.getValue().getPasswordHash();
        assertNotEquals(md5(RAW_PASSWORD), upgraded);
        assertTrue(BCrypt.checkpw(RAW_PASSWORD, upgraded),
                "升级后的哈希应能被 BCrypt 校验通过");
    }

    @Test
    void loginWithBcryptHashSucceedsWithoutMigration() {
        User user = userWithHash(BCrypt.hashpw(RAW_PASSWORD, BCrypt.gensalt()));
        when(userRepository.findByUsername("botest")).thenReturn(user);

        UserAuthApplicationService service = createService(new LoginAttemptService());
        Long userId = service.login(loginDto(RAW_PASSWORD));

        assertEquals(1L, userId);
        // 已是 BCrypt，不应触发迁移更新
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginWithWrongPasswordAgainstMd5HashThrows() {
        User user = userWithHash(md5(RAW_PASSWORD));
        when(userRepository.findByUsername("botest")).thenReturn(user);

        UserAuthApplicationService service = createService(new LoginAttemptService());
        assertThrows(BusinessException.class,
                () -> service.login(loginDto("wrongPwd")));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginWithWrongPasswordAgainstBcryptHashThrows() {
        User user = userWithHash(BCrypt.hashpw(RAW_PASSWORD, BCrypt.gensalt()));
        when(userRepository.findByUsername("botest")).thenReturn(user);

        UserAuthApplicationService service = createService(new LoginAttemptService());
        assertThrows(BusinessException.class,
                () -> service.login(loginDto("wrongPwd")));
    }

    @Test
    void loginWithUnknownUsernameThrows() {
        when(userRepository.findByUsername(anyString())).thenReturn(null);

        UserAuthApplicationService service = createService(new LoginAttemptService());
        assertThrows(BusinessException.class,
                () -> service.login(loginDto(RAW_PASSWORD)));
    }

    @Test
    void registerStoresBcryptHashNotPlaintextOrMd5() {
        when(userRepository.findByUsername("newuser")).thenReturn(null);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(1L);
            return u;
        });

        UserAuthApplicationService service = createService(new LoginAttemptService());
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("newuser");
        dto.setPassword(RAW_PASSWORD);
        service.register(dto);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        String stored = captor.getValue().getPasswordHash();
        assertNotEquals(RAW_PASSWORD, stored);
        assertNotEquals(md5(RAW_PASSWORD), stored);
        assertTrue(BCrypt.checkpw(RAW_PASSWORD, stored), "注册应存储 BCrypt 哈希");
    }

    @Test
    void loginLockedAfterMaxFailedAttempts() {
        // 使用真实计数器实现，验证完整锁定链路
        UserAuthApplicationService service = createService(new LoginAttemptService());
        User user = userWithHash(BCrypt.hashpw(RAW_PASSWORD, BCrypt.gensalt()));
        when(userRepository.findByUsername("botest")).thenReturn(user);

        for (int i = 0; i < LoginSecurityConsts.MAX_FAILED_ATTEMPTS; i++) {
            assertThrows(BusinessException.class,
                    () -> service.login(loginDto("wrongPwd")));
        }

        // 达到上限后即使密码正确也应拒绝登录
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.login(loginDto(RAW_PASSWORD)));
        assertTrue(ex.getMessage().contains("登录失败次数过多"),
                "锁定提示应告知用户等待时长");
    }

    @Test
    void successfulLoginResetsFailureCount() {
        UserAuthApplicationService service = createService(new LoginAttemptService());
        User user = userWithHash(BCrypt.hashpw(RAW_PASSWORD, BCrypt.gensalt()));
        when(userRepository.findByUsername("botest")).thenReturn(user);

        // 累计若干次失败但未达上限
        for (int i = 0; i < LoginSecurityConsts.MAX_FAILED_ATTEMPTS - 1; i++) {
            assertThrows(BusinessException.class,
                    () -> service.login(loginDto("wrongPwd")));
        }

        // 成功登录应清空失败计数
        assertEquals(1L, service.login(loginDto(RAW_PASSWORD)));

        // 重新从 0 计数：再次失败 MAX-1 次仍不应被锁定
        for (int i = 0; i < LoginSecurityConsts.MAX_FAILED_ATTEMPTS - 1; i++) {
            assertThrows(BusinessException.class,
                    () -> service.login(loginDto("wrongPwd")));
        }
        assertEquals(1L, service.login(loginDto(RAW_PASSWORD)));
    }
}
