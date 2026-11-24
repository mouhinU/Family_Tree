package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.AiApplicationService;
import com.mouhin.family.tree.application.service.FamilySettingApplicationService;
import com.mouhin.family.tree.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AI 控制器状态端点单元测试：验证「已配置」口径 = 总开关开启且密钥非空，
 * 未配置时前端据此隐藏机器人入口。
 *
 * @author Family-Tree
 * @date 2026-11-24
 */
@ExtendWith(MockitoExtension.class)
class AiControllerTest {

    @Mock
    private AiApplicationService aiApplicationService;

    @Mock
    private FamilySettingApplicationService settingService;

    private AiController aiController;

    @BeforeEach
    void setUp() {
        aiController = new AiController(aiApplicationService, settingService);
    }

    @Test
    void statusNotConfiguredByDefault() {
        Result<Map<String, Object>> result = aiController.status();

        assertEquals(200, result.getCode());
        assertEquals(Boolean.FALSE, result.getData().get("configured"));
    }

    @Test
    void statusUnconfiguredWhenEnabledButApiKeyBlank() {
        ReflectionTestUtils.setField(aiController, "llmEnabled", true);
        ReflectionTestUtils.setField(aiController, "openaiApiKey", "  ");

        Result<Map<String, Object>> result = aiController.status();

        assertEquals(Boolean.FALSE, result.getData().get("configured"));
    }

    @Test
    void statusUnconfiguredWhenApiKeySetButSwitchOff() {
        ReflectionTestUtils.setField(aiController, "llmEnabled", false);
        ReflectionTestUtils.setField(aiController, "openaiApiKey", "sk-test-key");

        Result<Map<String, Object>> result = aiController.status();

        assertEquals(Boolean.FALSE, result.getData().get("configured"));
    }

    @Test
    void statusConfiguredWhenSwitchOnAndApiKeyPresent() {
        ReflectionTestUtils.setField(aiController, "llmEnabled", true);
        ReflectionTestUtils.setField(aiController, "openaiApiKey", "sk-test-key");

        Result<Map<String, Object>> result = aiController.status();

        assertEquals(Boolean.TRUE, result.getData().get("configured"));
    }
}
