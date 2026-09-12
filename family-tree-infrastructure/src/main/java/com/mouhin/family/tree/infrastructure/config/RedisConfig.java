package com.mouhin.family.tree.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 缓存配置
 * <p>
 * 提供两种 RedisTemplate：
 * <ul>
 *     <li>{@code RedisTemplate<String, Object>} — JSON 序列化，用于复杂对象缓存（族谱树等）</li>
 *     <li>{@code StringRedisTemplate} — 纯字符串，用于计数器、简单值缓存</li>
 * </ul>
 *
 * @author Family-Tree
 * @date 2026-09-10
 */
@Configuration
public class RedisConfig {

    /**
     * JSON 序列化 RedisTemplate，用于存储复杂对象（如族谱树 VO 列表）。
     * Key 使用 String 序列化，Value 使用 Jackson JSON 序列化。
     *
     * @param connectionFactory Redis 连接工厂
     * @return RedisTemplate 实例
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 字符串 RedisTemplate，用于简单计数器、登录尝试等场景。
     * Spring Boot 已自动配置 StringRedisTemplate，此处显式声明以确保可用。
     *
     * @param connectionFactory Redis 连接工厂
     * @return StringRedisTemplate 实例
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
