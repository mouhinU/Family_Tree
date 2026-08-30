package com.mouhin.family.tree.infrastructure.ai;

import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.gateway.AiChatGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AI 功能未启用时的网关实现
 * <p>
 * 当 ai.llm.enabled=false（或未配置）时激活，调用即抛出业务异常，
 * 与迁移前的行为保持一致，同时避免引入 Spring AI 模型 Bean。
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Component
@ConditionalOnProperty(name = "ai.llm.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledAiChatGateway implements AiChatGateway {

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        throw new BusinessException("AI 功能未启用，请在配置中设置 ai.llm.enabled=true");
    }

    @Override
    public <T> T chatForEntity(String systemPrompt, String userPrompt, Class<T> targetType) {
        throw new BusinessException("AI 功能未启用，请在配置中设置 ai.llm.enabled=true");
    }
}
