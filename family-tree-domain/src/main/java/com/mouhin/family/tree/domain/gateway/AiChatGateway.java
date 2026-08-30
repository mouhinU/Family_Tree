package com.mouhin.family.tree.domain.gateway;

/**
 * AI 聊天网关接口（端口）
 * <p>
 * 抽象大模型调用能力，由基础设施层提供实现（如 Spring AI）。
 * AI 功能未启用时，激活的实现应抛出 BusinessException。
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
public interface AiChatGateway {

    /**
     * 发送提示词并获取纯文本回答
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return 模型回复文本
     */
    String chat(String systemPrompt, String userPrompt);

    /**
     * 发送提示词并将响应结构化映射为目标类型
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param targetType   目标类型
     * @param <T>          目标类型
     * @return 结构化对象
     */
    <T> T chatForEntity(String systemPrompt, String userPrompt, Class<T> targetType);
}
