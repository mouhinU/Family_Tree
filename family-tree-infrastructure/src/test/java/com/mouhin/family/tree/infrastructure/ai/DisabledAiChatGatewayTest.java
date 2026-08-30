package com.mouhin.family.tree.infrastructure.ai;

import com.mouhin.family.tree.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 功能禁用网关单元测试。
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
class DisabledAiChatGatewayTest {

    private final DisabledAiChatGateway gateway = new DisabledAiChatGateway();

    @Test
    void chat_throwsBusinessException() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> gateway.chat("system", "user"));
        assertTrue(ex.getMessage().contains("AI 功能未启用"));
    }

    @Test
    void chatForEntity_throwsBusinessException() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> gateway.chatForEntity("system", "user", Object.class));
        assertTrue(ex.getMessage().contains("AI 功能未启用"));
    }
}
