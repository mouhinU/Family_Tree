package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.AiApplicationService;
import com.mouhin.family.tree.application.service.FamilySettingApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.enums.FamilySettingKeyEnum;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.web.controller.user.AiController;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 控制器状态端点单元测试：验证「已配置」口径 = yml 总开关开启、密钥非空，
 * 且当前家族 AI_FEATURE_ENABLED 生效值为开，未配置时前端据此隐藏机器人入口。
 *
 * @author Family-Tree
 * @date 2026-11-24
 */
@ExtendWith(MockitoExtension.class)
class AiControllerTest {

    private static final Long FAMILY_ID = 100L;

    @Mock
    private AiApplicationService aiApplicationService;

    @Mock
    private FamilySettingApplicationService settingService;

    @Mock
    private HttpSession session;

    private AiController aiController;

    @BeforeEach
    void setUp() {
        aiController = new AiController(aiApplicationService, settingService);
    }

    @Test
    void statusNotConfiguredByDefault() {
        Result<Map<String, Object>> result = aiController.status(session);

        assertEquals(200, result.getCode());
        assertEquals(Boolean.FALSE, result.getData().get("configured"));
    }

    @Test
    void statusUnconfiguredWhenEnabledButApiKeyBlank() {
        ReflectionTestUtils.setField(aiController, "llmEnabled", true);
        ReflectionTestUtils.setField(aiController, "openaiApiKey", "  ");

        Result<Map<String, Object>> result = aiController.status(session);

        assertEquals(Boolean.FALSE, result.getData().get("configured"));
    }

    @Test
    void statusUnconfiguredWhenApiKeySetButSwitchOff() {
        ReflectionTestUtils.setField(aiController, "llmEnabled", false);
        ReflectionTestUtils.setField(aiController, "openaiApiKey", "sk-test-key");

        Result<Map<String, Object>> result = aiController.status(session);

        assertEquals(Boolean.FALSE, result.getData().get("configured"));
    }

    @Test
    void statusConfiguredWhenSwitchOnApiKeyPresentAndFamilyAiEnabled() {
        ReflectionTestUtils.setField(aiController, "llmEnabled", true);
        ReflectionTestUtils.setField(aiController, "openaiApiKey", "sk-test-key");
        when(session.getAttribute(FamilyTreeConsts.SESSION_FAMILY_ID)).thenReturn(FAMILY_ID);
        when(settingService.isEnabled(eq(FAMILY_ID), eq(FamilySettingKeyEnum.AI_FEATURE_ENABLED)))
                .thenReturn(true);

        Result<Map<String, Object>> result = aiController.status(session);

        assertEquals(Boolean.TRUE, result.getData().get("configured"));
    }

    @Test
    void statusUnconfiguredWhenYmlReadyButFamilyAiDisabled() {
        ReflectionTestUtils.setField(aiController, "llmEnabled", true);
        ReflectionTestUtils.setField(aiController, "openaiApiKey", "sk-test-key");
        when(session.getAttribute(FamilyTreeConsts.SESSION_FAMILY_ID)).thenReturn(FAMILY_ID);
        when(settingService.isEnabled(eq(FAMILY_ID), eq(FamilySettingKeyEnum.AI_FEATURE_ENABLED)))
                .thenReturn(false);

        Result<Map<String, Object>> result = aiController.status(session);

        assertEquals(Boolean.FALSE, result.getData().get("configured"));
    }

    @Test
    void statusUnconfiguredWhenNoFamilyInSession() {
        ReflectionTestUtils.setField(aiController, "llmEnabled", true);
        ReflectionTestUtils.setField(aiController, "openaiApiKey", "sk-test-key");
        when(session.getAttribute(FamilyTreeConsts.SESSION_FAMILY_ID)).thenReturn(null);

        Result<Map<String, Object>> result = aiController.status(session);

        assertEquals(Boolean.FALSE, result.getData().get("configured"));
        verify(settingService, never()).isEnabled(any(), any());
    }
}
