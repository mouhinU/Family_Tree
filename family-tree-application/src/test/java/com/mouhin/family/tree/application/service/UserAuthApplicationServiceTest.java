package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.constant.AccountConsts;
import com.mouhin.family.tree.common.constant.LoginSecurityConsts;
import com.mouhin.family.tree.common.dto.LoginDTO;
import com.mouhin.family.tree.common.dto.ProfileUpdateDTO;
import com.mouhin.family.tree.common.dto.RegisterDTO;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.User;
import com.mouhin.family.tree.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    /**
     * 假 Redis 存储：让 mock 具备 INCR/GET/DEL 语义，否则失败计数永远为 0、锁定链路测不到
     */
    private final Map<String, String> attemptStore = new HashMap<>();

    /**
     * 创建使用模拟 Redis 的 LoginAttemptService
     */
    private LoginAttemptService createLoginAttemptService() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.increment(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            long attempts = Long.parseLong(attemptStore.getOrDefault(key, "0")) + 1;
            attemptStore.put(key, String.valueOf(attempts));
            return attempts;
        });
        lenient().when(valueOperations.get(anyString()))
                .thenAnswer(invocation -> attemptStore.get(invocation.<String>getArgument(0)));
        lenient().when(stringRedisTemplate.delete(anyString()))
                .thenAnswer(invocation -> attemptStore.remove(invocation.<String>getArgument(0)) != null);
        return new LoginAttemptService(stringRedisTemplate);
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

    private UserAuthApplicationService createService(LoginAttemptService loginAttemptService) {
        return new UserAuthApplicationService(userRepository, loginAttemptService);
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

    private LoginDTO loginDto(String identifier, String password) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(identifier);
        dto.setPassword(password);
        return dto;
    }

    @Test
    void loginWithLegacyMd5HashSucceedsAndMigratesToBcrypt() {
        User user = userWithHash(md5(RAW_PASSWORD));
        when(userRepository.findByUsername("botest")).thenReturn(user);

        UserAuthApplicationService service = createService(createLoginAttemptService());
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

        UserAuthApplicationService service = createService(createLoginAttemptService());
        Long userId = service.login(loginDto(RAW_PASSWORD));

        assertEquals(1L, userId);
        // 已是 BCrypt，不应触发迁移更新
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginWithWrongPasswordAgainstMd5HashThrows() {
        User user = userWithHash(md5(RAW_PASSWORD));
        when(userRepository.findByUsername("botest")).thenReturn(user);

        UserAuthApplicationService service = createService(createLoginAttemptService());
        assertThrows(BusinessException.class,
                () -> service.login(loginDto("wrongPwd")));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginWithWrongPasswordAgainstBcryptHashThrows() {
        User user = userWithHash(BCrypt.hashpw(RAW_PASSWORD, BCrypt.gensalt()));
        when(userRepository.findByUsername("botest")).thenReturn(user);

        UserAuthApplicationService service = createService(createLoginAttemptService());
        assertThrows(BusinessException.class,
                () -> service.login(loginDto("wrongPwd")));
    }

    @Test
    void loginWithUnknownUsernameThrows() {
        when(userRepository.findByUsername(anyString())).thenReturn(null);

        UserAuthApplicationService service = createService(createLoginAttemptService());
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

        UserAuthApplicationService service = createService(createLoginAttemptService());
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
        assertFalse(captor.getValue().getAutoAccount(), "真人注册账号不应标记为自动账号");
    }

    @Test
    void loginLockedAfterMaxFailedAttempts() {
        // 使用真实计数器实现，验证完整锁定链路
        UserAuthApplicationService service = createService(createLoginAttemptService());
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
        UserAuthApplicationService service = createService(createLoginAttemptService());
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

    @Test
    void provisionAccountUsesNodeIdNicknameAndBcryptInitialPassword() {
        when(userRepository.findByUsername("fp_40")).thenReturn(null);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(9L);
            return saved;
        });

        UserAuthApplicationService service = createService(createLoginAttemptService());
        Long userId = service.provisionAccount(
                "fp_40", "张大爷", AccountConsts.NODE_ACCOUNT_INITIAL_PASSWORD);

        assertEquals(9L, userId);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertEquals("fp_40", saved.getUsername());
        assertEquals("张大爷", saved.getNickname());
        assertTrue(saved.getAutoAccount(), "自动开通的账号应标记为自动账号");
        assertTrue(BCrypt.checkpw(AccountConsts.NODE_ACCOUNT_INITIAL_PASSWORD, saved.getPasswordHash()),
                "初始密码应以 BCrypt 哈希存储");
    }

    @Test
    void provisionAccountBlankNicknameFallsBackToUsername() {
        when(userRepository.findByUsername("fp_41")).thenReturn(null);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserAuthApplicationService service = createService(createLoginAttemptService());
        service.provisionAccount("fp_41", "   ", AccountConsts.NODE_ACCOUNT_INITIAL_PASSWORD);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("fp_41", captor.getValue().getNickname());
    }

    @Test
    void provisionAccountDuplicateUsernameThrowsAndDoesNotSave() {
        when(userRepository.findByUsername("fp_40")).thenReturn(userWithHash("x"));

        UserAuthApplicationService service = createService(createLoginAttemptService());
        assertThrows(BusinessException.class, () -> service.provisionAccount(
                "fp_40", "张大爷", AccountConsts.NODE_ACCOUNT_INITIAL_PASSWORD));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void provisionAccountShortInitialPasswordThrows() {
        UserAuthApplicationService service = createService(createLoginAttemptService());
        assertThrows(BusinessException.class,
                () -> service.provisionAccount("fp_42", "张大爷", "123"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginWithEmailIdentifierResolvesUserByEmailIgnoringCase() {
        User user = userWithHash(BCrypt.hashpw(RAW_PASSWORD, BCrypt.gensalt()));
        // 库中邮箱已归一化为小写，登录串大小写混合也应命中
        when(userRepository.findByEmail("zhang@example.com")).thenReturn(user);

        UserAuthApplicationService service = createService(createLoginAttemptService());
        Long userId = service.login(loginDto("Zhang@Example.COM", RAW_PASSWORD));

        assertEquals(1L, userId);
        verify(userRepository, never()).findByUsername("Zhang@Example.COM");
    }

    @Test
    void loginByPhoneIdentifierResolvesUserByPhone() {
        User user = userWithHash(BCrypt.hashpw(RAW_PASSWORD, BCrypt.gensalt()));
        when(userRepository.findByPhone("13800138000")).thenReturn(user);

        UserAuthApplicationService service = createService(createLoginAttemptService());
        assertEquals(1L, service.login(loginDto("13800138000", RAW_PASSWORD)));
    }

    @Test
    void loginFallsBackToUsernameWhenContactNotFound() {
        User user = userWithHash(BCrypt.hashpw(RAW_PASSWORD, BCrypt.gensalt()));
        // 极端场景：用户名本身就是邮箱形态，邮箱未命中时应回落用户名查询
        when(userRepository.findByEmail("zhang@example.com")).thenReturn(null);
        when(userRepository.findByUsername("zhang@example.com")).thenReturn(user);

        UserAuthApplicationService service = createService(createLoginAttemptService());
        assertEquals(1L, service.login(loginDto("zhang@example.com", RAW_PASSWORD)));
    }

    @Test
    void registerNormalizesEmailAndPhoneBeforePersisting() {
        RegisterDTO dto = registerDto("zhangsan");
        dto.setEmail(" Zhang@Example.COM ");
        dto.setPhone("13800138000");
        when(userRepository.findByUsername("zhangsan")).thenReturn(null);
        when(userRepository.findByEmail("zhang@example.com")).thenReturn(null);
        when(userRepository.findByPhone("13800138000")).thenReturn(null);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(5L);
            return saved;
        });

        UserAuthApplicationService service = createService(createLoginAttemptService());
        assertEquals(5L, service.register(dto));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("zhang@example.com", captor.getValue().getEmail());
        assertEquals("13800138000", captor.getValue().getPhone());
    }

    @Test
    void registerWithEmailTakenByAnotherAccountThrows() {
        RegisterDTO dto = registerDto("zhangsan");
        dto.setEmail("zhang@example.com");
        User other = userWithHash("x");
        other.setId(7L);
        when(userRepository.findByUsername("zhangsan")).thenReturn(null);
        when(userRepository.findByEmail("zhang@example.com")).thenReturn(other);

        UserAuthApplicationService service = createService(createLoginAttemptService());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.register(dto));
        assertTrue(ex.getMessage().contains("该邮箱已被其他账号使用"),
                "应明确告知邮箱已被占用");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateProfileSetsEmailAndPhoneKeepingSelfUsernameMatch() {
        User user = userWithHash("x");
        when(userRepository.findById(1L)).thenReturn(user);
        // 用户名查回的是自己，不应判定为重复
        when(userRepository.findByUsername("botest")).thenReturn(user);
        when(userRepository.findByEmail("new@example.com")).thenReturn(null);
        when(userRepository.findByPhone("13900139000")).thenReturn(null);

        ProfileUpdateDTO dto = new ProfileUpdateDTO();
        dto.setEmail("NEW@example.com");
        dto.setPhone("13900139000");

        UserAuthApplicationService service = createService(createLoginAttemptService());
        service.updateProfile(1L, dto);

        assertEquals("new@example.com", user.getEmail());
        assertEquals("13900139000", user.getPhone());
        verify(userRepository).update(user);
    }

    @Test
    void updateProfileWithPhoneTakenByAnotherAccountThrowsAndKeepsUserUnchanged() {
        User user = userWithHash("x");
        User other = userWithHash("y");
        other.setId(8L);
        when(userRepository.findById(1L)).thenReturn(user);
        when(userRepository.findByUsername("botest")).thenReturn(user);
        when(userRepository.findByPhone("13900139000")).thenReturn(other);

        ProfileUpdateDTO dto = new ProfileUpdateDTO();
        dto.setPhone("13900139000");

        UserAuthApplicationService service = createService(createLoginAttemptService());
        assertThrows(BusinessException.class, () -> service.updateProfile(1L, dto));
        assertNull(user.getPhone());
        verify(userRepository, never()).update(any(User.class));
    }

    @Test
    void updateProfileWithoutContactFieldsLeavesThemUntouched() {
        User user = userWithHash("x");
        user.setEmail("old@example.com");
        user.setPhone("13800138000");
        when(userRepository.findById(1L)).thenReturn(user);

        ProfileUpdateDTO dto = new ProfileUpdateDTO();
        dto.setNickname("新昵称");

        UserAuthApplicationService service = createService(createLoginAttemptService());
        service.updateProfile(1L, dto);

        // 联系方式留空表示不修改
        assertEquals("old@example.com", user.getEmail());
        assertEquals("13800138000", user.getPhone());
        assertEquals("新昵称", user.getNickname());
    }

    private RegisterDTO registerDto(String username) {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername(username);
        dto.setPassword(RAW_PASSWORD);
        return dto;
    }
}
