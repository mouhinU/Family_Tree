package com.mouhin.family.tree.infrastructure.ai;

import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.gateway.AiChatGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 基于 Spring AI 的聊天网关实现
 * <p>
 * 仅在 ai.llm.enabled=true 时激活，通过 OpenAI 兼容协议对接
 * DeepSeek / OpenAI 等模型（具体接入由 spring.ai.openai.* 配置决定）。
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Component
@ConditionalOnProperty(name = "ai.llm.enabled", havingValue = "true")
public class SpringAiChatGatewayImpl implements AiChatGateway {

    private static final Logger logger = LoggerFactory.getLogger(SpringAiChatGatewayImpl.class);

    private final ChatClient chatClient;

    public SpringAiChatGatewayImpl(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        try {
            String content = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
            if (content == null || content.isBlank()) {
                throw new BusinessException("AI 返回内容为空，请稍后重试");
            }
            return content;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Spring AI chat call failed: {}", e.getMessage(), e);
            throw new BusinessException("AI 服务调用失败，请稍后重试");
        }
    }

    @Override
    public <T> T chatForEntity(String systemPrompt, String userPrompt, Class<T> targetType) {
        try {
            T entity = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .entity(targetType);
            if (entity == null) {
                throw new BusinessException("AI 返回内容为空，请稍后重试");
            }
            return entity;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Spring AI entity call failed: {}", e.getMessage(), e);
            throw new BusinessException("AI 服务调用失败，请稍后重试");
        }
    }
}
